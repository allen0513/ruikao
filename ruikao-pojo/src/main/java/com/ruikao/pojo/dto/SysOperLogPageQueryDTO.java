package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SysOperLogPageQueryDTO {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    private Integer page;

    @JsonAlias("size")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于0")
    private Integer pageSize;

    /** 操作模块（可选） */
    private String module;

    /** 操作类型（可选） */
    private String type;

    /** 操作人用户名（模糊匹配，可选） */
    private String operator;

    /** 开始日期（yyyy-MM-dd，可选） */
    private String startTime;

    /** 结束日期（yyyy-MM-dd，含当天，可选） */
    private String endTime;
}