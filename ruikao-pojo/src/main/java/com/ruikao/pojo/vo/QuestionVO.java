package com.ruikao.pojo.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QuestionVO {
    private Long id;
    private Integer questionType;
    private String questionContent;
    private String options;
    private String answer;
    private BigDecimal score;
    private Integer difficulty;
    private Integer sortOrder;
}
