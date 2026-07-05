package com.smart.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * 下游业务拦截器 (替代原有的 JWT 解析)
 * 仅负责从网关注入的 Header 中提取 userId 并放入 ThreadLocal
 */
@Component
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    /**
     * 匿名访问路径白名单（需与网关白名单保持一致，或外置到配置文件）
     */
    private static final List<String> ANON_PATH_PATTERNS = List.of(
            // 用户端匿名路径
            "/user/user/login",
            "/user/shop/status",
            "/user/category/list",
            "/user/dish/list",
            "/user/coupon/seckill",
            // 商家管理端匿名路径
            "/admin/employee/login",
            // Swagger / OpenAPI 相关
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String path = request.getRequestURI();

        // 1. 获取网关中注入的商家管理端 ID
        String adminIdStr = request.getHeader("X-Admin-Id");
        // 2. 有合法 adminId 则放入上下文，放行
        if (adminIdStr != null && !adminIdStr.isBlank()) {
            try {
                BaseContext.setCurrentId(Long.parseLong(adminIdStr));
                return true;
            } catch (NumberFormatException e) {
                log.warn("非法 X-Admin-Id 格式: {} | IP: {} | URI: {}",
                        adminIdStr, request.getRemoteAddr(), path);
                writeUnauthorized(response, MessageConstant.INVALID_USER_IDENTITY);
                return false;
            }
        }

        // 3. 获取网关中注入的普通用户 ID
        String userIdStr = request.getHeader("X-User-Id");
        // 4. 有合法 userId 则放入上下文，放行
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                BaseContext.setCurrentId(Long.parseLong(userIdStr));
                return true;
            } catch (NumberFormatException e) {
                log.warn("非法 X-User-Id 格式: {} | IP: {} | URI: {}",
                        userIdStr, request.getRemoteAddr(), path);
                writeUnauthorized(response, MessageConstant.INVALID_USER_IDENTITY);
                return false;
            }
        }

        // 5. 无 userId：若为匿名路径则放行（BaseContext 为空）
        if (isAnonymousPath(path)) {
            log.debug("匿名访问路径: {}", path);
            return true;
        }

        // 6. 需要登录的请求但缺失 userId （可能绕过了网关），拒绝访问
        log.warn("缺少 X-User-Id 请求头，疑似绕过网关 | IP: {} | URI: {}",
                request.getRemoteAddr(), path);
        writeUnauthorized(response, MessageConstant.USER_NOT_LOGIN);
        return false;
    }

    /**
     * 判断当前请求路径是否为匿名可访问路径
     */
    private boolean isAnonymousPath(String path) {
        return ANON_PATH_PATTERNS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 输出统一的 401 JSON 响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        try {
            Result<?> result = Result.error(401, message);
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (IOException e) {
            log.error("写入 401 响应失败", e);
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        // 请求结束后清理 ThreadLocal，防止内存泄漏和线程池复用导致的数据错乱
        BaseContext.removeCurrentId();
    }
}