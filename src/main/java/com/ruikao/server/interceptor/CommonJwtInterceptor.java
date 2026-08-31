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

/**
 * 公共接口 JWT 拦截器：接受管理端或学生端任一有效 token（用于 /api/common/**）
 */
@Component
@Slf4j
public class CommonJwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) return true;

        // 优先校验管理端 token
        String adminToken = request.getHeader(jwtProperties.getAdminTokenName());
        if (adminToken != null && !adminToken.isEmpty()) {
            String token = stripBearer(adminToken);
            try {
                Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
                // 已登出的 token 拒绝访问
                if (tokenBlacklistService.isBlacklisted("admin", token)) {
                    log.warn("公共JWT拦截: 管理端Token已登出 | URI: {}", request.getRequestURI());
                    UnauthorizedResponseUtil.write(response);
                    return false;
                }
                BaseContext.setCurrentId(Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString()));
                // 管理端 token 携带 userType（0-管理员 1-教师），供评论等公共接口区分用户命名空间
                BaseContext.setCurrentUserType(toInteger(claims.get(JwtClaimsConstant.USER_TYPE)));
                return true;
            } catch (Exception ignored) {
                // 管理端 token 无效，继续尝试学生端 token
            }
        }

        // 再校验学生端 token
        String studentToken = request.getHeader(jwtProperties.getStudentTokenName());
        if (studentToken != null && !studentToken.isEmpty()) {
            String token = stripBearer(studentToken);
            try {
                Claims claims = JwtUtil.parseJWT(jwtProperties.getStudentSecretKey(), token);
                // 已登出的 token 拒绝访问
                if (tokenBlacklistService.isBlacklisted("student", token)) {
                    log.warn("公共JWT拦截: 学生Token已登出 | URI: {}", request.getRequestURI());
                    UnauthorizedResponseUtil.write(response);
                    return false;
                }
                BaseContext.setCurrentId(Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString()));
                return true;
            } catch (Exception ex) {
                log.warn("公共JWT拦截: Token无效 | URI: {}", request.getRequestURI());
            }
        }

        UnauthorizedResponseUtil.write(response);
        return false;
    }

    private String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    /** JWT 声明转 Integer（jjwt 可能反序列化为 Integer 或 Long） */
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}