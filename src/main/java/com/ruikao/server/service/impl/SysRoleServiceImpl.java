package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.pojo.entity.SysRole;
import com.ruikao.pojo.entity.SysUserRole;
import com.ruikao.server.mapper.SysRoleMapper;
import com.ruikao.server.mapper.SysUserRoleMapper;
import com.ruikao.server.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Override
    public List<SysRole> list() {
        return sysRoleMapper.selectList(null);
    }

    @Override
    public SysRole getById(Long id) {
        return sysRoleMapper.selectById(id);
    }

    @Override
    public void add(SysRole role) {
        checkRoleCodeUnique(role.getRoleCode(), null);
        sysRoleMapper.insert(role);
    }

    @Override
    public void update(SysRole role) {
        checkRoleCodeUnique(role.getRoleCode(), role.getId());
        sysRoleMapper.updateById(role);
    }

    /** roleCode 唯一校验（更新时排除自身），避免依赖唯一键的 409 兜底 */
    private void checkRoleCodeUnique(String roleCode, Long excludeId) {
        if (roleCode == null || roleCode.isEmpty()) {
            return; // 空值由实体 @NotBlank 校验拦截
        }
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleCode, roleCode);
        if (excludeId != null) {
            wrapper.ne(SysRole::getId, excludeId);
        }
        if (sysRoleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("角色编码已存在: " + roleCode);
        }
    }

    @Override
    public void delete(Long id) {
        // 清理用户-角色关联，避免孤儿数据
        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getRoleId, id);
        sysUserRoleMapper.delete(userRoleWrapper);
        sysRoleMapper.deleteById(id);
    }
}
