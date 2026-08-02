package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.context.BaseContext;
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
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamPaperMapper;
import com.ruikao.server.mapper.ExamStudentMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.service.ExamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExamServiceImpl implements ExamService {

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamPaperMapper examPaperMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ExamStudentMapper examStudentMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<ExamVO> pageQuery(ExamPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        if (dto.getExamName() != null && !dto.getExamName().isEmpty()) {
            wrapper.like(Exam::getExamName, dto.getExamName());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Exam::getStatus, dto.getStatus());
        }
        if (dto.getExamDate() != null && !dto.getExamDate().isEmpty()) {
            wrapper.eq(Exam::getExamDate, dto.getExamDate());
        }
        wrapper.orderByDesc(Exam::getCreateTime);

        List<Exam> examList = examMapper.selectList(wrapper);
        Page<Exam> page = (Page<Exam>) examList;

        List<ExamVO> voList = examList.stream().map(exam -> {
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
        }).collect(Collectors.toList());

        return PageResult.of(page.getTotal(), voList);
    }

    @Override
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public void add(ExamDTO dto) {
        Exam exam = new Exam();
        BeanUtils.copyProperties(dto, exam);
        exam.setCreatorId(BaseContext.getCurrentId());
        examMapper.insert(exam);
    }

    @Override
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public void update(ExamDTO dto) {
        Exam exam = new Exam();
        BeanUtils.copyProperties(dto, exam);
        examMapper.updateById(exam);
    }

    @Override
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public void delete(Long id) {
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
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public void updateStatus(Long id, Integer status) {
        Exam exam = examMapper.selectById(id);
        if (exam != null) {
            exam.setStatus(status);
            examMapper.updateById(exam);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public void assignStudents(Long examId, List<Long> studentIds) {
        // 先清除该考试原有分配
        LambdaQueryWrapper<ExamStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamStudent::getExamId, examId);
        examStudentMapper.delete(wrapper);

        // 批量分配
        if (studentIds != null && !studentIds.isEmpty()) {
            for (Long studentId : studentIds) {
                ExamStudent es = new ExamStudent();
                es.setExamId(examId);
                es.setStudentId(studentId);
                examStudentMapper.insert(es);
            }
        }
        log.info("考试分配完成，examId={}, 学生数={}", examId, studentIds != null ? studentIds.size() : 0);
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
        if (tuples == null || tuples.isEmpty()) {
            return new ArrayList<>();
        }

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
}
