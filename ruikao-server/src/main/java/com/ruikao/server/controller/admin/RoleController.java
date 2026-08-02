package com.ruikao.server.controller.admin;

import com.ruikao.common.result.Result;
import com.ruikao.pojo.entity.SysRole;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/role")
@Slf4j
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        log.info("查询所有角色");
        List<SysRole> list = sysRoleService.list();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable Long id) {
        log.info("获取角色详情, id: {}", id);
        SysRole sysRole = sysRoleService.getById(id);
        return Result.success(sysRole);
    }

    @OperLog(module = "角色管理", type = "新增", description = "创建角色:{#sysRole.roleName}")
    @PostMapping
    public Result<String> create(@RequestBody @Valid SysRole sysRole) {
        log.info("创建角色: {}", sysRole.getRoleName());
        sysRoleService.add(sysRole);
        return Result.success();
    }

    @OperLog(module = "角色管理", type = "修改", description = "更新角色:{#sysRole.roleName}")
    @PutMapping
    public Result<String> update(@RequestBody @Valid SysRole sysRole) {
        log.info("更新角色, id: {}", sysRole.getId());
        sysRoleService.update(sysRole);
        return Result.success();
    }

    @OperLog(module = "角色管理", type = "删除", description = "删除角色:{#id}")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除角色, id: {}", id);
        sysRoleService.delete(id);
        return Result.success();
    }
}
