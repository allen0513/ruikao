package com.ruikao.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩排行榜条目
 */
@Data
public class RankVO {
    /** 名次（从 1 开始） */
    private Integer rank;
    private String studentName;
    private String studentNo;
    private BigDecimal score;
}