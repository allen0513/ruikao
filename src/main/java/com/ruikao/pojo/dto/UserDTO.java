package com.ruikao.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改入参 DTO：仅包含允许前端设置的字段，防止 mass assignment
 * 覆盖 createTime/userType 等敏感字段
 */
@Data
public class UserDTO {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 新增时必填；修改时留空表示不修改密码 */
    private String password;

    private String realName;
    private String email;
    private String phone;
    private String avatar;

    /** 用户类型：0-管理员 1-教师 */
    @Min(value = 0, message = "用户类型不合法")
    @Max(value = 1, message = "用户类型不合法")
    private Integer userType;

    /** 状态：1-启用 0-禁用 */
    @Min(value = 0, message = "状态值不合法")
    @Max(value = 1, message = "状态值不合法")
    private Integer status;

    /** 分配的角色 ID 列表（可选，新增/修改时同步写入 sys_user_role） */
    private List<Long> roleIds;
}