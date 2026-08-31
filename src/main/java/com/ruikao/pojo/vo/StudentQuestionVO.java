package com.ruikao.pojo.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 学生端题目视图：不含正确答案，防止开考/查分时泄露答案
 */
@Data
public class StudentQuestionVO {
    private Long id;
    private Integer questionType;
    private String questionContent;
    private String options;
    private BigDecimal score;
    private Integer difficulty;
    private Integer sortOrder;
}