package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class QuestionPageQueryDTO {
    private int page = 1;
    @JsonAlias("size")
    private int pageSize = 10;
    private Integer questionType;
    private Integer difficulty;
    private String questionContent;
}
