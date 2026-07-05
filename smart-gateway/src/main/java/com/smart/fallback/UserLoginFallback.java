package com.smart.fallback;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 用户登录降级策略
 * <p>
 * 设计理由：
 * - 登录接口是安全防线，被限流往往意味着暴力破解或短信轰炸
 * - 不需要熔断降级，因为登录服务本身较轻量
 * - 提示语偏向安全提示，而非系统繁忙
 */
@Component
@Order(2)
public class UserLoginFallback extends AbstractFallbackStrategy {

    public UserLoginFallback() {
        // 不限制 Method，因为登录可能支持 GET/POST
        super("user-login-route", "/user/user/login");
    }

    @Override
    public Mono<ServerResponse> handle(Throwable throwable) {
        // 限流：大概率是恶意攻击或频繁重试
        if (throwable instanceof FlowException || throwable instanceof ParamFlowException) {
            return CommonFallback.gatewayBlockHandler(429, "操作过于频繁，请60秒后再试");
        }
        // 系统保护
        if (throwable instanceof SystemBlockException) {
            return CommonFallback.gatewayBlockHandler(503, "系统维护中，请稍后再试");
        }
        return CommonFallback.gatewayBlockHandler();
    }
}