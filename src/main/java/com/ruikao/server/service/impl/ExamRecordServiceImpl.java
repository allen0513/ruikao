package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.RecordPageQueryDTO;
import com.ruikao.pojo.dto.ScoreDTO;
import com.ruikao.pojo.entity.*;
import com.ruikao.pojo.vo.ExamStartVO;
import com.ruikao.pojo.vo.QuestionVO;
import com.ruikao.pojo.vo.RecordVO;
import com.ruikao.pojo.vo.StudentExamVO;
import com.ruikao.pojo.vo.StudentQuestionVO;
import com.ruikao.pojo.vo.StudentRecordVO;
import com.ruikao.pojo.vo.SubmitResultVO;
import com.ruikao.server.annotation.RedisLock;
import com.ruikao.server.mapper.*;
import com.ruikao.server.service.ChartService;
import com.ruikao.server.service.ExamRecordService;
import com.ruikao.server.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExamRecordServiceImpl implements ExamRecordService {

    private final ExamRecordMapper examRecordMapper;

    private final ExamAnswerMapper examAnswerMapper;

    private final ExamMapper examMapper;

    private final ExamPaperMapper examPaperMapper;

    private final StudentMapper studentMapper;

    private final PaperQuestionMapper paperQuestionMapper;

    private final ExamStudentMapper examStudentMapper;

    private final QuestionBankMapper questionBankMapper;

    private final WebSocketServer webSocketServer;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 自身代理引用：score 需先解析 recordId 再获取记录级锁，经代理调用保证 @RedisLock 生效
     */
    private final ObjectProvider<ExamRecordServiceImpl> selfProvider;

    /** 学生端考试列表缓存：按学生 keyed 驱逐，避免全量清空击穿 */
    private final CacheManager cacheManager;

    /** 图表统计：定稿/删除答卷后异步重建缓存 */
    private final ChartService chartService;

    @Override
    public PageResult<RecordVO> pageQuery(RecordPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        // 阅卷管理只展示正式考试/作业的答卷，排除习题练习记录（练习 exam_id IS NULL，无人工阅卷环节）
        wrapper.isNotNull(ExamRecord::getExamId);
        if (dto.getExamId() != null) {
            wrapper.eq(ExamRecord::getExamId, dto.getExamId());
        }
        if (dto.getStudentId() != null) {
            wrapper.eq(ExamRecord::getStudentId, dto.getStudentId());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(ExamRecord::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(ExamRecord::getCreateTime);

        List<ExamRecord> recordList = examRecordMapper.selectList(wrapper);
        Page<ExamRecord> page = (Page<ExamRecord>) recordList;

        // 批量查询关联信息（exam/paper/student 各一次 IN 查询），避免 N+1
        Set<Long> examIds = recordList.stream().map(ExamRecord::getExamId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> paperIds = recordList.stream().map(ExamRecord::getPaperId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> studentIds = recordList.stream().map(ExamRecord::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Exam> examMap = examIds.isEmpty() ? Collections.emptyMap()
                : examMapper.selectBatchIds(examIds).stream().collect(Collectors.toMap(Exam::getId, e -> e));
        Map<Long, ExamPaper> paperMap = paperIds.isEmpty() ? Collections.emptyMap()
                : examPaperMapper.selectBatchIds(paperIds).stream().collect(Collectors.toMap(ExamPaper::getId, p -> p));
        Map<Long, Student> studentMap = studentIds.isEmpty() ? Collections.emptyMap()
                : studentMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(Student::getId, s -> s));

        List<RecordVO> voList = recordList.stream().map(record -> {
            RecordVO vo = new RecordVO();
            BeanUtils.copyProperties(record, vo);

            Exam exam = examMap.get(record.getExamId());
            if (exam != null) {
                vo.setExamName(exam.getExamName());
            }

            ExamPaper paper = paperMap.get(record.getPaperId());
            if (paper != null) {
                vo.setPaperName(paper.getPaperName());
            }

            Student student = studentMap.get(record.getStudentId());
            if (student != null) {
                vo.setStudentName(student.getName());
                vo.setStudentNo(student.getStudentNo());
            }

            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(page.getTotal(), voList);
    }

    @Override
    public RecordVO getDetail(Long id) {
        ExamRecord record = examRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        // 练习记录（exam_id IS NULL）不进阅卷管理，详情同样不可阅卷
        if (record.getExamId() == null) {
            return null;
        }
        RecordVO vo = new RecordVO();
        BeanUtils.copyProperties(record, vo);

        Exam exam = examMapper.selectById(record.getExamId());
        if (exam != null) {
            vo.setExamName(exam.getExamName());
        }

        ExamPaper paper = examPaperMapper.selectById(record.getPaperId());
        if (paper != null) {
            vo.setPaperName(paper.getPaperName());
        }

        Student student = studentMapper.selectById(record.getStudentId());
        if (student != null) {
            vo.setStudentName(student.getName());
            vo.setStudentNo(student.getStudentNo());
        }

        // 加载题目列表（管理端阅卷：含正确答案）
        if (record.getPaperId() != null) {
            vo.setQuestions(loadQuestionsWithAnswer(record.getPaperId()));
        }

        // 加载答案列表
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, id);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);
        vo.setAnswers(answers);

        return vo;
    }

    @Override
    public StudentRecordVO getStudentDetail(Long recordId, Long studentId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("考试记录不存在");
        }
        // 归属校验：学生只能查看自己的答卷
        if (record.getStudentId() == null || !record.getStudentId().equals(studentId)) {
            throw new BusinessException("无权查看该考试记录");
        }
        StudentRecordVO vo = new StudentRecordVO();
        BeanUtils.copyProperties(record, vo);

        Exam exam = examMapper.selectById(record.getExamId());
        if (exam != null) {
            vo.setExamName(exam.getExamName());
        }

        ExamPaper paper = examPaperMapper.selectById(record.getPaperId());
        if (paper != null) {
            vo.setPaperName(paper.getPaperName());
        }

        Student student = studentMapper.selectById(record.getStudentId());
        if (student != null) {
            vo.setStudentName(student.getName());
            vo.setStudentNo(student.getStudentNo());
        }

        // 成绩仅已审核（终态）展示；未审核前不透漏分数与逐题得分，防止泄露批改进度
        boolean audited = record.getStatus() != null && record.getStatus() == ExamConstants.RECORD_STATUS_AUDITED;
        if (!audited) {
            vo.setScore(null);
            vo.setObjectiveScore(null);
            vo.setSubjectiveScore(null);
        }

        // 题目（不含正确答案）：正式考试/作业取试卷题（按开考乱序回显），练习记录由作答记录反查
        List<StudentQuestionVO> questions = record.getPaperId() != null
                ? loadQuestionsWithoutAnswer(record.getPaperId())
                : loadPracticeQuestions(recordId);
        vo.setQuestions(orderByRedisOrder(questions, recordId));

        // 答案列表
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, recordId);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);
        if (!audited) {
            // 未审核前不展示逐题得分与对错
            answers.forEach(a -> {
                a.setScore(null);
                a.setIsCorrect(null);
            });
        }
        vo.setAnswers(answers);

        return vo;
    }

    @Override
    public List<ExamAnswer> getAnswers(Long recordId) {
        LambdaQueryWrapper<ExamAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswer::getRecordId, recordId);
        return examAnswerMapper.selectList(wrapper);
    }

    @Override
    public void score(ScoreDTO dto) {
        // 先解析出 recordId（前端按 answerId 传参），再走带记录级锁的评分方法，
        // 与交卷(submitExam)/定稿(completeMarking)/删记录(deleteRecord)/自动收卷互斥，防止 TOCTOU 竞态
        Long recordId = resolveRecordId(dto);
        selfProvider.getObject().scoreWithLock(dto, recordId);
    }

    /**
     * 带记录级锁的评分方法（经 selfProvider 代理调用，确保 @RedisLock 生效）
     */
    @RedisLock(key = "record:{#recordId}")
    public void scoreWithLock(ScoreDTO dto, Long recordId) {
        if (dto.getScore() == null || dto.getScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("分数不能为空或负数");
        }

        ExamAnswer answer = null;
        if (dto.getAnswerId() != null) {
            // 前端按 answerId 评分
            answer = examAnswerMapper.selectById(dto.getAnswerId());
        } else if (dto.getRecordId() != null && dto.getQuestionId() != null) {
            // 后端按 recordId + questionId 评分
            LambdaQueryWrapper<ExamAnswer> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExamAnswer::getRecordId, dto.getRecordId());
            wrapper.eq(ExamAnswer::getQuestionId, dto.getQuestionId());
            answer = examAnswerMapper.selectOne(wrapper);
        }
        if (answer == null) {
            throw new BusinessException("答题记录不存在");
        }

        // 已批改待审核/已审核的答卷禁止改分（防止审核前后篡改成绩）
        ExamRecord record = examRecordMapper.selectById(answer.getRecordId());
        if (record != null && record.getStatus() != null && record.getStatus() >= ExamConstants.RECORD_STATUS_MARKED) {
            throw new BusinessException("答卷已批改待审核，不可再评分");
        }

        // 分值上限：试卷自定义分，未设置则取题库默认分
        BigDecimal maxScore = null;
        if (record != null && record.getPaperId() != null) {
            LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
            pqWrapper.eq(PaperQuestion::getPaperId, record.getPaperId());
            pqWrapper.eq(PaperQuestion::getQuestionId, answer.getQuestionId());
            PaperQuestion pq = paperQuestionMapper.selectOne(pqWrapper);
            if (pq != null && pq.getQuestionScore() != null) {
                maxScore = pq.getQuestionScore();
            }
        }
        if (maxScore == null) {
            QuestionBank question = questionBankMapper.selectById(answer.getQuestionId());
            if (question != null) {
                maxScore = question.getScore();
            }
        }
        if (maxScore != null && dto.getScore().compareTo(maxScore) > 0) {
            throw new BusinessException("分数不能超过该题分值");
        }

        answer.setScore(dto.getScore());
        examAnswerMapper.updateById(answer);
    }

    /** 从评分入参解析考试记录 ID：优先 recordId，其次 answerId 反查 */
    private Long resolveRecordId(ScoreDTO dto) {
        if (dto.getRecordId() != null) {
            return dto.getRecordId();
        }
        if (dto.getAnswerId() != null) {
            ExamAnswer answer = examAnswerMapper.selectById(dto.getAnswerId());
            return answer != null ? answer.getRecordId() : null;
        }
        return null;
    }

    /** 按学生 keyed 驱逐学生端考试列表缓存，避免全量清空击穿 */
    private void evictStudentExamList(Long studentId) {
        if (studentId == null) {
            return;
        }
        Cache cache = cacheManager.getCache("studentExamList");
        if (cache != null) {
            cache.evict(studentId);
        }
    }

    /**
     * 图表统计缓存失效 + 异步重建：先同步驱逐保证正确性；
     * 异步重算放在事务提交后执行（事务内重算读不到未提交数据，会把旧值写回缓存），
     * 无事务上下文时直接异步刷新。成绩定稿/删除影响平均分、通过率等统计。
     */
    private void evictAndRefreshChartCache() {
        Cache cache = cacheManager.getCache("chart");
        if (cache != null) {
            cache.clear();
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    chartService.refreshCacheAsync();
                }
            });
        } else {
            chartService.refreshCacheAsync();
        }
    }

    @Override
    @Transactional
    @RedisLock(key = "record:{#id}")
    public void completeMarking(Long id) {
        ExamRecord record = examRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("考试记录不存在");
        }
        // 只允许对已交卷的答卷完成批改，防止未交卷或重复批改
        if (record.getStatus() == null || record.getStatus() != ExamConstants.RECORD_STATUS_SUBMITTED) {
            throw new BusinessException("仅已交卷的答卷可以完成批改");
        }

        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, id);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal subjectiveScore = BigDecimal.ZERO;
        BigDecimal objectiveScore = record.getObjectiveScore() != null ? record.getObjectiveScore() : BigDecimal.ZERO;

        for (ExamAnswer answer : answers) {
            if (answer.getScore() != null) {
                totalScore = totalScore.add(answer.getScore());
                // 计算已打分的主观题（主观文字题 + 操作题）
                QuestionBank question = questionBankMapper.selectById(answer.getQuestionId());
                if (question != null && (question.getQuestionType() == ExamConstants.QUESTION_TYPE_SUBJECTIVE
                        || question.getQuestionType() == ExamConstants.QUESTION_TYPE_OPERATION)) {
                    subjectiveScore = subjectiveScore.add(answer.getScore());
                }
            }
        }

        record.setScore(totalScore);
        record.setSubjectiveScore(subjectiveScore);
        record.setObjectiveScore(objectiveScore);
        // 完成批改：2 已交卷 → 3 已批改（待教师审核确认），成绩暂不生效
        record.setStatus(ExamConstants.RECORD_STATUS_MARKED);
        // 注意：不要覆盖 submitTime，保留学生的真实交卷时间
        examRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    @RedisLock(key = "record:{#id}")
    public void confirmMarking(Long id) {
        ExamRecord record = examRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("考试记录不存在");
        }
        // 只允许对已批改待审核的答卷审核确认，成绩终态不可再流转
        if (record.getStatus() == null || record.getStatus() != ExamConstants.RECORD_STATUS_MARKED) {
            throw new BusinessException("仅已批改待审核的答卷可以审核确认");
        }

        record.setStatus(ExamConstants.RECORD_STATUS_AUDITED);
        examRecordMapper.updateById(record);

        // 审核后成绩生效（学生端可查分）：按学生 keyed 驱逐考试列表缓存
        evictStudentExamList(record.getStudentId());
        // 成绩终态影响平均分/通过率统计，驱逐图表缓存
        evictAndRefreshChartCache();

        // 成绩生效后写入 Redis 排行榜（ZSET：member=studentId, score=总分）
        // 注册 afterCommit 回调：排行榜写入跟随事务提交，避免事务回滚后产生脏数据
        Long examId = record.getExamId();
        if (examId != null) {
            Long studentId = record.getStudentId();
            double rankScore = record.getScore() != null ? record.getScore().doubleValue() : 0d;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        stringRedisTemplate.opsForZSet().add(
                                "exam:rank:" + examId,
                                String.valueOf(studentId),
                                rankScore);
                    } catch (Exception e) {
                        log.error("写入成绩排行榜失败, examId={}, studentId={}", examId, studentId, e);
                    }
                }
            });
        }
    }

    @Override
    @Cacheable(cacheNames = "studentExamList", key = "#studentId")
    public List<StudentExamVO> getStudentExams(Long studentId) {
        // 1. 查询已分配的考试
        List<Long> assignedExamIds = examStudentMapper.getExamIdsByStudentId(studentId);

        // 2. 查询已开始的考试记录
        LambdaQueryWrapper<ExamRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(ExamRecord::getStudentId, studentId);
        List<ExamRecord> records = examRecordMapper.selectList(recordWrapper);

        // 3. 合并：已分配未开始的 + 已开始的
        Set<Long> startedExamIds = records.stream()
                .map(ExamRecord::getExamId)
                .collect(Collectors.toSet());

        List<StudentExamVO> voList = new ArrayList<>();

        // 先加已分配但未开始的考试
        for (Long examId : assignedExamIds) {
            if (!startedExamIds.contains(examId)) {
                Exam exam = examMapper.selectById(examId);
                if (exam != null) {
                    StudentExamVO vo = new StudentExamVO();
                    vo.setExamId(exam.getId());
                    vo.setExamName(exam.getExamName());
                    vo.setCourseName(exam.getCourseName());
                    vo.setExamDate(exam.getExamDate());
                    vo.setStartTime(exam.getStartTime());
                    vo.setEndTime(exam.getEndTime());
                    vo.setDuration(exam.getDuration());
                    vo.setExamType(exam.getExamType());
                    vo.setExamStatus(exam.getStatus());
                    vo.setRecordId(null);
                    vo.setRecordStatus(0);
                    vo.setScore(null);
                    voList.add(vo);
                }
            }
        }

        // 再加已开始的（有考试记录的）
        for (ExamRecord record : records) {
            Exam exam = examMapper.selectById(record.getExamId());
            if (exam != null) {
                StudentExamVO vo = new StudentExamVO();
                vo.setExamId(exam.getId());
                vo.setExamName(exam.getExamName());
                vo.setCourseName(exam.getCourseName());
                vo.setExamDate(exam.getExamDate());
                vo.setStartTime(exam.getStartTime());
                vo.setEndTime(exam.getEndTime());
                vo.setDuration(exam.getDuration());
                vo.setExamType(exam.getExamType());
                vo.setExamStatus(exam.getStatus());
                vo.setRecordId(record.getId());
                vo.setRecordStatus(record.getStatus());
                // 成绩仅已审核（终态）展示，未审核前不透漏分数
                vo.setScore(record.getStatus() != null && record.getStatus() == ExamConstants.RECORD_STATUS_AUDITED
                        ? record.getScore() : null);
                voList.add(vo);
            }
        }

        return voList;
    }

    @Override
    @Transactional
    @RedisLock(key = "start:{#examId}-{#studentId}")
    @CacheEvict(cacheNames = "studentExamList", key = "#studentId")
    public ExamStartVO startExam(Long examId, Long studentId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        // 分配校验：必须被分配到该考试
        LambdaQueryWrapper<ExamStudent> esWrapper = new LambdaQueryWrapper<>();
        esWrapper.eq(ExamStudent::getExamId, examId);
        esWrapper.eq(ExamStudent::getStudentId, studentId);
        if (examStudentMapper.selectCount(esWrapper) == 0) {
            throw new BusinessException("您未被分配参加该考试");
        }

        // 状态校验：考试必须处于进行中
        if (exam.getStatus() == null || exam.getStatus() != ExamConstants.STATUS_IN_PROGRESS) {
            throw new BusinessException("考试未在进行中，暂无法开考");
        }

        // 时间窗校验：考试日期必须为今天，且当前时间在开始-结束之间
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        if (exam.getExamDate() == null || exam.getStartTime() == null || exam.getEndTime() == null
                || !exam.getExamDate().equals(today)
                || exam.getStartTime().isAfter(now)
                || exam.getEndTime().isBefore(now)) {
            throw new BusinessException("当前不在考试时间窗内，无法开考");
        }

        // 考试必须关联试卷才能开始
        if (exam.getPaperId() == null) {
            throw new BusinessException("该考试未关联试卷，请联系管理员");
        }

        // 防重复开考：已有考试记录则直接返回，保证一次考试一份答卷
        LambdaQueryWrapper<ExamRecord> recWrapper = new LambdaQueryWrapper<>();
        recWrapper.eq(ExamRecord::getExamId, examId);
        recWrapper.eq(ExamRecord::getStudentId, studentId);
        ExamRecord existRecord = examRecordMapper.selectOne(recWrapper);
        if (existRecord != null) {
            return buildStartVO(existRecord, exam);
        }

        ExamRecord record = new ExamRecord();
        record.setExamId(examId);
        record.setStudentId(studentId);
        record.setPaperId(exam.getPaperId());
        record.setStartTime(LocalDateTime.now());
        record.setStatus(ExamConstants.RECORD_STATUS_EXAMINING);
        examRecordMapper.insert(record);
        return buildStartVO(record, exam);
    }

    /**
     * 组装开考返回（题目不含正确答案；防作弊：按学生随机打乱题序）
     */
    private ExamStartVO buildStartVO(ExamRecord record, Exam exam) {
        List<StudentQuestionVO> questions = exam.getPaperId() != null
                ? loadQuestionsWithoutAnswer(exam.getPaperId())
                : new ArrayList<>();
        // 防作弊：题序按学生随机打乱，顺序写 Redis（TTL=剩余时长），刷新/重进保持同一题序
        questions = shuffleWithRedisOrder(questions, record.getId(), exam.getDuration());

        ExamPaper paper = examPaperMapper.selectById(exam.getPaperId());

        ExamStartVO vo = new ExamStartVO();
        vo.setRecordId(record.getId());
        vo.setPaperId(exam.getPaperId());
        vo.setPaperName(paper != null ? paper.getPaperName() : "");
        vo.setDuration(exam.getDuration() != null ? exam.getDuration() : 0);
        vo.setTotalScore(paper != null ? paper.getTotalScore() : BigDecimal.ZERO);
        vo.setQuestions(questions);
        return vo;
    }

    @Override
    @Transactional
    @RedisLock(key = "record:{#id}")
    public void deleteRecord(Long id) {
        ExamRecord record = examRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("考试记录不存在");
        }
        // 删除该记录下的所有答题答案
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, id);
        examAnswerMapper.delete(answerWrapper);
        // 删除考试记录
        examRecordMapper.deleteById(id);

        // 按学生 keyed 驱逐学生端考试列表缓存
        evictStudentExamList(record.getStudentId());
        // 删除记录影响平均分/通过率统计，驱逐图表缓存
        evictAndRefreshChartCache();

        // 清理排行榜 ZSET 中该学生的成员（跟随事务提交，避免回滚后残留不一致）
        Long examId = record.getExamId();
        Long studentId = record.getStudentId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    stringRedisTemplate.opsForZSet().remove("exam:rank:" + examId, String.valueOf(studentId));
                } catch (Exception e) {
                    log.error("清理成绩排行榜失败, examId={}, studentId={}", examId, studentId, e);
                }
            }
        });
    }

    @Override
    @Transactional
    @RedisLock(key = "record:{#recordId}")
    @CacheEvict(cacheNames = "studentExamList", key = "#studentId")
    public SubmitResultVO submitExam(Long recordId, Long studentId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("考试记录不存在");
        }
        // 归属校验：只能提交自己的答卷（防代交卷）
        if (record.getStudentId() == null || !record.getStudentId().equals(studentId)) {
            throw new BusinessException("无权提交该答卷");
        }
        // 防重复交卷：已交卷/已定稿不可再次提交
        if (record.getStatus() != null && record.getStatus() >= ExamConstants.RECORD_STATUS_SUBMITTED) {
            throw new BusinessException("答卷已提交，请勿重复提交");
        }

        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, recordId);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);

        // 客观题自动评分（与学生端正常交卷共用同一套判分逻辑，保证口径一致）
        ObjectiveScoreResult scoreResult = autoScoreObjectiveQuestions(record, answers);
        BigDecimal objectiveScore = scoreResult.objectiveScore;
        int correctCount = scoreResult.correctCount;

        // 练习记录（exam_id IS NULL）无试卷，题目数取作答数；正式考试/作业取试卷题目数（总分分母）
        int totalQuestions = record.getPaperId() != null
                ? paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, record.getPaperId())).size()
                : answers.size();

        // 练习记录无人工批改环节，提交后直达已审核终态（总分为客观分）；正式考试/作业进入待批改
        boolean isPractice = record.getExamId() == null;
        record.setObjectiveScore(objectiveScore);
        record.setSubmitTime(LocalDateTime.now());
        record.setStatus(isPractice
                ? ExamConstants.RECORD_STATUS_AUDITED
                : ExamConstants.RECORD_STATUS_SUBMITTED);
        examRecordMapper.updateById(record);

        // 仿苍穹外卖来单提醒：正式考试/作业交卷后通过 WebSocket 向前端推送待阅卷通知（练习无需人工批改，不推送）
        if (!isPractice) {
            try {
                Exam exam = examMapper.selectById(record.getExamId());
                Student student = studentMapper.selectById(studentId);
                String examName = exam != null ? exam.getExamName() : "未知考试";
                String studentName = student != null ? student.getName() : "未知学生";
                webSocketServer.sendMarkReminder(recordId, examName, studentName);
            } catch (Exception e) {
                log.error("推送待阅卷WebSocket通知失败", e);
            }
        }

        SubmitResultVO result = new SubmitResultVO();
        result.setRecordId(recordId);
        result.setObjectiveScore(objectiveScore);
        result.setCorrectCount(correctCount);
        result.setTotalQuestions(totalQuestions);
        return result;
    }

    @Override
    @Transactional
    @RedisLock(key = "record:{#recordId}")
    public void forceSubmitBySystem(Long recordId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        // 幂等：不存在或非考试中状态（已交卷/已定稿）直接跳过
        if (record == null || record.getStatus() == null
                || record.getStatus() != ExamConstants.RECORD_STATUS_EXAMINING) {
            return;
        }

        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, recordId);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);

        // 与正常交卷共用同一套客观题自动评分，避免强制收卷后客观题按 0 分定稿
        ObjectiveScoreResult scoreResult = autoScoreObjectiveQuestions(record, answers);

        record.setObjectiveScore(scoreResult.objectiveScore);
        record.setSubmitTime(LocalDateTime.now());
        record.setStatus(ExamConstants.RECORD_STATUS_SUBMITTED);
        examRecordMapper.updateById(record);
    }

    /**
     * 客观题自动评分（单选题/多选题/判断题）：答案比对并写入每题得分，
     * 分值优先取试卷自定义分（paper_question.question_score），未设置时兜底题库默认分。
     * submitExam 与系统自动收卷（forceSubmitBySystem）共用，保证判分口径一致。
     */
    private ObjectiveScoreResult autoScoreObjectiveQuestions(ExamRecord record, List<ExamAnswer> answers) {
        // 获取试卷题目及自定义分值
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getPaperId, record.getPaperId());
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(pqWrapper);
        Map<Long, BigDecimal> questionScoreMap = new HashMap<>();
        for (PaperQuestion pq : paperQuestions) {
            questionScoreMap.put(pq.getQuestionId(), pq.getQuestionScore());
        }

        ObjectiveScoreResult result = new ObjectiveScoreResult();
        for (ExamAnswer answer : answers) {
            QuestionBank question = questionBankMapper.selectById(answer.getQuestionId());
            if (question != null && isAutoScoredType(question.getQuestionType())) {
                String correctAnswer = question.getAnswer();
                String studentAnswer = answer.getAnswerContent();
                boolean isCorrect = false;

                if (correctAnswer != null && studentAnswer != null) {
                    // 通用规则：trim 后精确比对（单选/填空题/操作题文本均适用）
                    String ca = correctAnswer.trim();
                    String sa = studentAnswer.trim();

                    if (question.getQuestionType() == ExamConstants.QUESTION_TYPE_MULTIPLE) {
                        // 多选题：对逗号分隔的选项进行排序后再比较，避免顺序影响
                        ca = sortMultiChoice(ca);
                        sa = sortMultiChoice(sa);
                    }

                    if (question.getQuestionType() == ExamConstants.QUESTION_TYPE_JUDGE) {
                        // 判断题：统一对错表示方式，兼容"对/√/正确"和"错/×/错误"
                        ca = normalizeJudgment(ca);
                        sa = normalizeJudgment(sa);
                    }

                    isCorrect = ca.equals(sa);
                }

                // 分值优先取试卷自定义分，未设置时兜底题库默认分
                BigDecimal questionScore = questionScoreMap.get(answer.getQuestionId());
                if (questionScore == null) {
                    questionScore = question.getScore() != null ? question.getScore() : BigDecimal.ZERO;
                }

                if (isCorrect) {
                    result.objectiveScore = result.objectiveScore.add(questionScore);
                    result.correctCount++;
                }
                answer.setIsCorrect(isCorrect ? 1 : 0);
                answer.setScore(isCorrect ? questionScore : BigDecimal.ZERO);
                examAnswerMapper.updateById(answer);
            }
        }
        return result;
    }

    /** 客观题自动评分结果 */
    private static class ObjectiveScoreResult {
        private BigDecimal objectiveScore = BigDecimal.ZERO;
        private int correctCount = 0;
    }

    /**
     * 管理端阅卷用：加载试卷题目（含正确答案）
     */
    private List<QuestionVO> loadQuestionsWithAnswer(Long paperId) {
        List<QuestionVO> questions = new ArrayList<>();
        for (PaperQuestion pq : loadPaperQuestions(paperId)) {
            QuestionBank question = questionBankMapper.selectById(pq.getQuestionId());
            if (question != null) {
                QuestionVO qvo = new QuestionVO();
                BeanUtils.copyProperties(question, qvo);
                qvo.setScore(pq.getQuestionScore());
                qvo.setSortOrder(pq.getSortOrder());
                questions.add(qvo);
            }
        }
        return questions;
    }

    /**
     * 学生端用：加载试卷题目（不含正确答案）
     */
    private List<StudentQuestionVO> loadQuestionsWithoutAnswer(Long paperId) {
        List<StudentQuestionVO> questions = new ArrayList<>();
        for (PaperQuestion pq : loadPaperQuestions(paperId)) {
            QuestionBank question = questionBankMapper.selectById(pq.getQuestionId());
            if (question != null) {
                StudentQuestionVO qvo = new StudentQuestionVO();
                BeanUtils.copyProperties(question, qvo);
                qvo.setScore(pq.getQuestionScore());
                qvo.setSortOrder(pq.getSortOrder());
                questions.add(qvo);
            }
        }
        return questions;
    }

    /**
     * 练习记录（无试卷）用：按作答顺序反查题目，用于练习结果回显
     */
    private List<StudentQuestionVO> loadPracticeQuestions(Long recordId) {
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, recordId);
        answerWrapper.orderByAsc(ExamAnswer::getId);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);
        if (answers.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> questionIds = answers.stream()
                .map(ExamAnswer::getQuestionId)
                .collect(Collectors.toList());
        Map<Long, QuestionBank> questionMap = questionBankMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(QuestionBank::getId, q -> q));
        List<StudentQuestionVO> questions = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            QuestionBank question = questionMap.get(answer.getQuestionId());
            if (question != null) {
                StudentQuestionVO qvo = new StudentQuestionVO();
                BeanUtils.copyProperties(question, qvo);
                questions.add(qvo);
            }
        }
        return questions;
    }

    private List<PaperQuestion> loadPaperQuestions(Long paperId) {
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getPaperId, paperId);
        pqWrapper.orderByAsc(PaperQuestion::getSortOrder);
        return paperQuestionMapper.selectList(pqWrapper);
    }

    /** 练习记录无固定时长，题序乱序的兜底 TTL（秒） */
    private static final long ORDER_TTL_FALLBACK_SECONDS = 24 * 60 * 60;

    /**
     * 防作弊题序乱序：首次开考随机打乱题目顺序并写入 Redis List `exam:order:{recordId}`，
     * 刷新页面/换设备重进时沿用已存顺序（TTL 内不换序）；Redis 异常时回退数据库原序。
     */
    private List<StudentQuestionVO> shuffleWithRedisOrder(List<StudentQuestionVO> questions,
                                                         Long recordId, Integer durationMinutes) {
        if (questions == null || questions.isEmpty()) {
            return questions;
        }
        String key = "exam:order:" + recordId;
        try {
            Long size = stringRedisTemplate.opsForList().size(key);
            if (size != null && size > 0) {
                // 已有乱序：按 Redis 顺序重排（防刷新换序、防换设备错乱）
                List<String> orderIds = stringRedisTemplate.opsForList().range(key, 0, -1);
                return reorderByIds(questions, orderIds);
            }
            // 首次开考：随机打乱并落库顺序
            List<StudentQuestionVO> shuffled = new ArrayList<>(questions);
            Collections.shuffle(shuffled);
            List<String> orderIds = shuffled.stream()
                    .map(q -> String.valueOf(q.getId()))
                    .collect(Collectors.toList());
            stringRedisTemplate.opsForList().rightPushAll(key, orderIds);
            // TTL = 考试剩余时长（分钟→秒）；练习等无固定时长场景用兜底 24 小时
            long ttlSeconds = (durationMinutes != null && durationMinutes > 0)
                    ? durationMinutes * 60L
                    : ORDER_TTL_FALLBACK_SECONDS;
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            return shuffled;
        } catch (Exception e) {
            log.warn("题序乱序写入Redis失败，回退原序, recordId={}", recordId, e);
            return questions;
        }
    }

    /**
     * 学生端回显按 Redis 乱序题序排序（与开考时一致），Redis 异常/无记录时回退原序
     */
    private List<StudentQuestionVO> orderByRedisOrder(List<StudentQuestionVO> questions, Long recordId) {
        if (questions == null || questions.isEmpty()) {
            return questions;
        }
        try {
            List<String> orderIds = stringRedisTemplate.opsForList().range("exam:order:" + recordId, 0, -1);
            if (orderIds != null && !orderIds.isEmpty()) {
                return reorderByIds(questions, orderIds);
            }
        } catch (Exception e) {
            log.warn("读取Redis题序失败，回退原序, recordId={}", recordId, e);
        }
        return questions;
    }

    /** 按 Redis 中的题目ID顺序重排（ID 不存在的题目按原序追加，防止试卷改题后丢题） */
    private List<StudentQuestionVO> reorderByIds(List<StudentQuestionVO> questions, List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return questions;
        }
        Map<Long, StudentQuestionVO> byId = questions.stream()
                .collect(Collectors.toMap(StudentQuestionVO::getId, q -> q));
        List<StudentQuestionVO> ordered = new ArrayList<>();
        for (String idStr : orderIds) {
            StudentQuestionVO q = byId.get(Long.valueOf(idStr));
            if (q != null) {
                ordered.add(q);
            }
        }
        for (StudentQuestionVO q : questions) {
            if (!ordered.contains(q)) {
                ordered.add(q);
            }
        }
        return ordered;
    }

    /**
     * 自动批改题型：单选/多选/判断/单空填空；
     * 主观文字题与操作题走人工批改
     */
    private boolean isAutoScoredType(Integer questionType) {
        return questionType != null && (questionType == ExamConstants.QUESTION_TYPE_SINGLE
                || questionType == ExamConstants.QUESTION_TYPE_MULTIPLE
                || questionType == ExamConstants.QUESTION_TYPE_JUDGE
                || questionType == ExamConstants.QUESTION_TYPE_FILL_BLANK);
    }

    /**
     * 对多选题答案进行排序，使 "B,A" 和 "A,B" 被视为相同
     */
    private String sortMultiChoice(String answer) {
        if (answer == null || answer.isEmpty()) return "";
        return Arrays.stream(answer.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * 统一判断题答案表示方式，兼容各种写法
     *   正确 → 对，√ → 对，T → 对，true → 对
     *   错误 → 错，× → 错，F → 错，false → 错
     */
    private String normalizeJudgment(String answer) {
        if (answer == null || answer.isEmpty()) return "";
        String a = answer.trim();
        if (a.equals("正确") || a.equals("√") || a.equals("T") || a.equals("true") || a.equals("是")) {
            return "对";
        }
        if (a.equals("错误") || a.equals("×") || a.equals("F") || a.equals("false") || a.equals("否")) {
            return "错";
        }
        return a;
    }
}
