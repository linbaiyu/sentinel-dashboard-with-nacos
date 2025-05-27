package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sentinel.nacos")
public class NacosConfigProperties {
    //nacos服务地址
    @Value("${sentinel.nacos.serverAddr}")
    private String serverAddr;
    //nacos登录名
    @Value("${sentinel.nacos.accessKey}")
    private String username;
    //nacos登录密码
    @Value("${sentinel.nacos.secretKey}")
    private String password;
    //nacos命名空间
    @Value("${sentinel.nacos.namespace}")
    private String namespace;

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
