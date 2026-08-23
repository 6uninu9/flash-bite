package com.smart.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.constant.JwtClaimsConstant;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.properties.JwtProperties;
import com.smart.result.Result;
import com.smart.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * Performs JWT authentication for direct requests to smart-server.
 * 对直接进入 smart-server 的请求执行 JWT 鉴权。
 *
 * <p>Identity headers from clients are never trusted. A request context is
 * created only after the corresponding user or administrator JWT is verified.
 * 不信任客户端传入的身份 Header；仅在对应用户或管理员 JWT 验证通过后创建请求上下文。</p>
 */
@Component
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    // 白名单路径模式，允许匿名访问的接口
    // Anonymous endpoint patterns.
    // 匿名可访问的接口路径模式。
    private static final List<String> ANONYMOUS_PATH_PATTERNS = List.of(
            "/user/user/login",
            "/user/shop/status",
            "/user/category/list",
            "/user/dish/list",
            "/admin/employee/login",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtProperties jwtProperties;

    public UserContextInterceptor(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String path = request.getRequestURI();
        if (isAnonymousPath(path)) {
            return true;
        }

        boolean adminRequest = pathMatcher.match("/admin/**", path);
        String token = resolveToken(request, adminRequest
                ? jwtProperties.getAdminTokenName()
                : jwtProperties.getUserTokenName());
        if (token == null) {
            log.warn("Missing JWT | IP: {} | URI: {}", request.getRemoteAddr(), path);
            writeUnauthorized(response, MessageConstant.USER_NOT_LOGIN);
            return false;
        }

        try {
            Claims claims = JwtUtil.parseJWT(
                    adminRequest ? jwtProperties.getAdminSecretKey() : jwtProperties.getUserSecretKey(), token);
            String claimName = adminRequest ? JwtClaimsConstant.EMP_ID : JwtClaimsConstant.USER_ID;
            Object subjectId = claims.get(claimName);
            if (subjectId == null) {
                log.warn("JWT is missing required claim | URI: {} | claim: {}", path, claimName);
                writeUnauthorized(response, MessageConstant.INVALID_USER_IDENTITY);
                return false;
            }
            BaseContext.setCurrentId(Long.parseLong(subjectId.toString()));
            return true;
        } catch (Exception e) {
            log.warn("JWT authentication failed | IP: {} | URI: {} | error: {}",
                    request.getRemoteAddr(), path, e.getMessage());
            writeUnauthorized(response, MessageConstant.USER_NOT_LOGIN);
            return false;
        }
    }

    private String resolveToken(HttpServletRequest request, String tokenName) {
        String token = request.getHeader(tokenName);
        if (token != null && !token.isBlank()) {
            return token;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String bearerToken = authorization.substring(7).trim();
            return bearerToken.isBlank() ? null : bearerToken;
        }
        return null;
    }

    private boolean isAnonymousPath(String path) {
        return ANONYMOUS_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, message)));
        } catch (IOException e) {
            log.error("Failed to write 401 response", e);
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
