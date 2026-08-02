package com.ruikao.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class UserPageQueryDTO {
    private int page = 1;
    @JsonAlias("size")
    private int pageSize = 10;
    private String username;
    private String realName;
    private Integer userType;
}
