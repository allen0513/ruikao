package com.ruikao.server.config;

import com.ruikao.server.interceptor.JwtTokenAdminInterceptor;
import com.ruikao.server.interceptor.JwtTokenStudentInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    @Autowired
    private JwtTokenStudentInterceptor jwtTokenStudentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("注册 JWT 双拦截器");

        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");

        registry.addInterceptor(jwtTokenStudentInterceptor)
                .addPathPatterns("/api/student/**")
                .excludePathPatterns(
                        "/api/student/auth/login",
                        "/api/student/auth/wx-login",
                        "/api/student/auth/bind-wx"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
