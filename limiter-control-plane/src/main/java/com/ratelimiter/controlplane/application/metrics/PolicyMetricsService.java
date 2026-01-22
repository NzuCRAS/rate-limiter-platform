package com. ratelimiter. controlplane.application.metrics;

import io.micrometer.core. instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype. Service;

import java.util.concurrent. TimeUnit;

@Slf4j
@Service
public class PolicyMetricsService {

    private final MeterRegistry meterRegistry;

    public PolicyMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录策略创建指标 - 修复版本
     */
    public Timer.Sample startPolicyCreate(String tenantId) {
        // 使用正确的 counter API
        meterRegistry. counter("policy_create_total", "tenant_id", tenantId).increment();
        return Timer. start(meterRegistry);
    }

    /**
     * 完成策略创建并记录结果 - 修复版本
     */
    public void finishPolicyCreate(Timer.Sample sample, boolean success, String tenantId, String errorType) {
        // 记录创建时长
        Timer timer = Timer.builder("policy_create_duration_seconds")
                .register(meterRegistry);
        sample.stop(timer);

        // 记录创建结果
        meterRegistry.counter("policy_create_result_total",
                "tenant_id", tenantId,
                "success", String.valueOf(success),
                "error_type", errorType != null ? errorType :  "none").increment();
    }

    /**
     * 记录策略查询指标 - 修复版本
     */
    public void recordPolicyQuery(String operation, String tenantId, boolean success, long durationMs) {
        // 记录查询次数
        meterRegistry.counter("policy_query_total",
                "operation", operation,
                "tenant_id", tenantId,
                "success", String.valueOf(success)).increment();

        // 记录查询时长
        Timer.builder("policy_operation_duration_seconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录策略验证失败
     */
    public void recordPolicyValidationFailed(String tenantId, String resourceKey, String reason) {
        meterRegistry.counter("policy_validation_failed_total",
                "tenant_id", tenantId,
                "reason", reason != null ? reason : "unknown").increment();
    }

    /**
     * 记录重复策略创建
     */
    public void recordPolicyDuplicate(String tenantId, String resourceKey) {
        meterRegistry.counter("policy_duplicate_total",
                "tenant_id", tenantId).increment();
    }

    /**
     * 记录数据库操作
     */
    public void recordDbQuery(String operation, boolean success, long durationMs) {
        // 记录数据库查询次数
        meterRegistry.counter("database_query_total",
                "operation", operation,
                "success", String.valueOf(success)).increment();

        if (!success) {
            meterRegistry.counter("database_query_failed_total",
                    "operation", operation).increment();
        }

        // 记录数据库查询时长
        Timer. builder("database_query_duration_seconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录策略更新
     */
    public void recordPolicyUpdate(String tenantId, String resourceKey, boolean success) {
        meterRegistry.counter("policy_update_total",
                "tenant_id", tenantId,
                "success", String.valueOf(success)).increment();
    }

    /**
     * 记录策略删除
     */
    public void recordPolicyDelete(String tenantId, String resourceKey, boolean success) {
        meterRegistry. counter("policy_delete_total",
                "tenant_id", tenantId,
                "success", String.valueOf(success)).increment();
    }

    /**
     * 记录活跃策略数量
     */
    public void recordActivePolicyCount(int count) {
        meterRegistry.gauge("policy_active_count", count);
    }

    /**
     * 记录按租户分组的策略数量
     */
    public void recordPolicyCountByTenant(String tenantId, int count) {
        meterRegistry.gauge("policy_count_by_tenant",
                Tags.of("tenant_id", tenantId), count);
    }

    /**
     * 记录策略操作延迟
     */
    public void recordPolicyOperationLatency(String operation, String tenantId, long durationMs) {
        Timer.builder("policy_operation_latency_ms")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}