package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class PaperPageQueryDTO {
    private int page = 1;
    @JsonAlias("size")
    private int pageSize = 10;
    /** 试卷名称（模糊匹配） */
    private String paperName;
    /** 状态: 0=草稿, 1=已发布 */
    private Integer status;
    /** 创建时间范围（yyyy-MM-dd） */
    private String createTimeBegin;
    private String createTimeEnd;
}
