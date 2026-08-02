package com.ruikao.pojo.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExamVO {
    private Long id;
    private String examName;
    private String courseName;
    private String examDate;
    private String startTime;
    private String endTime;
    private String examRoom;
    private Integer duration;
    private Integer maxStudents;
    private Long paperId;
    private String paperName;
    private Integer status;
    private Long creatorId;
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer registeredCount;
}
