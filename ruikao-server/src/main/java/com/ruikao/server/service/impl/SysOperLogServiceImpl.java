package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.SysOperLogPageQueryDTO;
import com.ruikao.pojo.entity.SysOperLog;
import com.ruikao.server.mapper.SysOperLogMapper;
import com.ruikao.server.service.SysOperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class SysOperLogServiceImpl implements SysOperLogService {

    @Autowired
    private SysOperLogMapper sysOperLogMapper;

    @Override
    public PageResult<SysOperLog> pageQuery(SysOperLogPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (dto.getModule() != null && !dto.getModule().isEmpty()) {
            wrapper.eq(SysOperLog::getModule, dto.getModule());
        }
        if (dto.getType() != null && !dto.getType().isEmpty()) {
            wrapper.eq(SysOperLog::getType, dto.getType());
        }
        if (dto.getOperator() != null && !dto.getOperator().isEmpty()) {
            wrapper.like(SysOperLog::getOperator, dto.getOperator());
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            wrapper.ge(SysOperLog::getCreateTime, LocalDate.parse(dto.getStartTime()).atStartOfDay());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            // 结束日期含当天整天（23:59:59.999999999）
            wrapper.le(SysOperLog::getCreateTime, LocalDate.parse(dto.getEndTime()).atTime(LocalTime.MAX));
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

    @Override
    public void insert(SysOperLog operLog) {
        sysOperLogMapper.insert(operLog);
    }
}
