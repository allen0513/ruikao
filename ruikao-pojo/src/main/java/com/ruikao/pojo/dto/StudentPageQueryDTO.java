package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StudentPageQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    @JsonAlias("size")
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize = 10;

    private String studentNo;
    private String name;
    private String className;

    /** 关键字（模糊匹配姓名或学号，前端 keyword 传参） */
    private String keyword;
}