package com.smart.fallback;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 秒杀下单降级策略（仅限 POST 请求）
 */
@Component
@Order(1) // 数字越小优先级越高，确保精确匹配的策略先执行
public class SeckillOrderFallback extends AbstractFallbackStrategy {

    public SeckillOrderFallback() {
        // 匹配包含 seckill 的路由或路径，且必须是 POST 请求
        super("seckill-post-route", "/user/coupon/seckill", "POST");
    }

    @Override
    public Mono<ServerResponse> handle(Throwable throwable) {
        // 熔断降级
        if (throwable instanceof DegradeException) {
            return CommonFallback.gatewayBlockHandler(503, "服务暂时不可用，请稍后重试");
        }
        // 限流（包括 QPS 限流和热点参数限流）
        if (throwable instanceof FlowException || throwable instanceof ParamFlowException) {
            return CommonFallback.gatewayBlockHandler(429, "活动太火爆，请稍后再试！");
        }
        // 系统保护（CPU/负载等）
        if (throwable instanceof SystemBlockException) {
            return CommonFallback.gatewayBlockHandler(503, "系统负载过高，请稍后重试");
        }
        // 兜底
        return CommonFallback.gatewayBlockHandler();
    }
}