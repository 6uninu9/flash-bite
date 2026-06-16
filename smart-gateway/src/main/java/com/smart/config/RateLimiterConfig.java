package com.smart.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * 限流维度配置类
 * 定义基于不同维度的 KeyResolver，配合 yml 中的 RequestRateLimiter 使用
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    @Value("${spring.cloud.gateway.rate-limit.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    /**
     * 多端自适应限流 Key 解析器（安全增强版）
     * <p>
     * 依赖前提：
     * 1. AuthGlobalFilter (order = -1) 先于当前限流器执行，已完成 Token 校验。
     * 2. 鉴权过滤器已移除客户端伪造的 X-User-Id / X-Admin-Id，
     *    并注入了网关签发的真实用户/管理员 ID。
     * <p>
     * 因此，此处从请求头获取的 ID 是可信的，无需担心伪造。
     */
    @Bean
    public KeyResolver authKeyResolver() {
        return exchange -> {
            // 1. 优先使用商家管理端 ID（鉴权过滤器在成功后会注入 X-Admin-Id）
            String adminId = exchange.getRequest().getHeaders().getFirst("X-Admin-Id");
            if (adminId != null && !adminId.isBlank()) {
                return Mono.just("admin:" + adminId);
            }

            // 2. 使用 C 端用户 ID（同样已由网关注入可信值），适用于秒杀等防刷场景
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            // 3. 匿名/白名单请求降级为真实 IP 限流，适用于全局防恶意爬虫/DDoS
            String realIp = getRealClientIp(exchange);
            return Mono.just("ip:" + realIp);
        };
    }

    /**
     * 获取真实客户端 IP 地址
     * <p>
     * 根据配置决定信任策略：
     * - 当 trustProxyHeaders 为 true 时，优先从代理头（X-Forwarded-For、X-Real-IP）中提取客户端 IP
     * - 当 trustProxyHeaders 为 false 时，直接从网络层获取 RemoteAddress，避免客户端伪造
     * - 极端情况下 RemoteAddress 为空时，使用请求唯一 ID 作为兜底标识
     *
     * @param exchange ServerWebExchange 对象，用于获取请求信息
     * @return 客户端 IP 地址字符串，或兜底标识字符串（格式为 "fallback:{requestId}"）
     */
    private String getRealClientIp(ServerWebExchange exchange) {
        if (trustProxyHeaders) {
            // 信任代理头，适用于有 Nginx/SLB 的环境
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }
        // 从网络层直接获取 RemoteAddress，防止客户端伪造
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress) // 获取 IP 地址
                .map(InetAddress::getHostAddress) // 转换为字符串
                .orElseGet(() -> {
                    // 极端情况下 RemoteAddress 为空（如模拟测试），
                    // 使用请求唯一 ID 作为兜底，防止所有异常请求共用一个令牌桶
                    return "fallback:" + exchange.getRequest().getId();
                });
    }
}