package com.ratelimiter.dataplane. application. metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j. Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimiterMetricsService {

    private final MeterRegistry meterRegistry;

    public RateLimiterMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录限流检查指标 - 修复版本
     */
    public Timer.Sample startRateLimitCheck() {
        // 简单计数，不带标签
        meterRegistry.counter("rate_limit_check_total").increment();
        return Timer.start(meterRegistry);
    }

    /**
     * 完成限流检查并记录结果 - 修复版本
     */
    public void finishRateLimitCheck(Timer.Sample sample, boolean allowed, String reason,
                                     String processPath, String tenantId, String resourceKey) {

        // 1. 记录总耗时 - 使用固定名称的 Timer
        Timer timer = Timer.builder("rate_limit_check_duration_seconds")
                .description("Rate limit check duration")
                .register(meterRegistry);
        sample.stop(timer);

        // 2. 记录结果 - 分别计数
        if (allowed) {
            meterRegistry. counter("rate_limit_allowed_total",
                    "tenant_id", tenantId,
                    "process_path", processPath).increment();
        } else {
            meterRegistry.counter("rate_limit_denied_total",
                    "tenant_id", tenantId,
                    "reason", reason,
                    "process_path", processPath).increment();
        }

        // 3. 记录处理路径
        meterRegistry. counter("rate_limit_path_total",
                "tenant_id", tenantId,
                "path", processPath).increment();
    }

    /**
     * 记录策略缓存命中
     */
    public void recordPolicyHit(String tenantId, String resourceKey) {
        meterRegistry.counter("rate_limit_policy_hit_total",
                "tenant_id", tenantId).increment();
    }

    /**
     * 记录策略未找到
     */
    public void recordPolicyNotFound(String tenantId, String resourceKey) {
        meterRegistry.counter("rate_limit_policy_not_found_total",
                "tenant_id", tenantId).increment();
    }

    /**
     * 记录事件发布成功
     */
    public void recordEventPublished(String tenantId, String resourceKey) {
        meterRegistry.counter("quota_event_published_total",
                "tenant_id", tenantId).increment();
    }

    /**
     * 记录事件发布失败
     */
    public void recordEventPublishFailed(String tenantId, String resourceKey, String errorType) {
        meterRegistry.counter("quota_event_publish_failed_total",
                "tenant_id", tenantId,
                "error_type", errorType).increment();
    }

    /**
     * 记录策略缓存大小 - 修复 Gauge 用法
     */
    public void recordPolicyCacheSize(int size) {
        // 使用 AtomicInteger 或者直接设置值
        meterRegistry.gauge("rate_limit_policy_cache_size", size);
    }

    /**
     * 记录限流延迟
     */
    public void recordRateLimitLatency(long durationMs, String tenantId, String resourceKey, String processPath) {
        Timer.builder("rate_limit_latency_ms")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}