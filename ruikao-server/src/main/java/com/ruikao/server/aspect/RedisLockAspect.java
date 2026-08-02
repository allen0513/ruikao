package com.ruikao.server.aspect;

import com.ruikao.common.exception.BusinessException;
import com.ruikao.server.annotation.RedisLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * @RedisLock 切面：SETNX 加锁，拿不到锁说明有重复请求在途，直接拒绝；
 * 释放时用 Lua 比较删除（仅持有者能删），防止锁超时被他人获取后误删他人锁
 */
@Aspect
@Component
@Slf4j
@Order(100)
public class RedisLockAspect {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /** Lua 原子释放：仅当锁 value 仍为本线程持有的随机值时删除，否则视为他人锁不动 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 模板模式：key 表达式形如 "submit:{#recordId}"，
     * {expression} 为占位符，其余为普通文本
     */
    private static final ParserContext TEMPLATE_CONTEXT = new ParserContext() {
        @Override
        public boolean isTemplate() {
            return true;
        }

        @Override
        public String getExpressionPrefix() {
            return "{";
        }

        @Override
        public String getExpressionSuffix() {
            return "}";
        }
    };

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(redisLock)")
    public Object around(ProceedingJoinPoint pjp, RedisLock redisLock) throws Throwable {
        String key = resolveKey(redisLock.key(), pjp);
        // 随机标识：释放时凭该值确认锁仍归本线程所有
        String value = UUID.randomUUID().toString();

        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(redisLock.expireSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("RedisLock 拦截重复请求: key={}", key);
            throw new BusinessException("操作过于频繁，请稍后重试");
        }

        try {
            return pjp.proceed();
        } finally {
            // 比较后删除：锁若已超时被他人获取，value 不匹配则不会误删
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
        }
    }

    /** 解析模板 key 表达式，如 "submit:{#recordId}" -> "submit:123" */
    private String resolveKey(String expression, ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = new DefaultParameterNameDiscoverer().getParameterNames(method);
        Object[] args = pjp.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return PARSER.parseExpression(expression, TEMPLATE_CONTEXT).getValue(context, String.class);
    }
}