package com.ruikao.server.service;

import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.mapper.SysUserRoleMapper;
import com.ruikao.server.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户删除越权校验测试：教师不可删除管理员账号，管理员可删
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @InjectMocks
    private SysUserServiceImpl sysUserServiceImpl;

    @AfterEach
    void clearContext() {
        BaseContext.remove();
    }

    @Test
    void delete_teacherCaller_deletingAdmin_throws() {
        SysUser target = new SysUser();
        target.setId(1L);
        target.setUserType(ExamConstants.USER_TYPE_ADMIN);
        when(sysUserMapper.selectById(1L)).thenReturn(target);
        BaseContext.setCurrentUserType(ExamConstants.USER_TYPE_TEACHER);

        assertThrows(BusinessException.class, () -> sysUserServiceImpl.delete(1L));
        verify(sysUserMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_adminCaller_deletesAdmin() {
        SysUser target = new SysUser();
        target.setId(1L);
        target.setUserType(ExamConstants.USER_TYPE_ADMIN);
        when(sysUserMapper.selectById(1L)).thenReturn(target);
        BaseContext.setCurrentUserType(ExamConstants.USER_TYPE_ADMIN);

        sysUserServiceImpl.delete(1L);

        verify(sysUserMapper).deleteById(1L);
    }

    @Test
    void delete_teacherCaller_deletesTeacher() {
        SysUser target = new SysUser();
        target.setId(1L);
        target.setUserType(ExamConstants.USER_TYPE_TEACHER);
        when(sysUserMapper.selectById(1L)).thenReturn(target);
        BaseContext.setCurrentUserType(ExamConstants.USER_TYPE_TEACHER);

        sysUserServiceImpl.delete(1L);

        verify(sysUserMapper).deleteById(1L);
    }

    @Test
    void delete_missingTarget_noop() {
        when(sysUserMapper.selectById(1L)).thenReturn(null);

        sysUserServiceImpl.delete(1L);

        verify(sysUserMapper, never()).deleteById(anyLong());
    }
}