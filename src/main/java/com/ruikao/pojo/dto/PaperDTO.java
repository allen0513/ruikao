package com.ruikao.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperDTO {

    private Long id;

    @NotBlank(message = "试卷名称不能为空")
    private String paperName;

    private BigDecimal totalScore;
    private Integer duration;
    private Integer status;

    /** 嵌套校验：校验每个题目的 questionId 等字段 */
    @Valid
    private List<PaperQuestionDTO> questions;
}