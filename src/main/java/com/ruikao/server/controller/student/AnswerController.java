package com.ruikao.server.controller.student;

import com.ruikao.common.context.BaseContext;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.server.service.ExamAnswerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/answer")
@Slf4j
public class AnswerController {

    @Autowired
    private ExamAnswerService examAnswerService;

    @PostMapping("/save")
    public Result<String> save(@RequestBody @Valid AnswerSubmitDTO answerSubmitDTO) {
        Long studentId = BaseContext.getCurrentId();
        log.info("保存学生答案: recordId={}, questionId={}, studentId={}",
                answerSubmitDTO.getRecordId(), answerSubmitDTO.getQuestionId(), studentId);
        examAnswerService.save(answerSubmitDTO, studentId);
        return Result.success();
    }

    @GetMapping("/list/{recordId}")
    public Result<List<ExamAnswer>> list(@PathVariable Long recordId) {
        Long studentId = BaseContext.getCurrentId();
        log.info("查询答题记录: recordId={}, studentId={}", recordId, studentId);
        List<ExamAnswer> answers = examAnswerService.getByRecordId(recordId, studentId);
        return Result.success(answers);
    }
}
