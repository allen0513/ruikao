package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.RecordPageQueryDTO;
import com.ruikao.pojo.dto.ScoreDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.vo.ExamStartVO;
import com.ruikao.pojo.vo.RecordVO;
import com.ruikao.pojo.vo.StudentExamVO;
import com.ruikao.pojo.vo.StudentRecordVO;
import com.ruikao.pojo.vo.SubmitResultVO;

import java.util.List;

public interface ExamRecordService {

    PageResult<RecordVO> pageQuery(RecordPageQueryDTO dto);

    RecordVO getDetail(Long id);

    /** 学生端查看自己的考试记录详情（含归属校验，题目不含正确答案） */
    StudentRecordVO getStudentDetail(Long recordId, Long studentId);

    List<ExamAnswer> getAnswers(Long recordId);

    void score(ScoreDTO dto);

    void completeMarking(Long id);

    /** 审核确认：3 已批改 → 4 已审核（成绩终态），写入排行榜并清统计缓存 */
    void confirmMarking(Long id);

    void deleteRecord(Long id);

    List<StudentExamVO> getStudentExams(Long studentId);

    ExamStartVO startExam(Long examId, Long studentId);

    SubmitResultVO submitExam(Long recordId, Long studentId);

    /** 系统自动收卷：考试结束后强制交卷并执行客观题自动评分（幂等，记录级锁） */
    void forceSubmitBySystem(Long recordId);
}
