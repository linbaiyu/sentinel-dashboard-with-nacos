package com.alibaba.csp.sentinel.dashboard.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 更多用户配置
 *
 * @author libeiyao
 * @version 1.0.0
 * @menu 更多用户配置
 * @date 2024/06/20
 */
@Data
@Configuration
@PropertySource("classpath:application-${spring.profiles.active}.properties")
@ConfigurationProperties(prefix = "auth.more")
public class MoreUserConfig {

//    private List<String> list = new ArrayList<>();

    private Map<String, String> map = new HashMap<>();

}
