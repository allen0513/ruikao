package com.ruikao.pojo.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QuestionDTO {
    private Long id;
    private Integer questionType;
    private String questionContent;
    private String options;
    private String answer;
    private BigDecimal score;
    private Integer difficulty;
}
