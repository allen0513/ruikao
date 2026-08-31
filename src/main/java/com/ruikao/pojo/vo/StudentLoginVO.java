package com.ruikao.pojo.vo;

import lombok.Data;

@Data
public class StudentLoginVO {
    private Long id;
    private String studentNo;
    private String name;
    private String avatar;
    private String token;
}
