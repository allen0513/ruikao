package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.common.utils.PasswordUtil;
import com.ruikao.pojo.dto.UserDTO;
import com.ruikao.pojo.dto.UserPageQueryDTO;
import com.ruikao.pojo.entity.SysRole;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.pojo.entity.SysUserRole;
import com.ruikao.server.mapper.SysRoleMapper;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.mapper.SysUserRoleMapper;
import com.ruikao.server.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysRoleMapper sysRoleMapper;

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
        fillRoles(page.getResult());
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    @Transactional
    public void add(UserDTO dto) {
        // 新增时密码必填（校验层无法覆盖 create/update 差异，这里兜底）
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            throw new BusinessException("初始密码不能为空");
        }
        // 越权防护：仅管理员可创建管理员账号或禁用账号，防止教师 token 提权
        checkRoleAndStatusPrivileges(dto.getUserType(), dto.getStatus());

        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        // 未指定角色/状态时收口为默认值：教师 + 启用
        if (user.getUserType() == null) {
            user.setUserType(ExamConstants.USER_TYPE_TEACHER);
        }
        if (user.getStatus() == null) {
            user.setStatus(ExamConstants.USER_STATUS_ENABLED);
        }
        // 密码加密后入库，禁止明文存储
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        sysUserMapper.insert(user);
        saveUserRoles(user.getId(), dto.getRoleIds());
    }

    @Override
    @Transactional
    public void update(UserDTO dto) {
        // 越权防护：仅管理员可提升为管理员；账号状态变化同样仅管理员可操作
        Integer currentStatus = null;
        if (dto.getStatus() != null && dto.getId() != null) {
            SysUser exist = sysUserMapper.selectById(dto.getId());
            currentStatus = exist != null ? exist.getStatus() : null;
        }
        boolean statusChanged = dto.getStatus() != null && !dto.getStatus().equals(currentStatus);
        checkRoleAndStatusPrivileges(dto.getUserType(), statusChanged ? dto.getStatus() : null);

        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        // 密码字段非空时重新加密，避免明文覆盖；为空则置 null 不更新（防止空串写库）
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(PasswordUtil.encode(dto.getPassword()));
        } else {
            user.setPassword(null);
        }
        sysUserMapper.updateById(user);
        // 角色为全量替换语义：未提交（null）不动，提交（含空数组）则先删后插
        if (dto.getRoleIds() != null) {
            replaceUserRoles(dto.getId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser target = sysUserMapper.selectById(id);
        if (target == null) {
            return;
        }
        // 越权防护：教师不可删除管理员账号，防止教师 token 直接清掉管理员
        boolean isAdminCaller = Integer.valueOf(ExamConstants.USER_TYPE_ADMIN).equals(BaseContext.getCurrentUserType());
        if (!isAdminCaller && Integer.valueOf(ExamConstants.USER_TYPE_ADMIN).equals(target.getUserType())) {
            throw new BusinessException("仅管理员可删除管理员账号");
        }
        // 清理用户-角色关联，避免孤儿数据
        LambdaQueryWrapper<SysUserRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysUserRole::getUserId, id);
        sysUserRoleMapper.delete(roleWrapper);
        sysUserMapper.deleteById(id);
    }

    @Override
    public SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user != null) {
            fillRoles(Collections.singletonList(user));
        }
        return user;
    }

    @Override
    public SysUser findByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return sysUserMapper.selectOne(wrapper);
    }

    /**
     * 批量回填用户的 roleIds / roleNames（批量查询避免 N+1）
     */
    private void fillRoles(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUserRole::getUserId, userIds);
        List<SysUserRole> userRoleList = sysUserRoleMapper.selectList(wrapper);
        if (userRoleList.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> roleIdsByUser = userRoleList.stream().collect(
                Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
        List<Long> roleIds = userRoleList.stream().map(SysUserRole::getRoleId).distinct().collect(Collectors.toList());
        Map<Long, String> roleNameById = sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(r -> r.getRoleName() != null)
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        for (SysUser user : users) {
            List<Long> ids = roleIdsByUser.getOrDefault(user.getId(), Collections.emptyList());
            user.setRoleIds(ids);
            user.setRoleNames(ids.stream()
                    .map(roleNameById::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (userId == null || roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            sysUserRoleMapper.insert(userRole);
        }
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        sysUserRoleMapper.delete(wrapper);
        saveUserRoles(userId, roleIds);
    }

    /**
     * 越权防护：仅管理员可创建/提升管理员账号（userType=0）或变更账号状态（禁用/启用）。
     * 教师（或上下文缺失时）一律不能触碰这两类敏感字段，防止 mass assignment 提权。
     *
     * @param userType 请求中的目标角色，null 表示未提交该字段
     * @param status   请求中的目标状态，null 表示未提交/未变更该字段
     */
    private void checkRoleAndStatusPrivileges(Integer userType, Integer status) {
        boolean isAdminCaller = Integer.valueOf(ExamConstants.USER_TYPE_ADMIN).equals(BaseContext.getCurrentUserType());
        if (!isAdminCaller) {
            if (Integer.valueOf(ExamConstants.USER_TYPE_ADMIN).equals(userType)) {
                throw new BusinessException("仅管理员可创建或设置管理员账号");
            }
            if (status != null) {
                throw new BusinessException("仅管理员可修改账号状态");
            }
        }
    }
}
