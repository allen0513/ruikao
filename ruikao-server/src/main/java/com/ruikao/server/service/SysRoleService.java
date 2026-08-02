package com.ruikao.server.service;

import com.ruikao.pojo.entity.SysRole;

import java.util.List;

public interface SysRoleService {

    List<SysRole> list();

    SysRole getById(Long id);

    void add(SysRole role);

    void update(SysRole role);

    void delete(Long id);
}
