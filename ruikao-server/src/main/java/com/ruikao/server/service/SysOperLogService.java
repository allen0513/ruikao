package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.entity.SysOperLog;

public interface SysOperLogService {

    PageResult<SysOperLog> pageQuery(int page, int pageSize, String module, String type);

    void delete(Long id);
}
