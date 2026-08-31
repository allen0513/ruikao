package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.SysOperLogPageQueryDTO;
import com.ruikao.pojo.entity.SysOperLog;

public interface SysOperLogService {

    PageResult<SysOperLog> pageQuery(SysOperLogPageQueryDTO dto);

    void delete(Long id);

    /** 写入操作日志（OperLogAspect 调用） */
    void insert(SysOperLog operLog);
}
