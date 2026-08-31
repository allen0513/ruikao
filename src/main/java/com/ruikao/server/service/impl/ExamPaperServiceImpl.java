package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.AutoPaperDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamPaperServiceImpl implements ExamPaperService {

    private final ExamPaperMapper examPaperMapper;

    private final PaperQuestionMapper paperQuestionMapper;

    private final QuestionBankMapper questionBankMapper;

    private final ExamMapper examMapper;

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
    public void autoGenerate(AutoPaperDTO dto) {
        // 校验：每项抽题数量必须大于 0（由 @Min 兜底，这里防御空集合）
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("组卷配置不能为空");
        }

        // 按配置逐项随机抽题，落库前统一校验库存，避免写了一半再回滚
        List<QuestionBank> picked = new ArrayList<>();
        for (AutoPaperDTO.Item item : dto.getItems()) {
            if (item.getCount() == null || item.getCount() <= 0) {
                throw new BusinessException("抽题数量必须大于0");
            }
            LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuestionBank::getSubjectId, dto.getSubjectId());
            if (item.getQuestionType() != null) {
                wrapper.eq(QuestionBank::getQuestionType, item.getQuestionType());
            }
            if (item.getDifficulty() != null) {
                wrapper.eq(QuestionBank::getDifficulty, item.getDifficulty());
            }
            List<QuestionBank> candidates = new ArrayList<>(questionBankMapper.selectList(wrapper));
            Collections.shuffle(candidates);
            if (candidates.size() < item.getCount()) {
                throw new BusinessException(String.format("该难度[%s]的[%s]库存不足，需要%d道，现有%d道",
                        difficultyName(item.getDifficulty()), typeName(item.getQuestionType()),
                        item.getCount(), candidates.size()));
            }
            picked.addAll(candidates.subList(0, item.getCount()));
        }

        // 落库：草稿试卷 + 题目关联（sortOrder 按组卷配置顺序递增，题目在每题型内随机）
        ExamPaper paper = new ExamPaper();
        paper.setPaperName(dto.getPaperName());
        paper.setDuration(dto.getDuration());
        paper.setStatus(0);
        paper.setCreatorId(BaseContext.getCurrentId());
        paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());
        examPaperMapper.insert(paper);

        BigDecimal total = BigDecimal.ZERO;
        int sortOrder = 1;
        for (AutoPaperDTO.Item item : dto.getItems()) {
            for (int i = 0; i < item.getCount(); i++) {
                QuestionBank question = picked.get(sortOrder - 1);
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(paper.getId());
                pq.setQuestionId(question.getId());
                pq.setSortOrder(sortOrder++);
                // 分值优先取组卷配置，未配置取题库默认分
                BigDecimal questionScore = item.getScore() != null
                        ? item.getScore()
                        : (question.getScore() != null ? question.getScore() : BigDecimal.ZERO);
                pq.setQuestionScore(questionScore);
                total = total.add(questionScore);
                paperQuestionMapper.insert(pq);
            }
        }
        paper.setTotalScore(total);
        examPaperMapper.updateById(paper);
    }

    /** 难度中文名（库存不足报错文案用） */
    private String difficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "不限";
        }
        return switch (difficulty) {
            case ExamConstants.QUESTION_DIFFICULTY_EASY -> "简单";
            case ExamConstants.QUESTION_DIFFICULTY_MEDIUM -> "中等";
            case ExamConstants.QUESTION_DIFFICULTY_HARD -> "困难";
            default -> String.valueOf(difficulty);
        };
    }

    /** 题型中文名（库存不足报错文案用） */
    private String typeName(Integer questionType) {
        if (questionType == null) {
            return "不限题型";
        }
        return switch (questionType) {
            case ExamConstants.QUESTION_TYPE_SINGLE -> "单选题";
            case ExamConstants.QUESTION_TYPE_MULTIPLE -> "多选题";
            case ExamConstants.QUESTION_TYPE_JUDGE -> "判断题";
            case ExamConstants.QUESTION_TYPE_SUBJECTIVE -> "简答题";
            case ExamConstants.QUESTION_TYPE_FILL_BLANK -> "填空题";
            case ExamConstants.QUESTION_TYPE_OPERATION -> "操作题";
            default -> "题型" + questionType;
        };
    }

    @Override
    @Transactional
    public void update(PaperDTO dto) {
        // 被考试引用的试卷不允许改题：update 先删后插，改题会中途更换已发布试卷的题目与分值
        LambdaQueryWrapper<Exam> examQuery = new LambdaQueryWrapper<>();
        examQuery.eq(Exam::getPaperId, dto.getId());
        if (examMapper.selectCount(examQuery) > 0) {
            throw new BusinessException("该试卷已被考试引用，不允许修改题目");
        }

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