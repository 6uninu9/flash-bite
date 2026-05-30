package com.smart.interceptor;

import com.smart.constant.JwtClaimsConstant;
import com.smart.context.BaseContext;
import com.smart.properties.JwtProperties;
import com.smart.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {


    private final JwtProperties jwtProperties;

    public JwtTokenUserInterceptor(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 拦截请求，进行jwt令牌校验
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());
        if (token == null || token.trim().isEmpty()) {
            log.warn("令牌为空");
            response.setStatus(HttpStatus.UNAUTHORIZED.value()); // 状态码401
            response.setContentType("application/json;charset=UTF-8"); // 响应内容类型为json
            response.getWriter().write("{\"code\":0,\"msg\":\"缺少令牌\"}"); // 响应内容
            return false;
        }

        //2、校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token); // 解析令牌
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString()); // 获取员工id
            BaseContext.setCurrentId(userId); // 设置当前线程的当前员工id，用于后续方法调用
            log.info("当前员工id：{}", userId);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            //4、不通过，响应401状态码、内容类型为json和相关错误信息内容
            log.warn("JWT 验证失败: {}", ex.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"" + ex.getMessage() + "\"}");
            return false;
        }
    }

    /**
     * 请求处理完成后执行（无论成功还是失败）
     * 在这里清理ThreadLocal，解决内存泄漏问题
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex){
        BaseContext.removeCurrentId();
        log.debug("清理ThreadLocal中的用户ID");
    }
}
