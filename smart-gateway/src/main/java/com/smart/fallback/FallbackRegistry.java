package com.smart.fallback;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Fallback 策略注册表（调度器）
 */
@Component
public class FallbackRegistry {

    /**
     * 策略集合
     * Spring 启动时，会自动扫描容器中所有实现了 FallbackStrategy 接口的 Bean，
     * 并根据 @Order 注解排序后，注入到这个 List 中。
     */
    private final List<FallbackStrategy> strategies;

    public FallbackRegistry(List<FallbackStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 遍历策略链，执行第一个匹配的策略
     */
    public Mono<ServerResponse> execute(String routeId, String path, String method, Throwable throwable) {
        // 遍历 Spring 自动收集好的策略集合
        for (FallbackStrategy strategy : strategies) {
            if (strategy.match(routeId, path, method)) {
                return strategy.handle(throwable);
            }
        }
        // 如果所有策略都不匹配，走默认兜底
        return CommonFallback.gatewayBlockHandler();
    }
}