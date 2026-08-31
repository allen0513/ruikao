package com.ruikao.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExamStartVO {
    private Long recordId;
    private Long paperId;
    private String paperName;
    private Integer duration;
    private BigDecimal totalScore;
    /** 开考题目（不含正确答案） */
    private List<StudentQuestionVO> questions;
}
