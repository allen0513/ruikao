package com.ruikao.pojo.dto;

import lombok.Data;

@Data
public class ExamDTO {
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
    private Integer status;
}
