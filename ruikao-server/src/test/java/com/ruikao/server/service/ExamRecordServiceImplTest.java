package com.ruikao.server.service;

import com.ruikao.common.exception.BusinessException;
import com.ruikao.pojo.dto.ScoreDTO;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.pojo.entity.PaperQuestion;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.pojo.entity.Student;
import com.ruikao.pojo.vo.SubmitResultVO;
import com.ruikao.server.mapper.ExamAnswerMapper;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamPaperMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.mapper.ExamStudentMapper;
import com.ruikao.server.mapper.PaperQuestionMapper;
import com.ruikao.server.mapper.QuestionBankMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.service.impl.ExamRecordServiceImpl;
import com.ruikao.server.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核心评分与定稿逻辑测试：自动评分分值来源、防重复交卷、定稿幂等、评分上限
 */
@ExtendWith(MockitoExtension.class)
class ExamRecordServiceImplTest {

    @Mock
    private ExamRecordMapper examRecordMapper;
    @Mock
    private ExamAnswerMapper examAnswerMapper;
    @Mock
    private ExamMapper examMapper;
    @Mock
    private ExamPaperMapper examPaperMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private PaperQuestionMapper paperQuestionMapper;
    @Mock
    private ExamStudentMapper examStudentMapper;
    @Mock
    private QuestionBankMapper questionBankMapper;
    @Mock
    private WebSocketServer webSocketServer;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ObjectProvider<ExamRecordServiceImpl> selfProvider;
    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private ExamRecordServiceImpl examRecordServiceImpl;

    @BeforeEach
    void wireSelfProxy() {
        // score() 经自身代理获取记录级锁，单测中直接指向被测实例（切面逻辑不在单测范围）；
        // 仅 score 相关用例使用，故 lenient 避免严格模式报多余 stub
        lenient().when(selfProvider.getObject()).thenReturn(examRecordServiceImpl);
    }

    @AfterEach
    void cleanTxSync() {
        // completeMarking 会注册事务同步器，测试结束后清理，避免线程状态泄漏
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------- submitExam 自动评分 ----------

    @Test
    void submitExam_scoresWithPaperCustomScore() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setExamId(1L);
        record.setStudentId(1L);
        record.setPaperId(1L);
        record.setStatus(1);
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        // 试卷自定义分：题11=5分，题12=10分
        PaperQuestion pq1 = new PaperQuestion();
        pq1.setPaperId(1L);
        pq1.setQuestionId(11L);
        pq1.setQuestionScore(BigDecimal.valueOf(5));
        PaperQuestion pq2 = new PaperQuestion();
        pq2.setPaperId(1L);
        pq2.setQuestionId(12L);
        pq2.setQuestionScore(BigDecimal.valueOf(10));
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(pq1, pq2));

        // 学生答案：题11答对（A），题12答错（选B，正确答案C）
        ExamAnswer a1 = answer(1L, 11L, "A");
        ExamAnswer a2 = answer(1L, 12L, "B");
        when(examAnswerMapper.selectList(any())).thenReturn(List.of(a1, a2));

        QuestionBank q1 = questionBank(11L, 0, "A", BigDecimal.valueOf(5));
        QuestionBank q2 = questionBank(12L, 0, "C", BigDecimal.valueOf(10));
        when(questionBankMapper.selectById(11L)).thenReturn(q1);
        when(questionBankMapper.selectById(12L)).thenReturn(q2);

        when(examMapper.selectById(1L)).thenReturn(new Exam());
        when(studentMapper.selectById(1L)).thenReturn(new Student());

        SubmitResultVO result = examRecordServiceImpl.submitExam(1L, 1L);

