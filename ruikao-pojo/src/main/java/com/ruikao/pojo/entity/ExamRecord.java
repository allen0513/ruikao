package com.ruikao.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_record")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long studentId;

    private Long examId;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private BigDecimal score;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
