package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class QuestionDTO {

    private Long id;

    @NotNull(message = "题型不能为空")
    private Integer questionType;

    @NotBlank(message = "题目内容不能为空")
    private String questionContent;

    private String options;
    private String answer;
    private BigDecimal score;
    private Integer difficulty;
}