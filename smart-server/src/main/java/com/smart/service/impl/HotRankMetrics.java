package com.smart.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前应用实例的热销榜运行指标
 */
@Component
public class HotRankMetrics {

    // 记录的事件
    private final Counter recorded;
    // 重复的事件
    private final Counter duplicated;
    // 记录失败的事件
    private final Counter recordFailed;
    // 补偿消息发送的事件
    private final Counter compensationSent;
    // 补偿消息发送失败的事件
    private final Counter compensationFailed;
    // 查询成功的事件
    private final Counter querySucceeded;
    // 查询失败的事件
    private final Counter queryFailed;

    /**
     * 构造函数
     *
     * @param meterRegistry 指标注册表
     */
    public HotRankMetrics(MeterRegistry meterRegistry) {
        recorded = meterRegistry.counter("smart.hot.rank.event.recorded");
        duplicated = meterRegistry.counter("smart.hot.rank.event.duplicated");
        recordFailed = meterRegistry.counter("smart.hot.rank.event.failed");
        compensationSent = meterRegistry.counter("smart.hot.rank.compensation.sent");
        compensationFailed = meterRegistry.counter("smart.hot.rank.compensation.failed");
        querySucceeded = meterRegistry.counter("smart.hot.rank.query.succeeded");
        queryFailed = meterRegistry.counter("smart.hot.rank.query.failed");
    }

    public void recorded() {
        recorded.increment();
    }

    public void duplicated() {
        duplicated.increment();
    }

    public void recordFailed() {
        recordFailed.increment();
    }

    public void compensationSent() {
        compensationSent.increment();
    }

    public void compensationFailed() {
        compensationFailed.increment();
    }

    public void querySucceeded() {
        querySucceeded.increment();
    }

    public void queryFailed() {
        queryFailed.increment();
    }

    /**
     * 返回指标快照，明确这些计数仅代表当前应用实例
     */
    public Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("recorded", (long) recorded.count());
        result.put("duplicated", (long) duplicated.count());
        result.put("recordFailed", (long) recordFailed.count());
        result.put("compensationSent", (long) compensationSent.count());
        result.put("compensationFailed", (long) compensationFailed.count());
        result.put("querySucceeded", (long) querySucceeded.count());
        result.put("queryFailed", (long) queryFailed.count());
        return result;
    }
}
