package com.ruikao.server.controller.admin;

import com.ruikao.common.result.Result;
import com.ruikao.pojo.entity.SysRole;
import com.ruikao.server.service.SysRoleService;
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

    @PostMapping
    public Result<String> create(@RequestBody SysRole sysRole) {
        log.info("创建角色: {}", sysRole.getRoleName());
        sysRoleService.add(sysRole);
        return Result.success();
    }

    @PutMapping
    public Result<String> update(@RequestBody SysRole sysRole) {
        log.info("更新角色, id: {}", sysRole.getId());
        sysRoleService.update(sysRole);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除角色, id: {}", id);
        sysRoleService.delete(id);
        return Result.success();
    }
}
