package com.ruikao.pojo.vo;

import com.ruikao.pojo.entity.ExamAnswer;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端考试记录详情：题目不含正确答案
 */
@Data
public class StudentRecordVO {
    private Long id;
    private Long paperId;
    private String paperName;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long examId;
    private String examName;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private BigDecimal score;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private Integer status;
    /** 试卷题目列表（不含正确答案） */
    private List<StudentQuestionVO> questions;
    /** 考生答案列表 */
    private List<ExamAnswer> answers;
}