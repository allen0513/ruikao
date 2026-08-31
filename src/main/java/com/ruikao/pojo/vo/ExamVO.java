package com.ruikao.pojo.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class ExamVO {
    private Long id;
    private String examName;
    private String courseName;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String examRoom;
    /** 考试类型：0-正式考试 1-课后作业 */
    private Integer examType;
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
