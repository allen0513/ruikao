package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxBindDTO {

    @NotBlank(message = "微信code不能为空")
    private String code;

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "密码不能为空")
    private String password;
}