package com.ruikao.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxLoginVO {
    /** 是否已绑定学生账号 */
    private Boolean bound;

    /** 学生ID（绑定后才有） */
    private Long id;

    /** 学号（绑定后才有） */
    private String studentNo;

    /** 姓名（绑定后才有） */
    private String name;

    /** 头像 URL（绑定后才有） */
    private String avatar;

    /** JWT token（绑定后才有） */
    private String token;
}
