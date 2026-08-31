package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.UserDTO;
import com.ruikao.pojo.dto.UserPageQueryDTO;
import com.ruikao.pojo.entity.SysUser;

public interface SysUserService {

    PageResult<SysUser> pageQuery(UserPageQueryDTO dto);

    void add(UserDTO dto);

    void update(UserDTO dto);

    void delete(Long id);

    SysUser getById(Long id);

    SysUser findByUsername(String username);
}
