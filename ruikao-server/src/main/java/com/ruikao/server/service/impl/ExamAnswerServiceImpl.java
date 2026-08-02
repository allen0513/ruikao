package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.server.mapper.ExamAnswerMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.service.ExamAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamAnswerServiceImpl implements ExamAnswerService {

    private final ExamAnswerMapper examAnswerMapper;

    private final ExamRecordMapper examRecordMapper;

    @Override
    @Transactional
    public void save(AnswerSubmitDTO dto, Long studentId) {
        // 归属校验：只能保存自己的答卷
        ExamRecord record = examRecordMapper.selectById(dto.getRecordId());
        if (record == null || record.getStudentId() == null || !record.getStudentId().equals(studentId)) {
            throw new BusinessException("无权保存该答卷");
        }
        // 状态校验：仅开考中可作答；已交卷/已定稿不可再写入（防覆盖已交卷/已批阅成绩）
        if (record.getStatus() != null && record.getStatus() >= ExamConstants.RECORD_STATUS_SUBMITTED) {
            throw new BusinessException("答卷已交卷，不可再修改");
        }

        LambdaQueryWrapper<ExamAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswer::getRecordId, dto.getRecordId());
        wrapper.eq(ExamAnswer::getQuestionId, dto.getQuestionId());
        examAnswerMapper.delete(wrapper);

        ExamAnswer answer = new ExamAnswer();
        answer.setRecordId(dto.getRecordId());
        answer.setQuestionId(dto.getQuestionId());
        answer.setAnswerContent(dto.getAnswerContent());
        examAnswerMapper.insert(answer);
    }

    @Override
    public List<ExamAnswer> getByRecordId(Long recordId, Long studentId) {
        // 归属校验：只能查看自己的答卷
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null || record.getStudentId() == null || !record.getStudentId().equals(studentId)) {
            throw new BusinessException("无权查看该答卷");
        }
        LambdaQueryWrapper<ExamAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswer::getRecordId, recordId);
        return examAnswerMapper.selectList(wrapper);
    }
}