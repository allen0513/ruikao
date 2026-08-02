package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.entity.PaperQuestion;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.server.mapper.ExamAnswerMapper;
import com.ruikao.server.mapper.PaperQuestionMapper;
import com.ruikao.server.mapper.QuestionBankMapper;
import com.ruikao.server.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionBankMapper questionBankMapper;

    private final PaperQuestionMapper paperQuestionMapper;

    private final ExamAnswerMapper examAnswerMapper;

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
        // 被试卷引用的题目禁止删除，防止外键级联静默改写已发布试卷
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getQuestionId, id);
        if (paperQuestionMapper.selectCount(pqWrapper) > 0) {
            throw new BusinessException("该题目已被试卷引用，无法删除");
        }
        // 已有作答记录的题目禁止删除，防止答卷数据失效
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getQuestionId, id);
        if (examAnswerMapper.selectCount(answerWrapper) > 0) {
            throw new BusinessException("该题目已有作答记录，无法删除");
        }
        questionBankMapper.deleteById(id);
    }

    @Override
    public QuestionBank getDetail(Long id) {
        return questionBankMapper.selectById(id);
    }
}
