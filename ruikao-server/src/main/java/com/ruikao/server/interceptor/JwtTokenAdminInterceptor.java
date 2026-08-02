package com.ruikao.server.interceptor;

import com.ruikao.common.constant.JwtClaimsConstant;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.properties.JwtProperties;
import com.ruikao.common.utils.JwtUtil;
import com.ruikao.server.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) return true;

        String token = request.getHeader(jwtProperties.getAdminTokenName());
        if (token == null || token.isEmpty()) {
            log.warn("管理端JWT拦截: 缺少Token | URI: {}", request.getRequestURI());
            UnauthorizedResponseUtil.write(response);
            return false;
        }

        // 去除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            // 已登出（黑名单）的 token 拒绝访问
            if (tokenBlacklistService.isBlacklisted("admin", token)) {
                log.warn("管理端JWT拦截: Token已登出 | URI: {}", request.getRequestURI());
                UnauthorizedResponseUtil.write(response);
                return false;
            }
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            BaseContext.setCurrentId(userId);
            // 用户类型写入上下文，供服务层做角色级校验（如仅管理员可创建管理员账号）
            Object userType = claims.get(JwtClaimsConstant.USER_TYPE);
            BaseContext.setCurrentUserType(userType != null ? Integer.valueOf(userType.toString()) : null);
            // 用户名写入上下文，供操作日志切面记录操作人
            Object username = claims.get(JwtClaimsConstant.USERNAME);
            BaseContext.setCurrentUsername(username != null ? username.toString() : null);
            log.debug("管理端JWT: userId={} 放行", userId);
            return true;
        } catch (Exception ex) {
            log.warn("管理端JWT拦截: Token无效 | URI: {}", request.getRequestURI());
            UnauthorizedResponseUtil.write(response);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}
