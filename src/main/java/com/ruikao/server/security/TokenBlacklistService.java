package com.ruikao.server.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 登出黑名单：JWT 本身无状态，登出后将 token 写入 Redis 黑名单，
 * 剩余有效期内该 token 不可再访问受保护接口
 */
@Component
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklist:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将 token 加入黑名单（TTL 与 token 剩余有效期一致，到期自动清理）
     *
     * @param type     类型：admin / student，与签发密钥对应
     * @param token    原始 token（已去 Bearer 前缀）
     * @param ttlMillis 签发时配置的 token 有效期（毫秒）
     */
    public void blacklist(String type, String token, long ttlMillis) {
        if (token == null || token.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(KEY_PREFIX + type + ":" + token, "1", Duration.ofMillis(ttlMillis));
    }

    public boolean isBlacklisted(String type, String token) {
        return token != null && !token.isEmpty()
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + type + ":" + token));
    }
}