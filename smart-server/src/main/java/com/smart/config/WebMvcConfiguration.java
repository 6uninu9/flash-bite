package com.smart.config;

import com.smart.interceptor.UserContextInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;

    public WebMvcConfiguration(UserContextInterceptor userContextInterceptor) {
        this.userContextInterceptor = userContextInterceptor;
    }

    //登录接口需要放行，否则无法获取 Token
    //Swagger UI 和 OpenAPI 文档需要公开访问，否则无法查看
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册拦截器");
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns(
                        "/error",                     // 只排除 Spring 错误页
                        "/favicon.ico"               // 排除图标
                );
    }

    // 配置OpenAPI自定义信息（可选）
    @Bean
    public OpenAPI customOpenAPI() {
        log.info("生成接口文档");
        return new OpenAPI()
                .info(new Info()
                        .title("闪食项目API文档")
                        .version("2.0")
                        .description("闪食项目后端API接口文档"));
    }
}