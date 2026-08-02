package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.entity.SysOperLog;
import com.ruikao.server.mapper.SysOperLogMapper;
import com.ruikao.server.service.SysOperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysOperLogServiceImpl implements SysOperLogService {

    @Autowired
    private SysOperLogMapper sysOperLogMapper;

    @Override
    public PageResult<SysOperLog> pageQuery(int page, int pageSize, String module, String type) {
        PageHelper.startPage(page, pageSize);
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            wrapper.eq(SysOperLog::getModule, module);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(SysOperLog::getType, type);
        }
        wrapper.orderByDesc(SysOperLog::getCreateTime);
        List<SysOperLog> list = sysOperLogMapper.selectList(wrapper);
        Page<SysOperLog> sysOperLogPage = (Page<SysOperLog>) list;
        return PageResult.of(sysOperLogPage.getTotal(), sysOperLogPage.getResult());
    }

    @Override
    public void delete(Long id) {
        sysOperLogMapper.deleteById(id);
    }
}
