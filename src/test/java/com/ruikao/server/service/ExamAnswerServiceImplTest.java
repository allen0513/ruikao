package com.ruikao.server.service;

import com.ruikao.common.exception.BusinessException;
import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.server.mapper.ExamAnswerMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.service.impl.ExamAnswerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 答卷保存归属与状态校验测试
 */
@ExtendWith(MockitoExtension.class)
class ExamAnswerServiceImplTest {

    @Mock
    private ExamAnswerMapper examAnswerMapper;

    @Mock
    private ExamRecordMapper examRecordMapper;

    @InjectMocks
    private ExamAnswerServiceImpl examAnswerService;

    private AnswerSubmitDTO dto(Long recordId, Long questionId) {
        AnswerSubmitDTO dto = new AnswerSubmitDTO();
        dto.setRecordId(recordId);
        dto.setQuestionId(questionId);
        dto.setAnswerContent("A");
        return dto;
    }

    private ExamRecord record(Long studentId, Integer status) {
        ExamRecord record = new ExamRecord();
        record.setId(1L);
        record.setStudentId(studentId);
        record.setStatus(status);
        return record;
    }

    @Test
    void save_recordNotOwned_throws() {
        when(examRecordMapper.selectById(1L)).thenReturn(record(99L, 1));
        assertThrows(BusinessException.class, () -> examAnswerService.save(dto(1L, 11L), 1L));
    }

    @Test
    void save_submittedRecord_throws() {
        // 已交卷（status=2）不可再改答案 —— 覆盖原审查缺口（原实现只拦 status=3）
        when(examRecordMapper.selectById(1L)).thenReturn(record(1L, 2));
        assertThrows(BusinessException.class, () -> examAnswerService.save(dto(1L, 11L), 1L));
    }

    @Test
    void save_finalizedRecord_throws() {
        when(examRecordMapper.selectById(1L)).thenReturn(record(1L, 3));
        assertThrows(BusinessException.class, () -> examAnswerService.save(dto(1L, 11L), 1L));
    }

    @Test
    void save_examiningRecord_succeeds() {
        when(examRecordMapper.selectById(1L)).thenReturn(record(1L, 1));
        assertDoesNotThrow(() -> examAnswerService.save(dto(1L, 11L), 1L));
        verify(examAnswerMapper).delete(any());
        verify(examAnswerMapper).insert(any(ExamAnswer.class));
    }

    @Test
    void getByRecordId_notOwned_throws() {
        when(examRecordMapper.selectById(1L)).thenReturn(record(99L, 1));
        assertThrows(BusinessException.class, () -> examAnswerService.getByRecordId(1L, 1L));
    }
}