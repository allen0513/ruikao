package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.server.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/question")
@Slf4j
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionBankService questionBankService;

    @PostMapping("/page")
    public Result<PageResult<QuestionBank>> page(@RequestBody QuestionPageQueryDTO queryDTO) {
        log.info("题目分页查询: {}", queryDTO);
        PageResult<QuestionBank> pageResult = questionBankService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @PostMapping
    public Result<String> create(@RequestBody QuestionDTO questionDTO) {
        log.info("创建题目");
        questionBankService.add(questionDTO);
        return Result.success();
    }

    @PutMapping
    public Result<String> update(@RequestBody QuestionDTO questionDTO) {
        log.info("更新题目, id: {}", questionDTO.getId());
        questionBankService.update(questionDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除题目, id: {}", id);
        questionBankService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<QuestionBank> getById(@PathVariable Long id) {
        log.info("获取题目详情, id: {}", id);
        QuestionBank questionBank = questionBankService.getDetail(id);
        return Result.success(questionBank);
    }
}
