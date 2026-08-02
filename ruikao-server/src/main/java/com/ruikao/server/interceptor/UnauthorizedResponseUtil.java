package com.ruikao.server.interceptor;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证响应工具：统一 401 返回 JSON body（{code:401, msg:...}），
 * 供各 JWT 拦截器在 preHandle 拒绝时写入，便于小程序/Web 端按 code=401 统一处理。
 */
public final class UnauthorizedResponseUtil {

    private UnauthorizedResponseUtil() {
    }

    /**
     * 写入 401 响应。仅在拦截器 preHandle 阶段调用（此时响应必然未提交，getWriter 安全）。
     */
    public static void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
    }
}
