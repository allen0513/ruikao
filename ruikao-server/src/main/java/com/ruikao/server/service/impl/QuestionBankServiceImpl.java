package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.server.mapper.QuestionBankMapper;
import com.ruikao.server.service.QuestionBankService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionBankServiceImpl implements QuestionBankService {

    @Autowired
    private QuestionBankMapper questionBankMapper;

    @Override
    public PageResult<QuestionBank> pageQuery(QuestionPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (dto.getQuestionType() != null) {
            wrapper.eq(QuestionBank::getQuestionType, dto.getQuestionType());
        }
        if (dto.getDifficulty() != null) {
            wrapper.eq(QuestionBank::getDifficulty, dto.getDifficulty());
        }
        if (dto.getQuestionContent() != null && !dto.getQuestionContent().isEmpty()) {
            wrapper.like(QuestionBank::getQuestionContent, dto.getQuestionContent());
        }
        wrapper.orderByDesc(QuestionBank::getCreateTime);
        List<QuestionBank> list = questionBankMapper.selectList(wrapper);
        Page<QuestionBank> page = (Page<QuestionBank>) list;
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    public void add(QuestionDTO dto) {
        QuestionBank question = new QuestionBank();
        BeanUtils.copyProperties(dto, question);
        question.setCreatorId(BaseContext.getCurrentId());
        questionBankMapper.insert(question);
    }

    @Override
    public void update(QuestionDTO dto) {
        QuestionBank question = new QuestionBank();
        BeanUtils.copyProperties(dto, question);
        questionBankMapper.updateById(question);
    }

    @Override
    public void delete(Long id) {
        questionBankMapper.deleteById(id);
    }

    @Override
    public QuestionBank getDetail(Long id) {
        return questionBankMapper.selectById(id);
    }
}
