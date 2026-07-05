package com.smart.fallback;

import org.springframework.util.StringUtils;

/**
 * 降级策略抽象基类
 * <p>
 * 作用：封装通用的路由、路径、请求方式匹配逻辑，子类只需实现 handle() 方法，只需关注"我要返回什么"，不用关心"怎么匹配"
 */
public abstract class AbstractFallbackStrategy implements FallbackStrategy {

    // 匹配条件：路由ID关键字
    private final String routeIdPattern;
    // 匹配条件：请求路径关键字
    private final String pathPattern;
    // 匹配条件：HTTP请求方式（为 null 表示不限制请求方式）
    private final String method;

    /**
     * 全参构造器（指定请求方式）
     */
    protected AbstractFallbackStrategy(String routeIdPattern, String pathPattern, String method) {
        this.routeIdPattern = routeIdPattern;
        this.pathPattern = pathPattern;
        this.method = method;
    }

    /**
     * 便捷构造器（不限制请求方式）
     */
    protected AbstractFallbackStrategy(String routeIdPattern, String pathPattern) {
        this(routeIdPattern, pathPattern, null);
    }
    
    /**
     * 极简构造器（仅匹配路由ID）
     */
    protected AbstractFallbackStrategy(String routeIdPattern) {
        this(routeIdPattern, null, null);
    }

    @Override
    public boolean match(String routeId, String path, String method) {
        // 1. 匹配 routeId（如果配置了且包含）
        if (StringUtils.hasText(this.routeIdPattern) && !routeId.contains(this.routeIdPattern)) {
            return false;
        }
        // 2. 匹配 path（如果配置了且包含）
        if (StringUtils.hasText(this.pathPattern) && !path.contains(this.pathPattern)) {
            return false;
        }
        // 3. 匹配 method（如果未配置，则直接放行；如果配置了则必须严格匹配）
        return this.method == null || this.method.equalsIgnoreCase(method);
    }
}