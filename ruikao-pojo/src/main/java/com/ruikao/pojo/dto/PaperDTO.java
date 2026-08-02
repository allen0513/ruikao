package com.ruikao.pojo.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperDTO {
    private Long id;
    private String paperName;
    private BigDecimal totalScore;
    private Integer duration;
    private Integer status;
    private List<PaperQuestionDTO> questions;
}
