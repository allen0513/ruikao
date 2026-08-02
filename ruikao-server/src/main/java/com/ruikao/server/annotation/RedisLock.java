package com.ruikao.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基于 Redis SETNX 的防重复提交锁注解
 * key 支持 SpEL 表达式，如 "submit:{#recordId}"
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisLock {

    /** 锁 key（SpEL 表达式） */
    String key();

    /** 锁自动过期时间（秒），防止异常时锁永久占用 */
    long expireSeconds() default 30;
}