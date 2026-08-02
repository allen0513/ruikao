package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.PaperDTO;
import com.ruikao.pojo.dto.PaperPageQueryDTO;
import com.ruikao.pojo.dto.PaperQuestionDTO;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamPaper;
import com.ruikao.pojo.entity.PaperQuestion;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.pojo.vo.QuestionVO;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamPaperMapper;
import com.ruikao.server.mapper.PaperQuestionMapper;
import com.ruikao.server.mapper.QuestionBankMapper;
import com.ruikao.server.service.ExamPaperService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExamPaperServiceImpl implements ExamPaperService {

    @Autowired
    private ExamPaperMapper examPaperMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private QuestionBankMapper questionBankMapper;

    @Autowired
    private ExamMapper examMapper;

    @Override
    public PageResult<ExamPaper> pageQuery(PaperPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        if (dto.getPaperName() != null && !dto.getPaperName().isEmpty()) {
            wrapper.like(ExamPaper::getPaperName, dto.getPaperName());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(ExamPaper::getStatus, dto.getStatus());
        }
        if (dto.getCreateTimeBegin() != null && !dto.getCreateTimeBegin().isEmpty()) {
            wrapper.ge(ExamPaper::getCreateTime, LocalDate.parse(dto.getCreateTimeBegin()).atStartOfDay());
        }
        if (dto.getCreateTimeEnd() != null && !dto.getCreateTimeEnd().isEmpty()) {
            wrapper.le(ExamPaper::getCreateTime, LocalDate.parse(dto.getCreateTimeEnd()).atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(ExamPaper::getCreateTime);
        List<ExamPaper> list = examPaperMapper.selectList(wrapper);
        Page<ExamPaper> page = (Page<ExamPaper>) list;
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    @Transactional
    public void add(PaperDTO dto) {
        ExamPaper paper = new ExamPaper();
        BeanUtils.copyProperties(dto, paper);
        paper.setCreatorId(BaseContext.getCurrentId());
        paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());
        examPaperMapper.insert(paper);

        BigDecimal total = BigDecimal.ZERO;
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            for (PaperQuestionDTO q : dto.getQuestions()) {
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(paper.getId());
                pq.setQuestionId(q.getQuestionId());
                pq.setSortOrder(q.getSortOrder() != null ? q.getSortOrder() : 0);

                BigDecimal questionScore;
                if (q.getQuestionScore() != null) {
                    questionScore = q.getQuestionScore();
                } else {
                    QuestionBank question = questionBankMapper.selectById(q.getQuestionId());
                    questionScore = question != null && question.getScore() != null ? question.getScore() : BigDecimal.ZERO;
                }
                pq.setQuestionScore(questionScore);
                total = total.add(questionScore);
                paperQuestionMapper.insert(pq);
            }
        }
        // 更新试卷总分
        paper.setTotalScore(total);
        examPaperMapper.updateById(paper);
    }

    @Override
    @Transactional
    public void update(PaperDTO dto) {
        ExamPaper paper = new ExamPaper();
        BeanUtils.copyProperties(dto, paper);
        examPaperMapper.updateById(paper);

        LambdaQueryWrapper<PaperQuestion> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(PaperQuestion::getPaperId, dto.getId());
        paperQuestionMapper.delete(deleteWrapper);

        BigDecimal total = BigDecimal.ZERO;
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            for (PaperQuestionDTO q : dto.getQuestions()) {
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(dto.getId());
                pq.setQuestionId(q.getQuestionId());
                pq.setSortOrder(q.getSortOrder() != null ? q.getSortOrder() : 0);

                BigDecimal questionScore;
                if (q.getQuestionScore() != null) {
                    questionScore = q.getQuestionScore();
                } else {
                    QuestionBank question = questionBankMapper.selectById(q.getQuestionId());
                    questionScore = question != null && question.getScore() != null ? question.getScore() : BigDecimal.ZERO;
                }
                pq.setQuestionScore(questionScore);
                total = total.add(questionScore);
                paperQuestionMapper.insert(pq);
            }
        }
        // 更新试卷总分
        paper.setTotalScore(total);
        examPaperMapper.updateById(paper);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 检查是否有考试引用了该试卷
        LambdaQueryWrapper<Exam> examQuery = new LambdaQueryWrapper<>();
        examQuery.eq(Exam::getPaperId, id);
        Long examCount = examMapper.selectCount(examQuery);
        if (examCount > 0) {
            throw new BusinessException("该试卷已被考试引用，无法删除");
        }

        LambdaQueryWrapper<PaperQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperQuestion::getPaperId, id);
        paperQuestionMapper.delete(wrapper);
        examPaperMapper.deleteById(id);
    }

    @Override
    public ExamPaper getDetail(Long id) {
        return examPaperMapper.selectById(id);
    }

    @Override
    public List<QuestionVO> getPaperQuestions(Long paperId) {
        LambdaQueryWrapper<PaperQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperQuestion::getPaperId, paperId);
        wrapper.orderByAsc(PaperQuestion::getSortOrder);
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(wrapper);

        List<QuestionVO> voList = new ArrayList<>();
        for (PaperQuestion pq : paperQuestions) {
            QuestionBank question = questionBankMapper.selectById(pq.getQuestionId());
            if (question != null) {
                QuestionVO vo = new QuestionVO();
                BeanUtils.copyProperties(question, vo);
                vo.setScore(pq.getQuestionScore());
                vo.setSortOrder(pq.getSortOrder());
                voList.add(vo);
            }
        }
        return voList;
    }

    @Override
    public void publish(Long id) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper != null) {
            paper.setStatus(1);
            examPaperMapper.updateById(paper);
        }
    }
}