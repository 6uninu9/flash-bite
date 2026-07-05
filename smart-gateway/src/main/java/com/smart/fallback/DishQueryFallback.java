package com.smart.fallback;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.smart.fallback.AbstractFallbackStrategy;
import com.smart.fallback.CommonFallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 菜品列表查询降级策略
 * <p>
 * 设计理由：
 * - 高频读接口，有 Redis 缓存兜底，QPS 阈值设得很高
 * - 被限流大概率是恶意爬虫或 DDoS，提示语偏中性
 * - 精确匹配 GET，防止 POST 修改菜品的请求误匹配
 */
@Component
@Order(4)
public class DishQueryFallback extends AbstractFallbackStrategy {

    public DishQueryFallback() {
        super("dish-query-route", "/user/dish/list", "GET");
    }

    @Override
    public Mono<ServerResponse> handle(Throwable throwable) {
        // 熔断降级：缓存穿透或下游服务挂了
        if (throwable instanceof DegradeException) {
            return CommonFallback.gatewayBlockHandler(503, "数据加载中，请稍后刷新");
        }
        // 限流：大概率是爬虫
        if (throwable instanceof FlowException || throwable instanceof ParamFlowException) {
            return CommonFallback.gatewayBlockHandler(429, "访问过于频繁，请稍后再试");
        }
        // 系统保护
        if (throwable instanceof SystemBlockException) {
            return CommonFallback.gatewayBlockHandler(503, "系统繁忙，请稍后重试");
        }
        return CommonFallback.gatewayBlockHandler();
    }
}