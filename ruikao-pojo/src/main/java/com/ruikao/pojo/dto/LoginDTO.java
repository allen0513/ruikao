package com.ruikao.pojo.dto;

import lombok.Data;

@Data
public class LoginDTO {
    private String username;
    private String password;
    private Integer userType;
}
