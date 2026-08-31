package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.AutoPaperDTO;
import com.ruikao.pojo.dto.PaperDTO;
import com.ruikao.pojo.dto.PaperPageQueryDTO;
import com.ruikao.pojo.entity.ExamPaper;
import com.ruikao.pojo.vo.QuestionVO;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.service.ExamPaperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/paper")
@Slf4j
@RequiredArgsConstructor
public class PaperController {

    private final ExamPaperService examPaperService;

    @PostMapping("/page")
    public Result<PageResult<ExamPaper>> page(@RequestBody @Valid PaperPageQueryDTO queryDTO) {
        log.info("试卷分页查询: {}", queryDTO);
        PageResult<ExamPaper> pageResult = examPaperService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @OperLog(module = "试卷管理", type = "自动组卷", description = "自动组卷:{#autoPaperDTO.paperName}")
    @PostMapping("/auto-generate")
    public Result<String> autoGenerate(@RequestBody @Valid AutoPaperDTO autoPaperDTO) {
        log.info("按难度自动随机组卷: {}", autoPaperDTO.getPaperName());
        examPaperService.autoGenerate(autoPaperDTO);
        return Result.success();
    }

    @OperLog(module = "试卷管理", type = "新增", description = "创建试卷:{#paperDTO.paperName}")
    @PostMapping
    public Result<String> create(@RequestBody @Valid PaperDTO paperDTO) {
        log.info("创建试卷: {}", paperDTO.getPaperName());
        examPaperService.add(paperDTO);
        return Result.success();
    }

    @OperLog(module = "试卷管理", type = "修改", description = "更新试卷:{#paperDTO.id}")
    @PutMapping
    public Result<String> update(@RequestBody @Valid PaperDTO paperDTO) {
        log.info("更新试卷, id: {}", paperDTO.getId());
        examPaperService.update(paperDTO);
        return Result.success();
    }

    @OperLog(module = "试卷管理", type = "删除", description = "删除试卷:{#id}")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除试卷, id: {}", id);
        examPaperService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        log.info("获取试卷详情, id: {}", id);
        ExamPaper examPaper = examPaperService.getDetail(id);
        List<QuestionVO> questions = examPaperService.getPaperQuestions(id);
        Map<String, Object> result = Map.of("paper", examPaper, "questions", questions);
        return Result.success(result);
    }

    @OperLog(module = "试卷管理", type = "发布", description = "发布试卷:{#id}")
    @PutMapping("/publish/{id}")
    public Result<String> publish(@PathVariable Long id) {
        log.info("发布试卷, id: {}", id);
        examPaperService.publish(id);
        return Result.success();
    }
}
