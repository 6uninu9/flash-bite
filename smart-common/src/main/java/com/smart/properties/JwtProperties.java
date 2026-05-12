package com.smart.properties;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.Key;

@Component
@ConfigurationProperties(prefix = "smart.jwt")
@Data
public class JwtProperties {

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminSecretKeyOrigin;
    private long adminTtl;
    private String adminTokenName;
    private Key adminSecretKey;

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userSecretKeyOrigin;
    private long userTtl;
    private String userTokenName;
    private Key userSecretKey;

    @PostConstruct
    public void init() {
        // 将 Base64 编码的字符串转换为 Key
        this.adminSecretKey = Keys.hmacShaKeyFor(adminSecretKeyOrigin.getBytes());
        this.userSecretKey = Keys.hmacShaKeyFor(userSecretKeyOrigin.getBytes());
    }
}
