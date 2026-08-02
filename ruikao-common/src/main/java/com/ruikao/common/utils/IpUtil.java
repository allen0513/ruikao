package com.ruikao.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取工具：优先代理头（X-Forwarded-For / X-Real-IP），最后回退 getRemoteAddr
 */
public class IpUtil {

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能携带多级代理，取第一个
        int comma = ip.indexOf(',');
        return comma > 0 ? ip.substring(0, comma).trim() : ip;
    }
}