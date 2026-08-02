package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class RecordPageQueryDTO {
    private int page = 1;
    @JsonAlias("size")
    private int pageSize = 10;
    private Long examId;
    private Long studentId;
    private Integer status;
}
