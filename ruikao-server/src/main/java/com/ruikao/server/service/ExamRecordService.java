package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.RecordPageQueryDTO;
import com.ruikao.pojo.dto.ScoreDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.vo.ExamStartVO;
import com.ruikao.pojo.vo.RecordVO;
import com.ruikao.pojo.vo.StudentExamVO;
import com.ruikao.pojo.vo.SubmitResultVO;

import java.util.List;

public interface ExamRecordService {

    PageResult<RecordVO> pageQuery(RecordPageQueryDTO dto);

    RecordVO getDetail(Long id);

    List<ExamAnswer> getAnswers(Long recordId);

    void score(ScoreDTO dto);

    void completeMarking(Long id);

    void deleteRecord(Long id);

    List<StudentExamVO> getStudentExams(Long studentId);

    ExamStartVO startExam(Long examId, Long studentId);

    SubmitResultVO submitExam(Long recordId, Long studentId);
}
