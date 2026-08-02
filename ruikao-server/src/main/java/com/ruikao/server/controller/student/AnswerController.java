package com.ruikao.server.controller.student;

import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.server.service.ExamAnswerService;
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
    public Result<String> save(@RequestBody AnswerSubmitDTO answerSubmitDTO) {
        log.info("保存学生答案: recordId={}, questionId={}",
                answerSubmitDTO.getRecordId(), answerSubmitDTO.getQuestionId());
        examAnswerService.save(answerSubmitDTO);
        return Result.success();
    }

    @GetMapping("/list/{recordId}")
    public Result<List<ExamAnswer>> list(@PathVariable Long recordId) {
        log.info("查询答题记录: recordId={}", recordId);
        List<ExamAnswer> answers = examAnswerService.getByRecordId(recordId);
        return Result.success(answers);
    }
}
