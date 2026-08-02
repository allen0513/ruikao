package com.ruikao.server.controller.student;

import com.ruikao.common.context.BaseContext;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.vo.RecordVO;
import com.ruikao.pojo.vo.StudentExamVO;
import com.ruikao.server.service.ExamRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("studentRecordController")
@RequestMapping("/api/student/record")
@Slf4j
public class RecordController {

    @Autowired
    private ExamRecordService examRecordService;

    @GetMapping("/list")
    public Result<List<StudentExamVO>> list() {
        Long studentId = BaseContext.getCurrentId();
        log.info("查询学生考试记录列表: studentId={}", studentId);
        List<StudentExamVO> records = examRecordService.getStudentExams(studentId);
        return Result.success(records);
    }

    @GetMapping("/{id}")
    public Result<RecordVO> getById(@PathVariable Long id) {
        log.info("查询考试记录详情: recordId={}", id);
        RecordVO recordVO = examRecordService.getDetail(id);
        return Result.success(recordVO);
    }
}
