package com.ruikao.pojo.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class StudentExamVO {
    private Long examId;
    private String examName;
    private String courseName;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer duration;
    /** 考试类型：0-正式考试 1-课后作业 */
    private Integer examType;
    private Integer examStatus;
    private Long recordId;
    private Integer recordStatus;
    private BigDecimal score;
}
