package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学生新增/修改入参 DTO：仅包含允许前端设置的字段，防止 mass assignment
 */
@Data
public class StudentDTO {

    private Long id;

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    /** 新增时必填；修改时留空表示不修改密码 */
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String gender;
    private String major;
    private String grade;
    private String className;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
}