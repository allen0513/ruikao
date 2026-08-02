package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.server.mapper.ExamAnswerMapper;
import com.ruikao.server.service.ExamAnswerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamAnswerServiceImpl implements ExamAnswerService {

    @Autowired
    private ExamAnswerMapper examAnswerMapper;

    @Override
    @Transactional
    public void save(AnswerSubmitDTO dto) {
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
    public List<ExamAnswer> getByRecordId(Long recordId) {
        LambdaQueryWrapper<ExamAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswer::getRecordId, recordId);
        return examAnswerMapper.selectList(wrapper);
    }
}
