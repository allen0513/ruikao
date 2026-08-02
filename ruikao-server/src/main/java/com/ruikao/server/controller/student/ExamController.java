package com.ruikao.server.controller.student;

import com.ruikao.common.context.BaseContext;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.vo.ExamStartVO;
import com.ruikao.pojo.vo.ExamVO;
import com.ruikao.pojo.vo.QuestionVO;
import com.ruikao.pojo.vo.StudentExamVO;
import com.ruikao.pojo.vo.SubmitResultVO;
import com.ruikao.server.service.ExamPaperService;
import com.ruikao.server.service.ExamRecordService;
import com.ruikao.server.service.ExamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("studentExamController")
@RequestMapping("/api/student/exam")
@Slf4j
public class ExamController {

    @Autowired
    private ExamRecordService examRecordService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamPaperService examPaperService;

    @GetMapping("/list")
    public Result<List<StudentExamVO>> list() {
        Long studentId = BaseContext.getCurrentId();
        log.info("查询学生考试列表: studentId={}", studentId);
        List<StudentExamVO> list = examRecordService.getStudentExams(studentId);
        return Result.success(list);
    }

    @GetMapping("/detail/{id}")
    public Result<ExamVO> detail(@PathVariable Long id) {
        log.info("查询考试详情: examId={}", id);
        ExamVO examVO = examService.getDetail(id);
        return Result.success(examVO);
    }

    @PostMapping("/start/{examId}")
    public Result<ExamStartVO> start(@PathVariable Long examId) {
        Long studentId = BaseContext.getCurrentId();
        log.info("学生开始考试: studentId={}, examId={}", studentId, examId);
        ExamStartVO examStart = examRecordService.startExam(examId, studentId);
        return Result.success(examStart);
    }

    @PostMapping("/submit/{recordId}")
    public Result<SubmitResultVO> submit(@PathVariable Long recordId) {
        Long studentId = BaseContext.getCurrentId();
        log.info("学生提交考试: studentId={}, recordId={}", studentId, recordId);
        SubmitResultVO result = examRecordService.submitExam(recordId, studentId);
        return Result.success(result);
    }
}
