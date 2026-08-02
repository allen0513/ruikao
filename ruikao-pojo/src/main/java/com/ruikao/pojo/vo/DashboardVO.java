package com.ruikao.pojo.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardVO {
    private long totalExams;
    private long activeExams;
    private long totalStudents;
    private long totalTeachers;
    private long pendingMark;
    private double passRate;
    private double avgScore;
    private List<Map<String, Object>> recentExams;
}