package com.ruikao.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ruikao.wechat")
@Data
public class WeChatProperties {

    private String appid;
    private String secret;
}
