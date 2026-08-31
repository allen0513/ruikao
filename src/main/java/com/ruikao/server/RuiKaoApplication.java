package com.ruikao.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.ruikao")
@EnableCaching
@EnableScheduling
@MapperScan("com.ruikao.server.mapper")
public class RuiKaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuiKaoApplication.class, args);
    }
}
