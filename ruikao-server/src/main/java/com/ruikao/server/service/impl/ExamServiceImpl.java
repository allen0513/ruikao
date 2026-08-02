package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.ExamDTO;
import com.ruikao.pojo.dto.ExamPageQueryDTO;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamPaper;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.pojo.entity.ExamStudent;
import com.ruikao.pojo.entity.Student;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.pojo.vo.ExamVO;
import com.ruikao.pojo.vo.RankVO;
import com.ruikao.server.annotation.RedisLock;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamPaperMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.mapper.ExamStudentMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamMapper examMapper;

    private final ExamPaperMapper examPaperMapper;

    private final SysUserMapper sysUserMapper;

    private final ExamStudentMapper examStudentMapper;

    private final StudentMapper studentMapper;

    private final ExamRecordMapper examRecordMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /** 学生端考试列表缓存：按学生 keyed 驱逐，避免全量清空击穿 */
    private final CacheManager cacheManager;

    @Override
    public PageResult<ExamVO> pageQuery(ExamPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        if (dto.getExamName() != null && !dto.getExamName().isEmpty()) {
            wrapper.like(Exam::getExamName, dto.getExamName());
        }
        // 关键字：模糊匹配考试名称或考场（嵌套 and 保证与 status 等条件优先级正确）
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(Exam::getExamName, dto.getKeyword())
                    .or().like(Exam::getExamRoom, dto.getKeyword()));
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Exam::getStatus, dto.getStatus());
        }
        if (dto.getExamDate() != null && !dto.getExamDate().isEmpty()) {
            wrapper.eq(Exam::getExamDate, LocalDate.parse(dto.getExamDate()));
        }
        wrapper.orderByDesc(Exam::getCreateTime);

        List<Exam> examList = examMapper.selectList(wrapper);
        Page<Exam> page = (Page<Exam>) examList;
        if (examList.isEmpty()) {
            return PageResult.of(page.getTotal(), new ArrayList<>());
        }

        // 批量查询试卷与创建人，避免逐条 selectById 的 N+1
        List<Long> paperIds = examList.stream().map(Exam::getPaperId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> creatorIds = examList.stream().map(Exam::getCreatorId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ExamPaper> paperMap = paperIds.isEmpty() ? Collections.emptyMap()
                : examPaperMapper.selectBatchIds(paperIds).stream().collect(Collectors.toMap(ExamPaper::getId, p -> p));
        Map<Long, SysUser> userMap = creatorIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(creatorIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        // 一次查出全部分配关系，内存中分组计数，避免逐条 selectCount
        LambdaQueryWrapper<ExamStudent> esWrapper = new LambdaQueryWrapper<>();
        esWrapper.in(ExamStudent::getExamId, examList.stream().map(Exam::getId).collect(Collectors.toList()));
        Map<Long, Long> registeredMap = examStudentMapper.selectList(esWrapper).stream()
                .collect(Collectors.groupingBy(ExamStudent::getExamId, Collectors.counting()));

        List<ExamVO> voList = examList.stream().map(exam -> {
            ExamVO vo = new ExamVO();
            BeanUtils.copyProperties(exam, vo);

            ExamPaper paper = paperMap.get(exam.getPaperId());
            if (paper != null) {
                vo.setPaperName(paper.getPaperName());
            }

            SysUser user = userMap.get(exam.getCreatorId());
            if (user != null) {
                vo.setCreatorName(user.getRealName());
            }

            Long count = registeredMap.get(exam.getId());
            vo.setRegisteredCount(count != null ? count.intValue() : 0);

            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(page.getTotal(), voList);
    }

    @Override
    public void add(ExamDTO dto) {
        Exam exam = new Exam();
        BeanUtils.copyProperties(dto, exam);
        // DTO 为 String，实体为 LocalDate/LocalTime，需手动转换
        exam.setExamDate(parseDate(dto.getExamDate()));
        exam.setStartTime(parseTime(dto.getStartTime()));
        exam.setEndTime(parseTime(dto.getEndTime()));
        exam.setCreatorId(BaseContext.getCurrentId());
        examMapper.insert(exam);
        // 新考试尚未分配学生，无需驱逐缓存（分配发生在 assignStudents，由该处负责驱逐）
    }

    @Override
    public void update(ExamDTO dto) {
        // 已有考试记录的考试禁止换卷：换卷会使历史答卷的 paper_id 悬空、阅卷与排行数据不一致
        if (dto.getPaperId() != null && dto.getId() != null) {
            Exam exist = examMapper.selectById(dto.getId());
            if (exist != null && exist.getPaperId() != null && !exist.getPaperId().equals(dto.getPaperId())) {
                LambdaQueryWrapper<ExamRecord> recordWrapper = new LambdaQueryWrapper<>();
                recordWrapper.eq(ExamRecord::getExamId, dto.getId());
                if (examRecordMapper.selectCount(recordWrapper) > 0) {
                    throw new BusinessException("该考试已有考试记录，无法更换试卷");
                }
            }
        }

        Exam exam = new Exam();
        BeanUtils.copyProperties(dto, exam);
        exam.setExamDate(parseDate(dto.getExamDate()));
        exam.setStartTime(parseTime(dto.getStartTime()));
        exam.setEndTime(parseTime(dto.getEndTime()));
        examMapper.updateById(exam);
        // 考试信息变化会影响已分配学生的考试列表，按学生 keyed 驱逐
        evictStudentExamListByExam(dto.getId(), null);
    }

    private LocalDate parseDate(String s) {
        return (s == null || s.isEmpty()) ? null : LocalDate.parse(s);
    }

    private LocalTime parseTime(String s) {
        return (s == null || s.isEmpty()) ? null : LocalTime.parse(s);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 已有考试记录的考试禁止删除，防止成绩数据悬空
        LambdaQueryWrapper<ExamRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(ExamRecord::getExamId, id);
        if (examRecordMapper.selectCount(recordWrapper) > 0) {
            throw new BusinessException("该考试已有考试记录，无法删除");
        }
        // 原分配学生列表需要刷新；须在分配关系删除前驱逐（否则查不到原名单）
        evictStudentExamListByExam(id, null);
        // 清理该考试的分配关系
        LambdaQueryWrapper<ExamStudent> esWrapper = new LambdaQueryWrapper<>();
        esWrapper.eq(ExamStudent::getExamId, id);
        examStudentMapper.delete(esWrapper);
        examMapper.deleteById(id);
    }

    @Override
    public ExamVO getDetail(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            return null;
        }
        ExamVO vo = new ExamVO();
        BeanUtils.copyProperties(exam, vo);

        if (exam.getPaperId() != null) {
            ExamPaper paper = examPaperMapper.selectById(exam.getPaperId());
            if (paper != null) {
                vo.setPaperName(paper.getPaperName());
            }
        }

        if (exam.getCreatorId() != null) {
            SysUser user = sysUserMapper.selectById(exam.getCreatorId());
            if (user != null) {
                vo.setCreatorName(user.getRealName());
            }
        }

        LambdaQueryWrapper<ExamStudent> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(ExamStudent::getExamId, exam.getId());
        Long count = examStudentMapper.selectCount(countWrapper);
        vo.setRegisteredCount(count != null ? count.intValue() : 0);

        return vo;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Exam exam = examMapper.selectById(id);
        if (exam != null) {
            exam.setStatus(status);
            examMapper.updateById(exam);
            // 状态变化会影响学生的考试列表展示，按学生 keyed 驱逐
            evictStudentExamListByExam(id, null);
        }
    }

    @Override
    @Transactional
    @RedisLock(key = "assign:{#examId}")
    public void assignStudents(Long examId, List<Long> studentIds) {
        // 去重：防止重复学生 ID 触发 uk_exam_student 唯一键冲突
        List<Long> distinctIds = studentIds == null
                ? new ArrayList<>()
                : studentIds.stream().distinct().collect(Collectors.toList());

        // 旧分配学生的列表需要刷新；须在分配关系删除前驱逐（否则查不到原名单）
        evictStudentExamListByExam(examId, null);

        // 先清除该考试原有分配
        LambdaQueryWrapper<ExamStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamStudent::getExamId, examId);
        examStudentMapper.delete(wrapper);

        // 批量分配
        for (Long studentId : distinctIds) {
            ExamStudent es = new ExamStudent();
            es.setExamId(examId);
            es.setStudentId(studentId);
            examStudentMapper.insert(es);
        }
        log.info("考试分配完成，examId={}, 学生数={}", examId, distinctIds.size());

        // 新分配学生的列表同样需要刷新
        evictStudentExamListByExam(examId, distinctIds);
    }

    /**
     * 按学生 keyed 驱逐学生端考试列表缓存：
     * 驱逐该考试的全部已分配学生 + 额外学生（如新分配名单），避免全量清空击穿
     */
    private void evictStudentExamListByExam(Long examId, List<Long> extraStudentIds) {
        Set<Long> ids = new HashSet<>();
        if (examId != null) {
            List<Long> assigned = examStudentMapper.getStudentIdsByExamId(examId);
            if (assigned != null) {
                ids.addAll(assigned);
            }
        }
        if (extraStudentIds != null) {
            ids.addAll(extraStudentIds);
        }
        if (ids.isEmpty()) {
            return;
        }
        Cache cache = cacheManager.getCache("studentExamList");
        if (cache != null) {
            for (Long studentId : ids) {
                cache.evict(studentId);
            }
        }
    }

    @Override
    public List<Long> getAssignedStudentIds(Long examId) {
        return examStudentMapper.getStudentIdsByExamId(examId);
    }

    @Override
    public List<RankVO> getExamRank(Long examId) {
        // ZREVRANGE 按分数降序取前 10 名
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores("exam:rank:" + examId, 0, 9);
        if (tuples != null && !tuples.isEmpty()) {
            List<RankVO> list = new ArrayList<>();
            int rank = 1;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                RankVO vo = new RankVO();
                vo.setRank(rank++);
                String studentIdStr = tuple.getValue();
                Double score = tuple.getScore();
                if (studentIdStr != null) {
                    Long studentId = Long.valueOf(studentIdStr);
                    Student student = studentMapper.selectById(studentId);
                    vo.setStudentName(student != null ? student.getName() : "未知学生");
                    vo.setStudentNo(student != null ? student.getStudentNo() : "");
                }
                vo.setScore(score != null ? BigDecimal.valueOf(score) : BigDecimal.ZERO);
                list.add(vo);
            }
            return list;
        }

        // Redis 无数据（如缓存被清空）时降级查库：已定稿成绩倒序取前 10，避免排行榜空窗
        log.info("排行榜缓存为空，降级查询数据库, examId={}", examId);
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamRecord::getExamId, examId)
                .eq(ExamRecord::getStatus, ExamConstants.RECORD_STATUS_FINALIZED)
                .isNotNull(ExamRecord::getScore)
                .orderByDesc(ExamRecord::getScore)
                .last("LIMIT 10");
        List<ExamRecord> records = examRecordMapper.selectList(wrapper);

        List<RankVO> list = new ArrayList<>();
        int rank = 1;
        for (ExamRecord record : records) {
            RankVO vo = new RankVO();
            vo.setRank(rank++);
            Student student = studentMapper.selectById(record.getStudentId());
            vo.setStudentName(student != null ? student.getName() : "未知学生");
            vo.setStudentNo(student != null ? student.getStudentNo() : "");
            vo.setScore(record.getScore() != null ? record.getScore() : BigDecimal.ZERO);
            list.add(vo);
        }
        return list;
    }
}