        // 客观题得分 = 5（题12 即使题库默认分 10，答错也不得分；题11 按自定义分 5 计）
        assertEquals(0, BigDecimal.valueOf(5).compareTo(result.getObjectiveScore()));
        assertEquals(1, result.getCorrectCount());
        // 答卷状态置为已交卷
        ArgumentCaptor<ExamRecord> captor = ArgumentCaptor.forClass(ExamRecord.class);
        verify(examRecordMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());
    }

    @Test
    void submitExam_fallsBackToBankScoreWhenPaperScoreMissing() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setExamId(1L);
        record.setStudentId(1L);
        record.setPaperId(1L);
        record.setStatus(1);
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        // 试卷未设置自定义分
        PaperQuestion pq = new PaperQuestion();
        pq.setPaperId(1L);
        pq.setQuestionId(11L);
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(pq));

        ExamAnswer a1 = answer(1L, 11L, "A");
        when(examAnswerMapper.selectList(any())).thenReturn(List.of(a1));

        // 题库默认分 8
        when(questionBankMapper.selectById(11L)).thenReturn(questionBank(11L, 0, "A", BigDecimal.valueOf(8)));
        when(examMapper.selectById(1L)).thenReturn(new Exam());
        when(studentMapper.selectById(1L)).thenReturn(new Student());

        SubmitResultVO result = examRecordServiceImpl.submitExam(1L, 1L);

        assertEquals(0, BigDecimal.valueOf(8).compareTo(result.getObjectiveScore()));
    }

    @Test
    void submitExam_notOwned_throws() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setExamId(1L);
        record.setStudentId(99L); // 他人答卷
        record.setStatus(1);
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> examRecordServiceImpl.submitExam(1L, 1L));
    }

    @Test
    void submitExam_alreadySubmitted_throws() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setExamId(1L);
        record.setStudentId(1L);
        record.setStatus(2); // 已交卷
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> examRecordServiceImpl.submitExam(1L, 1L));
        verify(examRecordMapper, never()).updateById(any(ExamRecord.class));
    }

    // ---------- forceSubmitBySystem 自动收卷 ----------

    @Test
    void forceSubmitBySystem_scoresObjectiveQuestions() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setExamId(1L);
        record.setStudentId(1L);
        record.setPaperId(1L);
        record.setStatus(1); // 考试中，未交卷
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        PaperQuestion pq = new PaperQuestion();
        pq.setPaperId(1L);
        pq.setQuestionId(11L);
        pq.setQuestionScore(BigDecimal.valueOf(5));
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(pq));

        ExamAnswer a1 = answer(1L, 11L, "A");
        when(examAnswerMapper.selectList(any())).thenReturn(List.of(a1));
        when(questionBankMapper.selectById(11L)).thenReturn(questionBank(11L, 0, "A", BigDecimal.valueOf(5)));

        examRecordServiceImpl.forceSubmitBySystem(1L);

        // 强制收卷与正常交卷同一判分口径：客观题得分已写入并计入客观分
        ArgumentCaptor<ExamRecord> captor = ArgumentCaptor.forClass(ExamRecord.class);
        verify(examRecordMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(captor.getValue().getObjectiveScore()));

        ArgumentCaptor<ExamAnswer> answerCaptor = ArgumentCaptor.forClass(ExamAnswer.class);
        verify(examAnswerMapper, atLeastOnce()).updateById(answerCaptor.capture());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(answerCaptor.getValue().getScore()));
        assertEquals(1, answerCaptor.getValue().getIsCorrect());
    }

    @Test
    void forceSubmitBySystem_alreadySubmitted_skips() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setStatus(2); // 已交卷：幂等跳过
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        examRecordServiceImpl.forceSubmitBySystem(1L);

        verify(examAnswerMapper, never()).selectList(any());
        verify(examRecordMapper, never()).updateById(any(ExamRecord.class));
    }

    // ---------- completeMarking 定稿 ----------

    @Test
    void completeMarking_notSubmitted_throws() {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setStatus(1); // 未交卷
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> examRecordServiceImpl.completeMarking(1L));
    }

    @Test
    void completeMarking_finalizesAndWritesRankAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setExamId(1L);
        record.setStudentId(1L);
        record.setPaperId(1L);
        record.setStatus(2); // 已交卷
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        // 主观题答得 8 分
        ExamAnswer a1 = answer(1L, 11L, "我的答案");
        a1.setScore(BigDecimal.valueOf(8));
        when(examAnswerMapper.selectList(any())).thenReturn(List.of(a1));
        when(questionBankMapper.selectById(11L)).thenReturn(questionBank(11L, 3, "参考答案", BigDecimal.valueOf(10)));

        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zset);

        examRecordServiceImpl.completeMarking(1L);

        // 状态置为已定稿，总分=主观 8 + 客观 0
        ArgumentCaptor<ExamRecord> captor = ArgumentCaptor.forClass(ExamRecord.class);
        verify(examRecordMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus());
        assertEquals(0, BigDecimal.valueOf(8).compareTo(captor.getValue().getScore()));

        // 排行榜写入注册在 afterCommit 回调内：提交后才写 ZSET
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(zset).add(eq("exam:rank:1"), eq("1"), eq(8.0));
    }

    // ---------- score 评分上限 ----------

    @Test
    void score_exceedsPaperScore_throws() {
        ExamAnswer answer = answer(1L, 11L, "A");
        when(examAnswerMapper.selectById(1L)).thenReturn(answer);

        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setPaperId(1L);
        record.setStatus(2); // 未定稿，允许评分
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        PaperQuestion pq = new PaperQuestion();
        pq.setPaperId(1L);
        pq.setQuestionId(11L);
        pq.setQuestionScore(BigDecimal.valueOf(5));
        when(paperQuestionMapper.selectOne(any())).thenReturn(pq);

        ScoreDTO dto = new ScoreDTO();
        dto.setAnswerId(1L);
        dto.setScore(BigDecimal.valueOf(6)); // 超上限
        assertThrows(BusinessException.class, () -> examRecordServiceImpl.score(dto));
    }

    @Test
    void score_finalizedRecord_throws() {
        ExamAnswer answer = answer(1L, 11L, "A");
        when(examAnswerMapper.selectById(1L)).thenReturn(answer);

        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setPaperId(1L);
        record.setStatus(3); // 已定稿
        when(examRecordMapper.selectById(1L)).thenReturn(record);

        ScoreDTO dto = new ScoreDTO();
        dto.setAnswerId(1L);
        dto.setScore(BigDecimal.valueOf(4));
        assertThrows(BusinessException.class, () -> examRecordServiceImpl.score(dto));
        verify(examAnswerMapper, never()).updateById(any(ExamAnswer.class));
    }

    // ---------- helpers ----------

    private ExamAnswer answer(Long recordId, Long questionId, String content) {
        ExamAnswer a = new ExamAnswer();
        a.setId(questionId);
        a.setRecordId(recordId);
        a.setQuestionId(questionId);
        a.setAnswerContent(content);
        return a;
    }

    private QuestionBank questionBank(Long id, int type, String answer, BigDecimal score) {
        QuestionBank q = new QuestionBank();
        q.setId(id);
        q.setQuestionType(type);
        q.setAnswer(answer);
        q.setScore(score);
        return q;
    }
}