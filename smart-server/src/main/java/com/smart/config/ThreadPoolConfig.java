package com.smart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    /**
     * 创建基于虚拟线程的 Executor Bean
     * 不需要设置核心线程数、队列大小等参数来池化，因为虚线程创建与销毁的成本极低，
     * JVM 可以轻松创建数百万个虚线程，既然资源几乎无限且廉价，就没有必要"限制"数量，也就不需要"池化"来复用
     * @return 虚拟线程执行器
     */
    @Bean("virtualTaskExecutor") // 将该方法的返回值注册为 Spring Bean，命名为 "virtualTaskExecutor"，可在其他地方通过 @Qualifier 或 @Resource 注入使用
    public Executor virtualTaskExecutor() { // 方法返回 java.util.concurrent.Executor 接口类型，提供任务执行能力
        // 直接返回一个为每个任务创建虚线程的执行器
        return Executors.newVirtualThreadPerTaskExecutor(); // 调用 JDK 21+ 的工厂方法，创建一个每次提交任务时都会新建一个虚拟线程来执行的 ExecutorService
    }
}