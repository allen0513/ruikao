package com.ruikao.server.security;

import com.ruikao.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录限流：连续失败 N 次锁定账号 15 分钟（Redis 计数），成功登录清零。
 * 注：按用户名锁定，攻击者可故意连续输错锁他人账号（轻量 DoS）；
 * 内网/教学场景可接受，如需更强防护可叠加 IP 维度与验证码。
 */
@Component
@Slf4j
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 登录前调用：账号已锁定则拒绝 */
    public void checkNotLocked(String username) {
        if (username == null) {
            return;
        }
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LOCK_KEY_PREFIX + username))) {
            throw new BusinessException("尝试次数过多，请15分钟后再试");
        }
    }

    /** 登录失败时调用：累计到阈值则锁定 */
    public void recordFailure(String username) {
        if (username == null) {
            return;
        }
        String failKey = FAIL_KEY_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            // 首次失败时设置计数过期，防止长期累计
            stringRedisTemplate.expire(failKey, LOCK_DURATION);
        }
        if (count != null && count >= MAX_FAILURES) {
            stringRedisTemplate.opsForValue().set(LOCK_KEY_PREFIX + username, "1", LOCK_DURATION);
            stringRedisTemplate.delete(failKey);
            log.warn("账号登录失败次数过多已锁定: {}", username);
        }
    }

    /** 登录成功时调用：清除失败计数与锁定状态 */
    public void recordSuccess(String username) {
        if (username == null) {
            return;
        }
        stringRedisTemplate.delete(LOCK_KEY_PREFIX + username);
        stringRedisTemplate.delete(FAIL_KEY_PREFIX + username);
    }
}