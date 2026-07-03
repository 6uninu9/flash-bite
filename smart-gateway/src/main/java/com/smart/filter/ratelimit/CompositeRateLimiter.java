package com.smart.filter.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.*;

/**
 * 包装 Redis 限流器
 * 改进点：将简单的失败计数器替换为【分桶滑动窗口】，解决高并发下的统计丢失和边界突刺问题。
 */
@Slf4j
@Component
@SuppressWarnings("UnstableApiUsage") // 抑制@Beta声明警告
public class CompositeRateLimiter implements org.springframework.cloud.gateway.filter.ratelimit.RateLimiter<RedisRateLimiter.Config> {

    // 原生 RedisRateLimiter
    private final RedisRateLimiter redisRateLimiter;
    // 本地限流器缓存
    private final Cache<String, RateLimiter> localLimiters;

    // 熔断配置常量，用于定义熔断器的行为参数
    // 失败率阈值 (例如 0.5 表示 50% 的失败率触发熔断)
    private static final double FAILURE_RATE_THRESHOLD = 0.5;
    // 最小请求数阈值 (窗口内总请求数低于此值时，即使全失败也不熔断，防止低流量误判)
    private static final int MIN_REQUEST_THRESHOLD = 10;
    // 统计时间窗口（10秒）
    private static final Duration STATISTIC_WINDOW = Duration.ofSeconds(10);
    // 探测间隔（5秒）
    private static final Duration PROBE_INTERVAL = Duration.ofSeconds(5);
    // 探测超时（2秒）
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    // 熔断状态机，用于维护熔断器的状态
    // 枚举定义熔断状态：CLOSED - 正常状态，OPEN - 熔断状态
    private enum State { CLOSED, OPEN }
    // 记录熔断器状态
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    // 用分桶滑动窗口统计失败次数和总请求数
    // 10秒窗口，切分为10个桶（每桶1秒）
    private final SlidingWindowCounter metricsCounter = new SlidingWindowCounter(
            STATISTIC_WINDOW.toMillis(), 10
    );

    // 熔断探测
    // 探测标志
    private final AtomicBoolean probing = new AtomicBoolean(false);
    // 探测时间
    private final AtomicLong lastProbeTime = new AtomicLong(0);

    public CompositeRateLimiter(RedisRateLimiter redisRateLimiter) {
        this.redisRateLimiter = redisRateLimiter;
        this.localLimiters = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 返回原生 RedisRateLimiter 的配置，保证 Actuator 监控端点正常显示
     * @return 原生 RedisRateLimiter 的配置
     */
    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return redisRateLimiter.getConfig() != null ? redisRateLimiter.getConfig() : new HashMap<>();
    }

    /**
     * 包装RedisRateLimiter 的 isAllowed() 方法，添加熔断降级逻辑
     * @param routeId 路由ID
     * @param id 请求ID
     * @return Mono<Response>
     */
    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        // 1. 统计请求
        metricsCounter.addTotal();

        // 2. 熔断状态判断
        if (state.get() == State.OPEN) {
            // 2. 如果处于熔断状态，判断是否满足探测条件
            return shouldProbe() ?
                    // 2.1 满足条件，则执行Redis探测
                    probeRedis(routeId, id) :
                    // 2.2 不满足条件，则继续走Guava限流逻辑
                    fallbackToGuava(routeId, id);
        }

