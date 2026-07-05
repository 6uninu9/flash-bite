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
 * 地址簿查询降级策略
 * <p>
 * 设计理由：
 * - 普通读接口，适度保护即可
 * - 地址信息对用户下单流程至关重要，降级提示需要引导用户重试
 */
@Component
@Order(5)
public class AddressBookFallback extends AbstractFallbackStrategy {

    public AddressBookFallback() {
        super("addressBook-list-route", "/user/addressBook/list");
    }

    @Override
    public Mono<ServerResponse> handle(Throwable throwable) {
        // 熔断降级
        if (throwable instanceof DegradeException) {
            return CommonFallback.gatewayBlockHandler(503, "地址服务暂时不可用，请稍后重试");
        }
        // 限流
        if (throwable instanceof FlowException || throwable instanceof ParamFlowException) {
            return CommonFallback.gatewayBlockHandler(429, "请求过于频繁，请稍后再试");
        }
        // 系统保护
        if (throwable instanceof SystemBlockException) {
            return CommonFallback.gatewayBlockHandler(503, "系统繁忙，请稍后重试");
        }
        return CommonFallback.gatewayBlockHandler();
    }
}