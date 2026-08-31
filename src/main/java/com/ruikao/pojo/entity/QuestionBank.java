package com.ruikao.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("question_bank")
public class QuestionBank {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer questionType;

    private String questionContent;

    private String options;

    private String answer;

    /** 答案解析 */
    private String analysis;

    private BigDecimal score;

    private Integer difficulty;

    /** 所属科目ID（subject.id） */
    private Long subjectId;

    private Long creatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
