package com.ruikao.server.security;

import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.utils.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 按 IP 的固定窗口限流（Redis 计数）：
 * 用于微信登录/绑定等无账号维度可限流的匿名接口，防止脚本刷微信 code 兑换。
 * 与 LoginAttemptService（按账号锁定）互补。
 */
@Component
@Slf4j
public class IpRateLimitService {

    /** 每 IP 每分钟最大请求数 */
    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "rate:ip:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 固定窗口计数：超过阈值抛业务异常。
     *
     * @param key     限流维度标识，如 "wx-login"、"bind-wx"
     * @param request 当前请求（取客户端 IP）
     */
    public void check(String key, HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        String redisKey = KEY_PREFIX + key + ":" + ip;
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            // 首次计数时设置窗口过期，防止长期累计
            stringRedisTemplate.expire(redisKey, WINDOW);
        }
        if (count != null && count > MAX_REQUESTS_PER_WINDOW) {
            log.warn("IP 请求过于频繁已拦截: key={}, ip={}, count={}", key, ip, count);
            throw new BusinessException("操作过于频繁，请稍后重试");
        }
    }
}