package com.ruikao.common.properties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix = "ruikao.jwt")
@Data
public class JwtProperties {
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;
    private String studentSecretKey;
    private long studentTtl;
    private String studentTokenName;
}
