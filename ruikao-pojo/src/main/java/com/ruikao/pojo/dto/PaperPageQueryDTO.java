package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PaperPageQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    @JsonAlias("size")
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize = 10;

    /** 试卷名称（模糊匹配） */
    private String paperName;
    /** 状态: 0=草稿, 1=已发布 */
    private Integer status;
    /** 创建时间范围（yyyy-MM-dd） */
    private String createTimeBegin;
    private String createTimeEnd;
}