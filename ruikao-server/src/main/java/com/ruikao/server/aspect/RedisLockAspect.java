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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * @RedisLock 切面：SETNX 加锁，拿不到锁说明有重复请求在途，直接拒绝；
 * 方法执行完（finally）释放锁，防止异常时锁残留
 */
@Aspect
@Component
@Slf4j
public class RedisLockAspect {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

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

        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(redisLock.expireSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("RedisLock 拦截重复请求: key={}", key);
            throw new BusinessException("操作过于频繁，请稍后重试");
        }

        try {
            return pjp.proceed();
        } finally {
            stringRedisTemplate.delete(key);
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