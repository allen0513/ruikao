package com.ruikao.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubmitResultVO {
    private Long recordId;
    private BigDecimal objectiveScore;
    private Integer correctCount;
    private Integer totalQuestions;
}
