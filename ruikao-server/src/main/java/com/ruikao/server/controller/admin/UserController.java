package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.UserPageQueryDTO;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.server.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;

    @PostMapping("/page")
    public Result<PageResult<SysUser>> page(@RequestBody UserPageQueryDTO queryDTO) {
        log.info("用户分页查询: {}", queryDTO);
        PageResult<SysUser> pageResult = sysUserService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @PostMapping
    public Result<String> create(@RequestBody SysUser sysUser) {
        log.info("创建用户: {}", sysUser.getUsername());
        sysUserService.add(sysUser);
        return Result.success();
    }

    @PutMapping
    public Result<String> update(@RequestBody SysUser sysUser) {
        log.info("更新用户, id: {}", sysUser.getId());
        sysUserService.update(sysUser);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除用户, id: {}", id);
        sysUserService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        log.info("获取用户详情, id: {}", id);
        SysUser sysUser = sysUserService.getById(id);
        return Result.success(sysUser);
    }
}
