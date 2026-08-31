package com.ruikao.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    /**
     * 允许的来源（逗号分隔），用 ruikao.cors.allowed-origins 覆盖。
     * 默认 *（任意来源），便于局域网/手机/多端调试访问；
     * 生产环境务必配置为具体部署域名白名单。
     * 注意：通配符场景必须用 addAllowedOriginPattern，才能与 allowCredentials(true) 安全共存。
     */
    @Value("${ruikao.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        if ("*".equals(allowedOrigins.trim())) {
            config.addAllowedOriginPattern("*");
        } else {
            for (String origin : allowedOrigins.split(",")) {
                String o = origin.trim();
                if (!o.isEmpty()) {
                    config.addAllowedOrigin(o);
                }
            }
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}