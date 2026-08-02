package com.ruikao.pojo.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StudentExamVO {
    private Long examId;
    private String examName;
    private String courseName;
    private String examDate;
    private String startTime;
    private String endTime;
    private Integer duration;
    private Integer examStatus;
    private Long recordId;
    private Integer recordStatus;
    private BigDecimal score;
}
