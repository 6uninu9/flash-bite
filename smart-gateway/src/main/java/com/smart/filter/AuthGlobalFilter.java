package com.smart.filter;

import com.smart.constant.JwtClaimsConstant;
import com.smart.properties.JwtProperties;
import com.smart.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

/**
 * 全局鉴权过滤器
 * 拦截所有请求，校验 JWT 并将 userId 注入 Header 传递给下游
 */
@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtProperties jwtProperties;

    // 用户白名单路径
    private static final List<String> USER_WHITE_LIST = List.of(
            "/user/user/login",
            "/user/shop/status",
            "/user/category/list",
            "/user/dish/list",
            "/swagger-ui/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**"
    );

    // 管理端白名单路径
    private static final List<String> ADMIN_WHITE_LIST = List.of(
            "/admin/employee/login",
            "/swagger-ui/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**"
    );

    // 路径前缀约定
    private static final String ADMIN_PATH_PREFIX = "/admin/";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthGlobalFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从交换对象中获取请求对象
        ServerHttpRequest request = exchange.getRequest();
        // 获取请求路径
        String path = request.getURI().getPath();

        // 判断是否为商家管理端请求
        boolean isAdminRequest = pathMatcher.match(ADMIN_PATH_PREFIX + "**", path);// 商家管理端路径匹配

        // 1. 白名单放行
        // 检查当前请求路径是否在白名单中，如果在则直接放行
        if (isAdminRequest && isWhiteListed(path, ADMIN_WHITE_LIST)) {
            return chain.filter(exchange);
        }
        if (!isAdminRequest && isWhiteListed(path, USER_WHITE_LIST)) {
            return chain.filter(exchange);
        }

        // 3. 动态选择密钥与 Token Header 名称
        Key secretKey = isAdminRequest ? jwtProperties.getAdminSecretKey() : jwtProperties.getUserSecretKey();
        String tokenName = isAdminRequest ? jwtProperties.getAdminTokenName() : jwtProperties.getUserTokenName();
        String targetHeader = isAdminRequest ? "X-Admin-Id" : "X-User-Id";

        // 2. 获取并校验 Token
        // 从请求头中获取 Token
        String token = request.getHeaders().getFirst(tokenName);
        if (token == null || token.isBlank()) {
            // 降级尝试从标准 Authorization 头获取 (去除 Bearer 前缀)
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        // 检查 Token 是否存在或为空
        if (token == null || token.isBlank()) {
            return unauthorizedResponse(exchange, "缺少认证 Token");
        }

        try {
            // 3. 解析 JWT，使用 JWT 工具类解析 Token，获取其中的声明信息
            Claims claims = JwtUtil.parseJWT(secretKey, token); // 解析令牌

            // 4. 从声明中获取用户 ID
            Object idObj = claims.get(JwtClaimsConstant.USER_ID);
            if (idObj == null){
                // 如果用户 ID 为空，可能是商家管理端id
                idObj = claims.get(JwtClaimsConstant.EMP_ID);
            }
            // 检查 ID 是否存在
            if (idObj == null) {
                return unauthorizedResponse(exchange, "Token 解析失败：缺少用户信息");
            }
            String id = idObj.toString();

            // 5. 鉴权成功：重构请求头
            // 创建一个新的请求构建器，用于修改请求头
            // 移除所有可能的鉴权头和身份头，防止下游伪造或重复解析
            // 注入 X-User-Id或X-Admin-Id 供下游业务直接使用
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove(HttpHeaders.AUTHORIZATION);
                        headers.remove(jwtProperties.getUserTokenName());
                        headers.remove(jwtProperties.getAdminTokenName());
                        headers.remove("X-Admin-Id");
                        headers.remove("X-User-Id"); // 防止恶意用户在请求头中伪造 X-User-Id 绕过网关
                    })  // 移除原始认证头
                    .header(targetHeader, id)  // 添加真正的用户 ID 头信息
                    .build();

            // 6. 使用修改后的请求继续过滤链
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            // 捕获并记录 JWT 解析过程中的异常
            log.warn("JWT 鉴权失败, path: {}, error: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Token 无效或已过期");
        }
    }

    /**
     * 检查给定的路径是否在白名单中
     *
     * @param path 需要检查的路径字符串
     * @return 如果路径匹配白名单中的任何一个模式，则返回true；否则返回false
     */
    private boolean isWhiteListed(String path, List<String> whiteList) {
        // 使用Java 8的Stream API遍历WHITE_LIST中的所有模式
        // 使用anyMatch方法检查是否存在至少一个模式与给定路径匹配
        // pathMatcher.match方法用于执行实际的模式匹配
        return whiteList.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 处理未授权响应的方法
     *
     * @param exchange 服务器网络交换对象，包含请求和响应信息
     * @param msg      未授权的错误信息
     * @return 返回一个Mono<Void>表示异步操作的完成
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg) {
        // 获取响应对象
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) return Mono.empty();
        // 设置HTTP状态码为401未授权
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        // 设置响应头内容类型为JSON，并指定字符集为UTF-8
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        // 构造JSON格式的错误响应体
        String body = "{\"code\":401,\"msg\":\"" + msg + "\"}";
        // 将响应体字符串转换为DataBuffer
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        // 将响应体写入响应并返回
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 优先级高于内置的 RequestRateLimiter (确保限流前不消耗过多资源，或限流后不执行鉴权)
        // 这里设置为 -1，让鉴权在限流之前执行，防止恶意请求打满 Redis 令牌桶
        return -1;
    }
}