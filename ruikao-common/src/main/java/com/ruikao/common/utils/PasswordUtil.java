package com.ruikao.common.utils;

import org.mindrot.jbcrypt.BCrypt;
import lombok.extern.slf4j.Slf4j;

/**
 * 密码工具类
 * matches: 兼容旧版 bcrypt 哈希和明文密码
 */
@Slf4j
public class PasswordUtil {

    /**
     * 密码匹配
     * 1. 如果是 bcrypt 哈希（兼容旧数据），用 BCrypt 校验
     * 2. 如果是明文，直接 equals 比较
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        // 兼容旧版 bcrypt 哈希
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(rawPassword, encodedPassword);
            } catch (Exception e) {
                log.warn("BCrypt 校验失败: {}", e.getMessage());
                return false;
            }
        }
        // 明文对比
        return rawPassword.equals(encodedPassword);
    }
}
