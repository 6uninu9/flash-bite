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
 * 普通下单降级策略
 * <p>
 * 设计理由：
 * - 下单是写操作，直接消耗 DB 连接池资源
 * - 限流时提示"下单人数过多"，符合用户心智模型
 * - 熔断时说明服务不可用，引导用户稍后重试
 */
@Component
@Order(3)
public class OrderSubmitFallback extends AbstractFallbackStrategy {

    public OrderSubmitFallback() {
        super("order-submit-route", "/user/order/submit");
    }

    @Override
    public Mono<ServerResponse> handle(Throwable throwable) {
        // 熔断降级
        if (throwable instanceof DegradeException) {
            return CommonFallback.gatewayBlockHandler(503, "下单服务暂时不可用，请稍后重试");
        }
        // 限流
        if (throwable instanceof FlowException || throwable instanceof ParamFlowException) {
            return CommonFallback.gatewayBlockHandler(429, "下单人数过多，系统繁忙请稍后再试");
        }
        // 系统保护
        if (throwable instanceof SystemBlockException) {
            return CommonFallback.gatewayBlockHandler(503, "系统负载过高，请稍后重试");
        }
        return CommonFallback.gatewayBlockHandler();
    }
}