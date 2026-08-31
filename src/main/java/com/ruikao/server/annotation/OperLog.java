package com.ruikao.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在管理端写接口上，
 * 由 {@code OperLogAspect} 切面统一记录 module/type/description/operator/IP/URI/耗时/结果。
 * 日志写库失败不影响主流程，仅记 warn 日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 功能模块，如「考试管理」 */
    String module();

    /** 操作类型，如「新增」「修改」「删除」「发布」 */
    String type();

    /**
     * 操作描述，支持 SpEL 模板表达式（花括号内为表达式，可引用方法参数），
     * 如 "创建考试:{#examDTO.examName}"；表达式解析失败时回退为原文
     */
    String description() default "";
}