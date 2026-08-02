package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ExamPageQueryDTO {
    private int page = 1;
    @JsonAlias("size")
    private int pageSize = 10;
    private String examName;
    private Integer status;
    private String examDate;
}
