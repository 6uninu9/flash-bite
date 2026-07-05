package com.smart.fallback;

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 降级策略顶层接口
 */
public interface FallbackStrategy {
    /**
     * 判断是否匹配当前请求
     * @param routeId 路由ID
     * @param path 请求路径
     * @param method 请求方法
     */
    boolean match(String routeId, String path, String method);

    /**
     * 执行具体的降级响应逻辑
     * @param throwable 触发降级的异常 (如 FlowException 限流, DegradeException 熔断)
     * @return 响应式的 ServerResponse 对象
     */
    Mono<ServerResponse> handle(Throwable throwable);
}