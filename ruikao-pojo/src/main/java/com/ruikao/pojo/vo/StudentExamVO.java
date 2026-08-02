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
    private Integer examStatus;
    private Long recordId;
    private Integer recordStatus;
    private BigDecimal score;
}
