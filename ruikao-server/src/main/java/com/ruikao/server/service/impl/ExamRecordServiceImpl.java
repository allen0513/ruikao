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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
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

    @Override
    public PageResult<RecordVO> pageQuery(RecordPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
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

        // 题目（不含正确答案）
        if (record.getPaperId() != null) {
            vo.setQuestions(loadQuestionsWithoutAnswer(record.getPaperId()));
        }

        // 答案列表
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, recordId);
        vo.setAnswers(examAnswerMapper.selectList(answerWrapper));

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

        // 已定稿的答卷禁止改分（防止定稿后篡改成绩）
        ExamRecord record = examRecordMapper.selectById(answer.getRecordId());
        if (record != null && record.getStatus() != null && record.getStatus() == ExamConstants.RECORD_STATUS_FINALIZED) {
            throw new BusinessException("答卷已定稿，不可再评分");
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

    /** 驱逐图表统计缓存：定稿/删除记录影响平均分、通过率等统计 */
    private void evictChartCache() {
        Cache cache = cacheManager.getCache("chart");
        if (cache != null) {
            cache.clear();
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
        // 只允许对已交卷的答卷定稿，防止未交卷或重复定稿
        if (record.getStatus() == null || record.getStatus() != ExamConstants.RECORD_STATUS_SUBMITTED) {
            throw new BusinessException("仅已交卷的答卷可以定稿");
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
                // 计算已打分的主观题（简答题）
                QuestionBank question = questionBankMapper.selectById(answer.getQuestionId());
                if (question != null && question.getQuestionType() == ExamConstants.QUESTION_TYPE_SUBJECTIVE) {
                    subjectiveScore = subjectiveScore.add(answer.getScore());
                }
            }
        }

        record.setScore(totalScore);
        record.setSubjectiveScore(subjectiveScore);
        record.setObjectiveScore(objectiveScore);
        record.setStatus(ExamConstants.RECORD_STATUS_FINALIZED);
        // 注意：不要覆盖 submitTime，保留学生的真实交卷时间
        examRecordMapper.updateById(record);

        // 按学生 keyed 驱逐学生端考试列表缓存（该学生可立即看到定稿成绩）
        evictStudentExamList(record.getStudentId());
        // 定稿影响平均分/通过率统计，驱逐图表缓存
        evictChartCache();

        // 成绩定稿后写入 Redis 排行榜（ZSET：member=studentId, score=总分）
        // 注册 afterCommit 回调：排行榜写入跟随事务提交，避免事务回滚后产生脏数据
        Long examId = record.getExamId();
        Long studentId = record.getStudentId();
        double rankScore = totalScore.doubleValue();
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
                vo.setExamStatus(exam.getStatus());
                vo.setRecordId(record.getId());
                vo.setRecordStatus(record.getStatus());
                vo.setScore(record.getScore());
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
     * 组装开考返回（题目不含正确答案）
     */
    private ExamStartVO buildStartVO(ExamRecord record, Exam exam) {
        List<StudentQuestionVO> questions = exam.getPaperId() != null
                ? loadQuestionsWithoutAnswer(exam.getPaperId())
                : new ArrayList<>();

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
        evictChartCache();

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

        // 获取试卷题目数（总分分母）
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getPaperId, record.getPaperId());
        int totalQuestions = paperQuestionMapper.selectList(pqWrapper).size();

        record.setObjectiveScore(objectiveScore);
        record.setSubmitTime(LocalDateTime.now());
        record.setStatus(ExamConstants.RECORD_STATUS_SUBMITTED);
        examRecordMapper.updateById(record);

        // 仿苍穹外卖来单提醒：通过 WebSocket 向前端推送待阅卷通知
        try {
            Exam exam = examMapper.selectById(record.getExamId());
            Student student = studentMapper.selectById(studentId);
            String examName = exam != null ? exam.getExamName() : "未知考试";
            String studentName = student != null ? student.getName() : "未知学生";
            webSocketServer.sendMarkReminder(recordId, examName, studentName);
        } catch (Exception e) {
            log.error("推送待阅卷WebSocket通知失败", e);
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
            if (question != null && question.getQuestionType() >= ExamConstants.QUESTION_TYPE_SINGLE
                    && question.getQuestionType() <= ExamConstants.QUESTION_TYPE_JUDGE) {
                String correctAnswer = question.getAnswer();
                String studentAnswer = answer.getAnswerContent();
                boolean isCorrect = false;

                if (correctAnswer != null && studentAnswer != null) {
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

    private List<PaperQuestion> loadPaperQuestions(Long paperId) {
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getPaperId, paperId);
        pqWrapper.orderByAsc(PaperQuestion::getSortOrder);
        return paperQuestionMapper.selectList(pqWrapper);
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
