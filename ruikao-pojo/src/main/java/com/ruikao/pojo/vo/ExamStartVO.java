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
    private List<QuestionVO> questions;
}
