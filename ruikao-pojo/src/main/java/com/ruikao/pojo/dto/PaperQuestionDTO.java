package com.ruikao.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaperQuestionDTO {
    private Long questionId;
    private BigDecimal questionScore;
    private Integer sortOrder;
}