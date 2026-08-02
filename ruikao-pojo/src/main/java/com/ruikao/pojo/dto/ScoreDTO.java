package com.ruikao.pojo.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ScoreDTO {
    private Long recordId;
    private Long questionId;
    private Long answerId;
    private BigDecimal score;
}
