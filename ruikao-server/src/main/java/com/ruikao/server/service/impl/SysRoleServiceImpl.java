package com.ruikao.server.service.impl;

import com.ruikao.pojo.entity.SysRole;
import com.ruikao.server.mapper.SysRoleMapper;
import com.ruikao.server.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Autowired
    private SysRoleMapper sysRoleMapper;

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
        sysRoleMapper.insert(role);
    }

    @Override
    public void update(SysRole role) {
        sysRoleMapper.updateById(role);
    }

    @Override
    public void delete(Long id) {
        sysRoleMapper.deleteById(id);
    }
}
