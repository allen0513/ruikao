package com.ruikao.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("睿考平台接口文档")
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
