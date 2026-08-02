package com.ruikao.pojo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ScoreDTO {

    private Long recordId;
    private Long questionId;
    private Long answerId;

    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0", message = "分数不能为负数")
    private BigDecimal score;
}