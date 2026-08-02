package com.ruikao.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruikao.common.properties.WeChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class WeChatUtil {

    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final WeChatProperties weChatProperties;

    /** 带超时的 RestTemplate（连接 3s / 读 5s），避免微信接口异常导致请求线程挂死 */
    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeChatUtil(WeChatProperties weChatProperties) {
        this.weChatProperties = weChatProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 通过微信登录 code 获取 openid
     *
     * @param code 小程序 wx.login() 获取的临时 code
     * @return openid，失败返回 null（调用方决定如何提示用户）
     */
    public String getOpenid(String code) {
        try {
            String url = WX_LOGIN_URL + "?appid={appid}&secret={secret}&js_code={js_code}&grant_type={grant_type}";

            Map<String, String> params = new HashMap<>();
            params.put("appid", weChatProperties.getAppid());
            params.put("secret", weChatProperties.getSecret());
            params.put("js_code", code);
            params.put("grant_type", "authorization_code");

            // 微信返回 content-type: text/plain，先以 String 接收再手动解析
            String json = restTemplate.getForObject(url, String.class, params);

            if (json != null) {
                Map<String, Object> result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                });
                // 显式检查 errcode：code 过期/被使用/非法时会返回 errcode 而非 openid
                Object errcodeObj = result.get("errcode");
                if (errcodeObj instanceof Number && ((Number) errcodeObj).intValue() != 0) {
                    log.warn("微信登录接口返回错误，errcode: {}, errmsg: {}", errcodeObj, result.get("errmsg"));
                    return null;
                }
                String openid = (String) result.get("openid");
                if (openid == null) {
                    log.warn("微信登录接口未返回 openid");
                    return null;
                }
                // 注意：不打印 openid，属敏感信息
                return openid;
            }
            return null;
        } catch (Exception e) {
            log.error("调用微信登录接口异常", e);
            return null;
        }
    }
}