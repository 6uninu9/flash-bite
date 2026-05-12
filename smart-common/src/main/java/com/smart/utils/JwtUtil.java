package com.smart.utils;

import com.smart.constant.JwtConstant;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JwtUtil {

    /**
     * 生成 JWT token（指定有效期）
     *
     * @param claims      要放入 payload 的声明
     * @param ttlMillis   token 有效时间（毫秒）
     * @return JWT 字符串
     * @throws JwtException 如果生成失败
     */
    public static String createJWT(Key secretKey, long ttlMillis, Map<String, Object> claims) {
        try {
            // 使用Java随机生成 Base64 编码的密钥
            //String base64Key = java.util.Base64.getEncoder().encodeToString(secretKey.getEncoded());

            // 指定签名的时候使用的签名算法，也就是header那部分
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

            // 生成JWT的时间
            Date now = new Date();
            Date expiration = new Date(now.getTime() + ttlMillis);

            // 创建payload（有效载荷）的私有声明（根据特定的业务需要添加）
            return Jwts.builder()
                    .setClaims(claims)
                    .setIssuedAt(now) //设置生成JWT的时间
                    .setExpiration(expiration) //设置过期时间
                    .signWith(secretKey, signatureAlgorithm) //获取数字签名
                    .compact(); //将配置好的 JWT 信息组合并编码成一个完整的字符串
        } catch (Exception e) {
            log.error("JWT 生成失败: {}", e.getMessage());
            throw new JwtException(JwtConstant.TOKEN_GENERATION_FAILED);
        }
    }

    /**
     * 解析 JWT token，返回 Claims
     *
     * @param token JWT 字符串
     * @return Claims 对象
     * @throws JwtException 如果 token 无效（过期、签名错误、格式错误等）
     */
    public static Claims parseJWT(Key secretKey, String token) {
        try {
            return Jwts.parserBuilder() //构建token解析器
                    .setSigningKey(secretKey) //把签名密钥设置到解析器中
                    .build() //构建出一个 JwtParser 对象，这个对象可用于解析 JWT 令牌
                    .parseClaimsJws(token) //对传入的 JWT 令牌进行解析，返回一个 Jws<Claims> 对象，此对象包含了 JWT 的头部、负载和签名信息
                    .getBody(); //获取 JWT 的负载信息
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
            throw new JwtException(JwtConstant.TOKEN_EXPIRED);
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 JWT 格式: {}", e.getMessage());
            throw new JwtException(JwtConstant.TOKEN_UNSUPPORTED);
        } catch (MalformedJwtException e) {
            log.warn("JWT 格式错误: {}", e.getMessage());
            throw new JwtException(JwtConstant.TOKEN_MALFORMED);
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("JWT 签名验证失败: {}", e.getMessage());
            throw new JwtException(JwtConstant.TOKEN_SIGNATURE_FAILED);
        } catch (Exception e) {
            log.warn("JWT 解析异常: {}", e.getMessage());
            throw new JwtException(JwtConstant.TOKEN_PARSING_FAILED);
        }
    }

}
