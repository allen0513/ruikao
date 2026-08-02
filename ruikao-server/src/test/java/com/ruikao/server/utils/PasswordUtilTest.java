package com.ruikao.server.utils;

import com.ruikao.common.utils.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码工具类测试：BCrypt 加解密、$2b$ 前缀兼容、明文拒绝
 */
class PasswordUtilTest {

    /** 种子 SQL 中使用的哈希（$2b$ 前缀，jbcrypt 0.4 需规范化后校验） */
    private static final String SEED_HASH_2B = "$2b$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2";

    @Test
    void encodeAndMatches_roundTrip() {
        String hash = PasswordUtil.encode("123456");
        assertTrue(PasswordUtil.matches("123456", hash));
        assertFalse(PasswordUtil.matches("wrong-password", hash));
    }

    @Test
    void matches_2bPrefixHash_normalizedBeforeCheck() {
        // $2b$ 前缀的哈希必须能匹配（历史上 jbcrypt 0.4 会抛 Invalid salt revision 导致登录失败）
        assertTrue(PasswordUtil.matches("123456", SEED_HASH_2B));
    }

    @Test
    void matches_plaintextStoredPassword_rejected() {
        // 禁止明文兜底比较：库中若残留明文密码，一律拒绝
        assertFalse(PasswordUtil.matches("123456", "123456"));
    }

    @Test
    void matches_nullInput_rejected() {
        assertFalse(PasswordUtil.matches(null, "x"));
        assertFalse(PasswordUtil.matches("x", null));
    }
}