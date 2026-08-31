package com.ruikao.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * 密码工具类（仅支持 BCrypt 哈希存储，不接收明文）
 */
@Slf4j
public class PasswordUtil {

    /**
     * 密码加密（BCrypt，自动生成盐）
     */
    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 密码匹配
     * spring-security-crypto 的 BCrypt 原生兼容 $2a$/$2b$/$2y$ 前缀（含种子数据中的 $2b$ 哈希）。
     * 非 BCrypt 格式一律拒绝匹配，不再支持明文密码。
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String hash = encodedPassword;
        if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(rawPassword, hash);
            } catch (Exception e) {
                log.warn("BCrypt 校验失败: {}", e.getMessage());
                return false;
            }
        }
        log.warn("密码非 BCrypt 格式，拒绝匹配");
        return false;
    }
}
