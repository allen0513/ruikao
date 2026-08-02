package com.ruikao.pojo.vo;

import lombok.Data;

@Data
public class LoginVO {
    private Long id;
    private String username;
    private String realName;
    private String avatar;
    private String token;
    private Integer userType;
}
