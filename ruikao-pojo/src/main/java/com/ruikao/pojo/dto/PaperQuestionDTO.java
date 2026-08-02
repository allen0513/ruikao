package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaperQuestionDTO {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    private BigDecimal questionScore;
    private Integer sortOrder;
}