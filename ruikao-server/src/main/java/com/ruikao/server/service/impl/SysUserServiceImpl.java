package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.UserPageQueryDTO;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public PageResult<SysUser> pageQuery(UserPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            wrapper.like(SysUser::getUsername, dto.getUsername());
        }
        if (dto.getRealName() != null && !dto.getRealName().isEmpty()) {
            wrapper.like(SysUser::getRealName, dto.getRealName());
        }
        if (dto.getUserType() != null) {
            wrapper.eq(SysUser::getUserType, dto.getUserType());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        List<SysUser> list = sysUserMapper.selectList(wrapper);
        Page<SysUser> page = (Page<SysUser>) list;
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    public void add(SysUser user) {
        sysUserMapper.insert(user);
    }

    @Override
    public void update(SysUser user) {
        sysUserMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        sysUserMapper.deleteById(id);
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    public SysUser findByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return sysUserMapper.selectOne(wrapper);
    }
}
