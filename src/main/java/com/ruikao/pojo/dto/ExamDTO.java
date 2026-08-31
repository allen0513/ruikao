package com.ruikao.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ExamDTO {

    private Long id;

    @NotBlank(message = "考试名称不能为空")
    private String examName;

    /** 考试类型：0-正式考试 1-课后作业（作业无考场） */
    @Min(value = 0, message = "考试类型不合法")
    @Max(value = 1, message = "考试类型不合法")
    private Integer examType;

    private String courseName;

    // 日期/时间为 String 便于前端表单，这里用正则约束格式，避免服务层解析失败落到 500
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "考试日期格式应为 yyyy-MM-dd")
    private String examDate;

    // 时间允许 HH:mm 与 HH:mm:ss：前端 el-time-picker 提交带秒，DB TIME 列读出也带秒，LocalTime.parse 两种都支持
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "开始时间格式应为 HH:mm 或 HH:mm:ss")
    private String startTime;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "结束时间格式应为 HH:mm 或 HH:mm:ss")
    private String endTime;

    private String examRoom;

    @Positive(message = "考试时长必须大于0")
    private Integer duration;

    private Integer maxStudents;
    private Long paperId;

    @Min(value = 0, message = "考试状态不合法")
    @Max(value = 2, message = "考试状态不合法")
    private Integer status;
}