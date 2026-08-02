package com.ruikao.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruikao.common.properties.WeChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class WeChatUtil {

    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 通过微信登录 code 获取 openid
     * @param code 小程序 wx.login() 获取的临时 code
     * @return openid，如果失败返回 null
     */
    public String getOpenid(String code) {
        try {
            String url = WX_LOGIN_URL + "?appid={appid}&secret={secret}&js_code={js_code}&grant_type={grant_type}";

            Map<String, String> params = new HashMap<>();
            params.put("appid", weChatProperties.getAppid());
            params.put("secret", weChatProperties.getSecret());
            params.put("js_code", code);
            params.put("grant_type", "authorization_code");

            log.info("调用微信登录接口，appid: {}", weChatProperties.getAppid());

            // 微信返回 content-type: text/plain，先以 String 接收再手动解析
            String json = restTemplate.getForObject(url, String.class, params);

            if (json != null) {
                Map<String, Object> result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                String openid = (String) result.get("openid");
                if (openid != null) {
                    log.info("微信登录成功，openid: {}", openid);
                    return openid;
                } else {
                    log.error("微信登录失败，返回: {}", json);
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("调用微信登录接口异常", e);
            return null;
        }
    }
}
