package com.ratelimiter.dataplane.application.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimiterMetricsService {

    private final MeterRegistry meterRegistry;

    // 不再需要预先定义Counter成员变量

    public RateLimiterMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录限流检查指标
     */
    public Timer.Sample startRateLimitCheck() {
        // 使用meterRegistry直接记录指标
        meterRegistry.counter("rate_limit_check_total").increment();
        return Timer.start(meterRegistry);
    }

    /**
     * 完成限流检查并记录结果
     */
    public void finishRateLimitCheck(Timer.Sample sample, boolean allowed, String reason, String processPath,
                                     String tenantId, String resourceKey) {
        // 记录总耗时
        sample.stop(Timer.builder("rate_limit_check_duration_seconds")
                .tags(Tags.of(
                        "tenant_id", tenantId,
                        "resource_key", resourceKey,
                        "process_path", processPath,
                        "allowed", String.valueOf(allowed)
                ))
                .register(meterRegistry));

        // 记录结果
        if (allowed) {
            meterRegistry.counter("rate_limit_allowed_total",
                    Tags.of(
                            "tenant_id", tenantId,
                            "resource_key", resourceKey,
                            "process_path", processPath
                    )).increment();
        } else {
            meterRegistry.counter("rate_limit_denied_total",
                    Tags.of(
                            "tenant_id", tenantId,
                            "resource_key", resourceKey,
                            "reason", reason,
                            "process_path", processPath
                    )).increment();
        }

        // 记录处理路径
        if ("local".equals(processPath)) {
            meterRegistry.counter("rate_limit_local_path_total",
                    Tags.of(
                            "tenant_id", tenantId,
                            "resource_key", resourceKey
                    )).increment();
        } else if ("redis".equals(processPath)) {
            meterRegistry.counter("rate_limit_redis_path_total",
                    Tags.of(
                            "tenant_id", tenantId,
                            "resource_key", resourceKey
                    )).increment();
        }
    }

    /**
     * 记录策略缓存命中
     */
    public void recordPolicyHit(String tenantId, String resourceKey) {
        meterRegistry.counter("rate_limit_policy_hit_total",
                Tags.of(
                        "tenant_id", tenantId,
                        "resource_key", resourceKey
                )).increment();
    }

    /**
     * 记录策略未找到
     */
    public void recordPolicyNotFound(String tenantId, String resourceKey) {
        meterRegistry.counter("rate_limit_policy_not_found_total",
                Tags.of(
                        "tenant_id", tenantId,
                        "resource_key", resourceKey
                )).increment();
    }

    /**
     * 记录事件发布成功
     */
    public void recordEventPublished(String tenantId, String resourceKey) {
        meterRegistry.counter("quota_event_published_total",
                Tags.of(
                        "tenant_id", tenantId,
                        "resource_key", resourceKey
                )).increment();
    }

    /**
     * 记录事件发布失败
     */
    public void recordEventPublishFailed(String tenantId, String resourceKey, String errorType) {
        meterRegistry.counter("quota_event_publish_failed_total",
                Tags.of(
                        "tenant_id", tenantId,
                        "resource_key", resourceKey,
                        "error_type", errorType
                )).increment();
    }

    /**
     * 记录策略缓存大小（Gauge 指标）
     */
    public void recordPolicyCacheSize(int size) {
        // 使用gauge记录缓存大小
        meterRegistry.gauge("rate_limit_policy_cache_size",
                Tags.empty(), // 可以添加标签
                size);
    }

    /**
     * 记录限流延迟（替代原来的Timer用法）
     */
    public void recordRateLimitLatency(long durationMs, String tenantId, String resourceKey, String processPath) {
        meterRegistry.timer("rate_limit_latency_ms",
                Tags.of(
                        "tenant_id", tenantId,
                        "resource_key", resourceKey,
                        "process_path", processPath
                )).record(durationMs, TimeUnit.MILLISECONDS);
    }
}