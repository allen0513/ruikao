package com.ruikao.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 接口文档配置：生产环境（prod profile）不加载，避免 /doc.html 泄露接口信息
 */
@Configuration
@Profile("!prod")
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("在线考试管理系统接口文档")
                        .version("2.0")
                        .description("Smart Exam, Fair Proctor."));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .packagesToScan("com.ruikao.server.controller.admin")
                .build();
    }

    @Bean
    public GroupedOpenApi studentApi() {
        return GroupedOpenApi.builder()
                .group("学生端接口")
                .packagesToScan("com.ruikao.server.controller.student")
                .build();
    }

    @Bean
    public GroupedOpenApi commonApi() {
        return GroupedOpenApi.builder()
                .group("公共接口")
                .packagesToScan("com.ruikao.server.controller.common")
                .build();
    }
}
