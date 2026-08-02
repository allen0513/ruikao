package com.ruikao.server.aspect;

import com.ruikao.common.context.BaseContext;
import com.ruikao.common.utils.IpUtil;
import com.ruikao.pojo.entity.SysOperLog;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.service.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * @OperLog 切面：环绕记录操作日志（module/type/description/operator/IP/URI/耗时/结果）。
 * 写库失败仅记 warn，不影响主流程；失败时结果记为 error，异常详情落服务日志。
 */
@Aspect
@Component
@Slf4j
public class OperLogAspect {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_ERROR = "error";
    /** 与 sys_oper_log 表列长对齐，超长截断防止 Data too long */
    private static final int MAX_TEXT_LENGTH = 500;

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /** SpEL 模板：{expr} 为表达式占位符，与 RedisLockAspect 保持一致 */
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
    private SysOperLogService sysOperLogService;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        String result = RESULT_SUCCESS;
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            result = RESULT_ERROR;
            log.warn("操作失败 | {} | {} | 原因: {}", operLog.module(), operLog.type(), ex.getMessage());
            throw ex;
        } finally {
            long costTime = System.currentTimeMillis() - start;
            try {
                saveLog(pjp, operLog, result, costTime);
            } catch (Exception ex) {
                // 日志写库失败不影响主流程
                log.warn("操作日志写库失败: {}", ex.getMessage());
            }
        }
    }

    private void saveLog(ProceedingJoinPoint pjp, OperLog operLog, String result, long costTime) {
        SysOperLog operLogEntity = new SysOperLog();
        operLogEntity.setModule(truncate(operLog.module(), MAX_TEXT_LENGTH));
        operLogEntity.setType(truncate(operLog.type(), MAX_TEXT_LENGTH));
        operLogEntity.setDescription(truncate(resolveDescription(operLog.description(), pjp), MAX_TEXT_LENGTH));
        operLogEntity.setOperator(BaseContext.getCurrentUsername());
        operLogEntity.setResult(result);
        operLogEntity.setCostTime(costTime);

        // 经 RequestContextHolder 取当前请求；AOP 内部调用时可能无请求上下文
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            operLogEntity.setOperIp(IpUtil.getClientIp(request));
            operLogEntity.setOperUri(truncate(request.getRequestURI(), MAX_TEXT_LENGTH));
        }
        sysOperLogService.insert(operLogEntity);
    }

    /** 解析描述模板，如 "创建考试:{#examDTO.examName}"；解析失败回退原文 */
    private String resolveDescription(String description, ProceedingJoinPoint pjp) {
        if (description == null || description.isEmpty() || !description.contains("{")) {
            return description;
        }
        try {
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = new DefaultParameterNameDiscoverer().getParameterNames(method);
            Object[] args = pjp.getArgs();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            return PARSER.parseExpression(description, TEMPLATE_CONTEXT).getValue(context, String.class);
        } catch (Exception ex) {
            log.debug("操作日志描述 SpEL 解析失败，回退原文: {}", description);
            return description;
        }
    }

    private String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}