package com.smart.fallback;

import com.smart.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 通用 Sentinel 降级处理类
 */
public class CommonFallback {

    /**
     * 网关层通用 Block 响应 (WebFlux 风格)
     */
    public static Mono<ServerResponse> gatewayBlockHandler() {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":429,\"msg\":\"系统繁忙，请稍后再试\",\"data\":null}");
    }
}