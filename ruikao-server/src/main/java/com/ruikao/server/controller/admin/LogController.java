package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.entity.SysOperLog;
import com.ruikao.server.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/log")
@Slf4j
@RequiredArgsConstructor
public class LogController {

    private final SysOperLogService sysOperLogService;

    @PostMapping("/page")
    public Result<PageResult<SysOperLog>> page(@RequestBody Map<String, Object> params) {
        log.info("操作日志分页查询");
        PageResult<SysOperLog> pageResult = sysOperLogService.pageQuery(
            Integer.parseInt(params.getOrDefault("page", "1").toString()),
            Integer.parseInt(params.getOrDefault("pageSize", "10").toString()),
            (String) params.get("module"),
            (String) params.get("type"));
        return Result.success(pageResult);
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除操作日志, id: {}", id);
        sysOperLogService.delete(id);
        return Result.success();
    }
}