        // 3. 正常状态：执行 Redis 限流
        return redisRateLimiter.isAllowed(routeId, id)
                // 发生Redis限流异常，则执行降级逻辑
                .onErrorResume(throwable -> {
                    log.warn("Redis限流异常，触发降级！route={}, err={}", routeId, throwable.getMessage());
                    // 记录失败次数，并判断是否需要熔断
                    recordFailureAndMaybeOpen();
                    // 执行Guava限流降级逻辑
                    return fallbackToGuava(routeId, id);
                })
                // 防止底层 Redis 客户端出现 Bug 返回了空的 Mono，导致流直接终止而不触发 onErrorResume
                .switchIfEmpty(Mono.defer(() -> {
                    recordFailureAndMaybeOpen();
                    return fallbackToGuava(routeId, id);
                }));
    }

    /**
     * Redis 熔断探测
     * @param routeId 路由ID
     * @param id 请求ID
     * @return Mono<Response>
     */
    private Mono<Response> probeRedis(String routeId, String id) {
        log.info("触发 Redis 探测请求: route={}", routeId);
        return redisRateLimiter.isAllowed(routeId, id)
                // 超时控制，防止 Redis 连接池耗尽导致请求永久挂起，从而锁死 probing 标志
                .timeout(PROBE_TIMEOUT)
                .doOnNext(response -> {
                    log.info("探测成功，Redis 恢复正常。");
                    // 重置熔断器状态为 CLOSED
                    resetToClosed();
                })
                .onErrorResume(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        log.warn("探测超时({}s)，Redis 仍不可用。", PROBE_TIMEOUT.getSeconds());
                    } else {
                        log.warn("探测失败，Redis 仍不可用。err={}", throwable.getMessage());
                    }
                    // 探测失败也记入滑动窗口
                    recordFailureAndMaybeOpen();
                    // 执行Guava限流降级逻辑
                    return fallbackToGuava(routeId, id);
                })
                // 探测标志置为 false，表示熔断探测结束，释放探测锁，让下一个 5 秒能继续探测
                .doFinally(signalType -> probing.set(false));
    }

    /**
     * 触发 Redis 熔断探测
     * @return true 表示需要触发熔断探测
     */
    private boolean shouldProbe() {
        // 1. 获取当前时间
        long now = System.currentTimeMillis();
        // 2. 获取上次熔断探测时间
        long lastProbe = lastProbeTime.get();
        // 3. 如果当前时间 - 上次熔断探测时间 > 熔断探测间隔，则触发熔断探测
        if (now - lastProbe > PROBE_INTERVAL.toMillis()) {
            // 4. 使用双重 CAS 检查，尝试设置最后一次探测时间和尝试设置熔断探测标志，均成功表示当前请求可以触发熔断探测
            return lastProbeTime.compareAndSet(lastProbe, now) && probing.compareAndSet(false, true);
        }
        return false;
    }

    /**
     * 记录失败并判断是否熔断，并基于失败率判断是否熔断
     * 使用滑动窗口累加，彻底解决并发覆盖和边界突刺
     */
    private void recordFailureAndMaybeOpen() {
        // 1. 将失败事件记入滑动窗口
        metricsCounter.addFailure();

        // 2. 获取窗口快照
        WindowSnapshot snapshot = metricsCounter.getSnapshot();

        // 3. 达到阈值（总请求数达标 && 失败率超标）且状态为 CLOSED，则 CAS 切换为 OPEN
        if (snapshot.totalCount >= MIN_REQUEST_THRESHOLD &&
                snapshot.failureRate >= FAILURE_RATE_THRESHOLD) {

            // CAS 切换状态
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                log.warn("触发熔断！10秒内总请求:{}，失败数:{}，失败率:{}% >= {}%",
                        snapshot.totalCount, snapshot.failureCount,
                        (int)(snapshot.failureRate * 100), (int)(FAILURE_RATE_THRESHOLD * 100));
            }
        }
    }

    private void resetToClosed() {
        if (state.get() != State.CLOSED) {
            state.set(State.CLOSED);

            // 重置滑动窗口，清理历史失败次数，
            // 避免在熔断恢复后的少量失败与历史失败叠加导致熔断，从而在最初的 10 秒内几乎无法真正恢复
            metricsCounter.reset();

            log.info("Redis 熔断器 CLOSED。");
        }
    }

    /**
     * 降级逻辑：使用 Guava RateLimiter 实现
     * @param routeId 路由ID
     * @param key 请求ID
     * @return Mono<Response>
     */
    private Mono<Response> fallbackToGuava(String routeId, String key) {
        return Mono.fromCallable(() -> { // 将同步的 Guava 操作包装成响应式流，延迟到订阅时执行
            String limiterKey = routeId + ":" + key;
            // 获取降级QPS
            double fallbackQps = getFallbackQps(routeId);

            // 从 Caffeine 缓存获取或创建 Guava 限流器
            // 由于 Redis 恢复后流量会切回 Redis，且 Guava 实例不会长期存活（有超时时间），无需担心"令牌囤积"问题
            RateLimiter limiter = localLimiters.get(limiterKey, k ->
                    // 使用 SmoothBursty (突发型)
                    // 降级场景需要立刻提供全量限流能力，不需要预热期，避免降级初期误杀正常请求
                    RateLimiter.create(fallbackQps)
            );

            // 非阻塞地尝试获取通行令牌
            boolean acquired = limiter.tryAcquire(0, TimeUnit.MILLISECONDS);
            // 返回响应
            return new Response(acquired, Collections.singletonMap("X-RateLimit-Fallback", "true"));
        });
    }

    /**
     * 获取降级QPS
     * 后续可根据业务情况调整拓展降级QPS
     *
     * @param routeId 路由ID
     * @return 降级QPS
     */
    private double getFallbackQps(String routeId) {
        return (routeId != null && routeId.contains("seckill")) ? 50.0 : 100.0;
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() { return redisRateLimiter.getConfigClass(); }
    @Override
    public RedisRateLimiter.Config newConfig() { return redisRateLimiter.newConfig(); }


    // 轻量级分桶滑动窗口 (Bucketed Sliding Window)

    /**
     * 窗口快照 (不可变对象)
     * 用于快速计算窗口内失败率
     */
    private static class WindowSnapshot {
        final long totalCount;
        final long failureCount;
        final double failureRate;

        WindowSnapshot(long totalCount, long failureCount) {
            this.totalCount = totalCount;
            this.failureCount = failureCount;
            this.failureRate = totalCount == 0 ? 0.0 : (double) failureCount / totalCount;
        }
    }

    /**
     * 分桶滑动窗口计数器
     * 原理：将 totalTimeMs 切分为 bucketCount 个桶，使用环形数组存储。
     * 每次统计时，遍历数组，丢弃过期的桶，累加有效桶的数据。
     */
    private static class SlidingWindowCounter {
        // 窗口时长
        private final long windowDurationMs;
        // 桶数量
        private final int bucketCount;
        // 桶时长
        private final long bucketDurationMs;
        // 桶
        private final AtomicReferenceArray<WindowBucket> buckets;

        public SlidingWindowCounter(long windowDurationMs, int bucketCount) {
            this.windowDurationMs = windowDurationMs;
            this.bucketCount = bucketCount;
            this.bucketDurationMs = windowDurationMs / bucketCount;
            this.buckets = new AtomicReferenceArray<>(bucketCount);
            for (int i = 0; i < bucketCount; i++) {
                buckets.set(i, new WindowBucket(0));
            }
        }

        /** 记录一次总请求 */
        public void addTotal() {
            WindowBucket bucket = currentBucket();
            bucket.totalCount.incrementAndGet();
        }

        /**
         * 记录一次失败
         */
        public void addFailure() {
            WindowBucket bucket = currentBucket();
            bucket.failureCount.incrementAndGet();
        }

        /**
         * 获取当前窗口内的聚合快照
         */
        public WindowSnapshot getSnapshot() {
            // 获取当前时间
            long now = System.currentTimeMillis();
            // 获取窗口开始时间
            long windowStart = now - windowDurationMs;
            long total = 0;
            long failures = 0;

            // 遍历桶，获取窗口内数据
            for (int i = 0; i < bucketCount; i++) {
                WindowBucket bucket = buckets.get(i);
                if (bucket.windowStart > windowStart) {
                    total += bucket.totalCount.get();
                    failures += bucket.failureCount.get();
                }
            }
            return new WindowSnapshot(total, failures);
        }

        /**
         * 状态恢复时调用：重置滑动窗口，清理历史失败次数
         */
        public void reset() {
            for (int i = 0; i < bucketCount; i++) {
                WindowBucket bucket = buckets.get(i);
                if (bucket != null) {
                    bucket.totalCount.set(0);
                    bucket.failureCount.set(0);
                }
            }
        }

        /**
         * 获取当前时间对应的桶
         */
        private WindowBucket currentBucket() {
            // 获取当前时间
            long now = System.currentTimeMillis();
            // 获取当前时间对应桶的起始时间戳
            long bucketStart = now -
                    (now % bucketDurationMs); // 计算当前时间在一个桶内的偏移量
            // 获取当前时间对应的桶索引
            // now / bucketDurationMs：计算从 Unix 纪元到当前时刻一共经历了多少个时间桶（即总桶序号），
            //                         这个数字随时间单调递增，用于标识当前时间落在哪个"桶编号"上。
            //(now / bucketDurationMs) % bucketCount：对总桶序号取模（除以桶总数取余数），得到当前桶在循环数组中的存储位置（0 到 bucketCount-1），
            //                                        使得数组槽位能循环复用，而不必无限扩容。
            int idx = (int) ((now / bucketDurationMs) % bucketCount);

            while (true) {
                WindowBucket bucket = buckets.get(idx);

                if (bucket.windowStart == bucketStart) {
                    return bucket; // 命中当前桶
                }

                if (bucket.windowStart < bucketStart) {
                    // 桶已过期，尝试使用 CAS 无锁重置
                    WindowBucket newBucket = new WindowBucket(bucketStart);
                    // CAS 操作：如果 buckets[idx] 的值还是旧的 bucket，就替换为 newBucket
                    if (buckets.compareAndSet(idx, bucket, newBucket)) {
                        return newBucket; // CAS 成功，返回新桶
                    }
                    // CAS 失败说明其他线程已经抢先更新了该桶，继续 while 循环重试（自旋）
                    // 不会永久自旋，每次的下一次要么直接命中当前桶，要么再争抢，等其他线程设置完成了，就会直接命中桶
                } else {
                    // 时钟回拨，即now 突然变小，导致计算出的 bucketStart 比当前数组中已有的桶的起始时间还要小，
                    // 直接返回（兜底）
                    return bucket;
                }
            }
        }

        /**
         * 单个时间桶
         */
        private static class WindowBucket {
            final long windowStart;
            final AtomicLong totalCount = new AtomicLong(0);
            final AtomicLong failureCount = new AtomicLong(0);

            WindowBucket(long windowStart) {
                this.windowStart = windowStart;
            }
        }
    }
}