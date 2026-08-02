package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.RecordPageQueryDTO;
import com.ruikao.pojo.dto.ScoreDTO;
import com.ruikao.pojo.entity.*;
import com.ruikao.pojo.vo.ExamStartVO;
import com.ruikao.pojo.vo.QuestionVO;
import com.ruikao.pojo.vo.RecordVO;
import com.ruikao.pojo.vo.StudentExamVO;
import com.ruikao.pojo.vo.SubmitResultVO;
import com.ruikao.server.annotation.RedisLock;
import com.ruikao.server.mapper.*;
import com.ruikao.server.service.ExamRecordService;
import com.ruikao.server.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExamRecordServiceImpl implements ExamRecordService {

    @Autowired
    private ExamRecordMapper examRecordMapper;

    @Autowired
    private ExamAnswerMapper examAnswerMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamPaperMapper examPaperMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private ExamStudentMapper examStudentMapper;

    @Autowired
    private QuestionBankMapper questionBankMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

        List<RecordVO> voList = recordList.stream().map(record -> {
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

        // 加载题目列表
        if (record.getPaperId() != null) {
            LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
            pqWrapper.eq(PaperQuestion::getPaperId, record.getPaperId());
            pqWrapper.orderByAsc(PaperQuestion::getSortOrder);
            List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(pqWrapper);

            List<QuestionVO> questions = new ArrayList<>();
            for (PaperQuestion pq : paperQuestions) {
                QuestionBank question = questionBankMapper.selectById(pq.getQuestionId());
                if (question != null) {
                    QuestionVO qvo = new QuestionVO();
                    BeanUtils.copyProperties(question, qvo);
                    qvo.setScore(pq.getQuestionScore());
                    qvo.setSortOrder(pq.getSortOrder());
                    questions.add(qvo);
                }
            }
            vo.setQuestions(questions);
        }

        // 加载答案列表
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, id);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);
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
    @RedisLock(key = "score:{#dto.answerId ?: (#dto.recordId + '-' + #dto.questionId)}")
    public void score(ScoreDTO dto) {
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
        if (answer != null) {
            answer.setScore(dto.getScore());
            examAnswerMapper.updateById(answer);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public void completeMarking(Long id) {
        ExamRecord record = examRecordMapper.selectById(id);
        if (record == null) {
            return;
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
                // 计算已打分的主观题（简答题 type=3）
                QuestionBank question = questionBankMapper.selectById(answer.getQuestionId());
                if (question != null && question.getQuestionType() == 3) {
                    subjectiveScore = subjectiveScore.add(answer.getScore());
                }
            }
        }

        record.setScore(totalScore);
        record.setSubjectiveScore(subjectiveScore);
        record.setObjectiveScore(objectiveScore);
        record.setStatus(3);
        // 注意：不要覆盖 submitTime，保留学生的真实交卷时间
        examRecordMapper.updateById(record);

        // 成绩定稿后写入 Redis 排行榜（ZSET：member=studentId, score=总分）
        try {
            stringRedisTemplate.opsForZSet().add(
                    "exam:rank:" + record.getExamId(),
                    String.valueOf(record.getStudentId()),
                    totalScore.doubleValue());
        } catch (Exception e) {
            log.error("写入成绩排行榜失败, examId={}, studentId={}", record.getExamId(), record.getStudentId(), e);
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
    public ExamStartVO startExam(Long examId, Long studentId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            return null;
        }

        // 考试必须关联试卷才能开始
        if (exam.getPaperId() == null) {
            throw new BusinessException("该考试未关联试卷，请联系管理员");
        }

        ExamRecord record = new ExamRecord();
        record.setExamId(examId);
        record.setStudentId(studentId);
        record.setPaperId(exam.getPaperId());
        record.setStartTime(LocalDateTime.now());
        record.setStatus(1);
        examRecordMapper.insert(record);

        // 获取试卷题目
        List<QuestionVO> questions = new ArrayList<>();
        if (exam.getPaperId() != null) {
            LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
            pqWrapper.eq(PaperQuestion::getPaperId, exam.getPaperId());
            pqWrapper.orderByAsc(PaperQuestion::getSortOrder);
            List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(pqWrapper);

            for (PaperQuestion pq : paperQuestions) {
                QuestionBank question = questionBankMapper.selectById(pq.getQuestionId());
                if (question != null) {
                    QuestionVO vo = new QuestionVO();
                    BeanUtils.copyProperties(question, vo);
                    vo.setScore(pq.getQuestionScore());
                    vo.setSortOrder(pq.getSortOrder());
                    questions.add(vo);
                }
            }
        }

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
    }

    @Override
    @Transactional
    @RedisLock(key = "submit:{#recordId}")
    @CacheEvict(cacheNames = "studentExamList", allEntries = true)
    public SubmitResultVO submitExam(Long recordId, Long studentId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            return null;
        }

        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getRecordId, recordId);
        List<ExamAnswer> answers = examAnswerMapper.selectList(answerWrapper);

        BigDecimal objectiveScore = BigDecimal.ZERO;
        int correctCount = 0;
        int totalQuestions = 0;

        // 获取试卷题目列表用于计算总分
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getPaperId, record.getPaperId());
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(pqWrapper);
        totalQuestions = paperQuestions.size();

        // 自动评分：客观题（单选题0、多选题1、判断题2）
        for (ExamAnswer answer : answers) {
            QuestionBank question = questionBankMapper.selectById(answer.getQuestionId());
            if (question != null && question.getQuestionType() >= 0 && question.getQuestionType() <= 2) {
                String correctAnswer = question.getAnswer();
                String studentAnswer = answer.getAnswerContent();
                boolean isCorrect = false;

                if (correctAnswer != null && studentAnswer != null) {
                    String ca = correctAnswer.trim();
                    String sa = studentAnswer.trim();

                    if (question.getQuestionType() == 1) {
                        // 多选题：对逗号分隔的选项进行排序后再比较，避免顺序影响
                        ca = sortMultiChoice(ca);
                        sa = sortMultiChoice(sa);
                    }

                    if (question.getQuestionType() == 2) {
                        // 判断题：统一对错表示方式，兼容"对/√/正确"和"错/×/错误"
                        ca = normalizeJudgment(ca);
                        sa = normalizeJudgment(sa);
                    }

                    isCorrect = ca.equals(sa);
                }

                if (isCorrect) {
                    objectiveScore = objectiveScore.add(question.getScore() != null ? question.getScore() : BigDecimal.ZERO);
                    correctCount++;
                }
                answer.setIsCorrect(isCorrect ? 1 : 0);
                answer.setScore(isCorrect ? question.getScore() : BigDecimal.ZERO);
                examAnswerMapper.updateById(answer);
            }
        }

        record.setObjectiveScore(objectiveScore);
        record.setSubmitTime(LocalDateTime.now());
        record.setStatus(2);
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
