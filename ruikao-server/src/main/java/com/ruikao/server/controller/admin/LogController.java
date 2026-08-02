package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.SysOperLogPageQueryDTO;
import com.ruikao.pojo.entity.SysOperLog;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.service.SysOperLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/log")
@Slf4j
@RequiredArgsConstructor
public class LogController {

    private final SysOperLogService sysOperLogService;

    @PostMapping("/page")
    public Result<PageResult<SysOperLog>> page(@RequestBody @Valid SysOperLogPageQueryDTO dto) {
        log.info("操作日志分页查询");
        return Result.success(sysOperLogService.pageQuery(dto));
    }

    @OperLog(module = "日志管理", type = "删除", description = "删除操作日志:{#id}")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除操作日志, id: {}", id);
        sysOperLogService.delete(id);
        return Result.success();
    }
}
