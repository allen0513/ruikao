package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.RecordPageQueryDTO;
import com.ruikao.pojo.dto.ScoreDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.vo.RecordVO;
import com.ruikao.server.service.ExamRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("adminRecordController")
@RequestMapping("/api/admin/record")
@Slf4j
@RequiredArgsConstructor
public class RecordController {

    private final ExamRecordService examRecordService;

    @PostMapping("/page")
    public Result<PageResult<RecordVO>> page(@RequestBody RecordPageQueryDTO queryDTO) {
        log.info("考试记录分页查询: {}", queryDTO);
        PageResult<RecordVO> pageResult = examRecordService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<RecordVO> getById(@PathVariable Long id) {
        log.info("获取考试记录详情, id: {}", id);
        RecordVO recordVO = examRecordService.getDetail(id);
        return Result.success(recordVO);
    }

    @GetMapping("/answers/{recordId}")
    public Result<List<ExamAnswer>> getAnswers(@PathVariable Long recordId) {
        log.info("获取学生答卷答案, recordId: {}", recordId);
        List<ExamAnswer> answers = examRecordService.getAnswers(recordId);
        return Result.success(answers);
    }

    @PostMapping("/score")
    public Result<String> score(@RequestBody ScoreDTO scoreDTO) {
        log.info("评分: {}", scoreDTO);
        examRecordService.score(scoreDTO);
        return Result.success();
    }

    @PostMapping("/complete/{id}")
    public Result<String> completeMarking(@PathVariable Long id) {
        log.info("完成阅卷, recordId: {}", id);
        examRecordService.completeMarking(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除考试记录, id: {}", id);
        examRecordService.deleteRecord(id);
        return Result.success();
    }
}
