package com.smart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    /**
     * 创建一个线程池，用于处理订单相关的任务（实现任务并行化）
     * @return 线程池对象
     */
    @Bean("orderTaskExecutor")
    public Executor orderTaskExecutor() {
        // Spring 增强线程池，支持优雅停机、上下文传递、监控、整合 @Async，底层是 ThreadPoolExecutor
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：IO密集型任务可设置为 CPU核心数 * 2
        executor.setCorePoolSize(32);
        // 最大线程数：控制并发峰值，避免压垮下游服务（核心线程数+空闲线程数）
        executor.setMaxPoolSize(64);
        // 空闲线程存活时间（60s）：超过这个时间，空闲线程会被回收
        executor.setKeepAliveSeconds(60);
        // 队列容量：缓冲等待任务，避免线程频繁创建销毁
        executor.setQueueCapacity(150);
        // 线程名前缀：方便日志排查问题
        executor.setThreadNamePrefix("order-parallel-task-");
        // 拒绝策略：队列满了后，让主线程自己执行，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }

    /**
     * 创建一个线程池，用于异步重建菜品缓存
     * 该线程池不是用来“同时跑多个重建”，而是用来承接大量一次性投递的异步任务，保护系统资源、隔离环境影响、控制执行速率。
     * @return 线程池对象
     */
    @Bean("rebuildDishCacheExecutor")
    public Executor rebuildDishCacheExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); // 最多只有一个异步线程重建缓存
        executor.setMaxPoolSize(2);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(50); // 超过 50 个待执行的重建任务就直接拒绝，避免内存撑爆
        executor.setThreadNamePrefix("rebuild-dish-cache-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy()); // 拒绝时丢弃，不发异常
        executor.initialize();
        return executor;
    }
}