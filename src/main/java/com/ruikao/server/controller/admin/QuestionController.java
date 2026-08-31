package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.pojo.vo.QuestionImportResultVO;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.annotation.RedisLock;
import com.ruikao.server.service.QuestionBankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/question")
@Slf4j
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionBankService questionBankService;

    @PostMapping("/page")
    public Result<PageResult<QuestionBank>> page(@RequestBody @Valid QuestionPageQueryDTO queryDTO) {
        log.info("题目分页查询: {}", queryDTO);
        PageResult<QuestionBank> pageResult = questionBankService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @OperLog(module = "题库管理", type = "新增", description = "创建题目:{#questionDTO.questionContent}")
    @PostMapping
    public Result<String> create(@RequestBody @Valid QuestionDTO questionDTO) {
        log.info("创建题目");
        questionBankService.add(questionDTO);
        return Result.success();
    }

    @OperLog(module = "题库管理", type = "修改", description = "更新题目:{#questionDTO.id}")
    @PutMapping
    public Result<String> update(@RequestBody @Valid QuestionDTO questionDTO) {
        log.info("更新题目, id: {}", questionDTO.getId());
        questionBankService.update(questionDTO);
        return Result.success();
    }

    @OperLog(module = "题库管理", type = "删除", description = "删除题目:{#id}")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除题目, id: {}", id);
        questionBankService.delete(id);
        return Result.success();
    }

    @OperLog(module = "题库管理", type = "导入", description = "Excel 批量导入题库:{#file.originalFilename}")
    @RedisLock(key = "'question-import'", expireSeconds = 300)
    @PostMapping("/import")
    public Result<QuestionImportResultVO> importExcel(@RequestParam("file") MultipartFile file) {
        log.info("Excel 批量导入题库: {}", file.getOriginalFilename());
        QuestionImportResultVO result = questionBankService.importExcel(file);
        log.info("Excel 导入完成: 成功{}条, 失败{}条", result.getSuccessCount(), result.getFailCount());
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        log.info("获取题目详情, id: {}", id);
        QuestionBank questionBank = questionBankService.getDetail(id);
        List<Long> knowledgePointIds = questionBank != null
                ? questionBankService.getKnowledgePointIds(id)
                : List.of();
        return Result.success(Map.of("question", questionBank, "knowledgePointIds", knowledgePointIds));
    }
}
