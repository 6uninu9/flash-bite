package com.smart.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.smart.fallback.CommonFallback;
import com.smart.fallback.FallbackRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 Sentinel 拦截配置
 * <p>
 * 核心职责：
 * 1. 自定义 Sentinel 限流/熔断时的响应处理逻辑
 * 2. 当接口慢请求超过一定值，将默认的 HTML 错误页替换为统一的 JSON 格式返回给前端
 */
@Slf4j
@Configuration
public class SentinelGatewayHandler {

    private final FallbackRegistry fallbackRegistry;

    public SentinelGatewayHandler(FallbackRegistry fallbackRegistry) {
        this.fallbackRegistry = fallbackRegistry;
    }

    /**
     * 初始化方法，在 Bean 创建完成后自动执行
     * <p>
     * 作用时机：Spring 容器完成该 Configuration Bean 的实例化后立即调用
     */
    @PostConstruct
    public void init() {
        // 注册自定义的 Block 处理器，复用 CommonFallback
        // GatewayCallbackManager: Sentinel 提供的全局回调管理器，用于定制被拦截后的行为
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            /**
             * 当请求被 Sentinel 拦截时触发的回调方法
             *
             * @param exchange  ServerWebExchange 对象，包含当前请求和响应的上下文信息
             * @param throwable 触发拦截的异常对象（如 FlowException 表示限流，DegradeException 表示熔断）
             * @return Mono<ServerResponse> 响应式返回体，封装了要返回给客户端的 HTTP 响应
             */
            @Override
            public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
                // 获取当前请求的路由ID、路径、请求方式等信息
                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                String routeId = (route != null) ? route.getId() : "unknown";
                String path = exchange.getRequest().getURI().getPath();
                String method = exchange.getRequest().getMethod().name();

                log.warn("Sentinel Block - routeId: {}, path: {}, method: {}, exception: {}",
                        routeId, path, method, throwable.getClass().getSimpleName());

                // 委托给 fallbackRegistry 的统一匹配调度处理，返回标准化的 JSON 错误响应
                // 这样前端收到的不是 Sentinel 默认的 "Blocked by Sentinel" 文本，而是类似 {"code": 429, "msg": "请求过于频繁"} 的结构
                return fallbackRegistry.execute(routeId, path, method, throwable);
            }
        });
    }
}
