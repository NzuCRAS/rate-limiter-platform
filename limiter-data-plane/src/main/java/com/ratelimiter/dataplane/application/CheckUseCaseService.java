package com.ratelimiter.dataplane.application;

import com.ratelimiter.common.web.domain.event.QuotaConsumedEvent;
import com.ratelimiter.common.web.dto.dataPlane.CheckRequest;
import com.ratelimiter.common.web.dto.dataPlane.CheckResponse;
import com.ratelimiter.common.web.dto.dataPlane.PolicyDto;
import com.ratelimiter.dataplane.application.event.QuotaEventPublisher;
import com.ratelimiter.dataplane.application.metrics.RateLimiterMetricsService;
import com.ratelimiter.dataplane.domain.LocalTokenBucketManager;
import com.ratelimiter.dataplane.domain.PolicyCache;
import com.ratelimiter.dataplane.infrastructure.persistence.redis.RedisRateLimiterRepository;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class CheckUseCaseService implements CheckUseCase {

    private final LocalTokenBucketManager localBucketManager;
    private final PolicyCache policyCache;
    private final RedisRateLimiterRepository redisRepository;
    private final QuotaEventPublisher eventPublisher; // 新增
    private final RateLimiterMetricsService metricsService; // 新增

    public CheckUseCaseService(LocalTokenBucketManager localBucketManager,
                               PolicyCache policyCache,
                               RedisRateLimiterRepository redisRepository,
                               QuotaEventPublisher eventPublisher,
                               RateLimiterMetricsService metricsService) {
        this.localBucketManager = localBucketManager;
        this.policyCache = policyCache;
        this.redisRepository = redisRepository;
        this.eventPublisher = eventPublisher;
        this.metricsService = metricsService;
    }

    @Override
    public CheckResponse checkAndConsume(CheckRequest request) {
        // 开始计时
        Timer.Sample sample = metricsService.startRateLimitCheck();

        long now = Instant.now().toEpochMilli();
        String processPath = "unknown";
        String traceId = org.slf4j.MDC.get("traceId");

        try {
            // 1. 查策略
            PolicyDto policy = policyCache.findPolicy(request.getTenantId(), request.getResourceKey());

            if (policy == null) {
                processPath = "policy_not_found";
                metricsService.recordPolicyNotFound(request.getTenantId(), request.getResourceKey());

                CheckResponse response = buildDeniedResponse(request, "policy_not_found", 0L, null, now);
                publishEventWithMetrics(request, response, null, traceId, processPath);

                // 记录指标
                metricsService.finishRateLimitCheck(sample, false, "policy_not_found", processPath,
                        request. getTenantId(), request.getResourceKey());
                return response;
            }

            // 记录策略命中
            metricsService.recordPolicyHit(request.getTenantId(), request.getResourceKey());

            long tokensToConsume = request.getTokens() == null ? 1L : request.getTokens();

            // 2. 本地 token bucket (fast path)
            boolean localAllowed = localBucketManager. tryConsume(
                    request.getTenantId(),
                    request.getResourceKey(),
                    policy.getCapacity(),
                    policy.getRefillRate(),
                    tokensToConsume,
                    now
            );

            if (localAllowed) {
                processPath = "local";
                long remaining = localBucketManager. estimateRemaining(
                        request.getTenantId(), request.getResourceKey());
                CheckResponse response = buildAllowedResponse(request, remaining, policy.getVersion(), now);
                publishEventWithMetrics(request, response, policy, traceId, processPath);

                // 记录指标
                metricsService. finishRateLimitCheck(sample, true, "", processPath,
                        request.getTenantId(), request.getResourceKey());
                return response;
            }

            // 3. Redis fallback (slow path)
            processPath = "redis";
            RedisRateLimiterRepository.RateLimitResult redisResult = redisRepository.tryConsumeTokens(
                    request.getTenantId(),
                    request. getResourceKey(),
                    policy.getCapacity(),
                    policy.getRefillRate(),
                    tokensToConsume,
                    request.getRequestId(),
                    now
            );

            CheckResponse response;
            if (redisResult.allowed()) {
                response = buildAllowedResponse(request, redisResult.remaining(), policy.getVersion(), now);
            } else {
                response = buildDeniedResponse(request, redisResult.reason(), redisResult.remaining(), policy.getVersion(), now);
            }

            publishEventWithMetrics(request, response, policy, traceId, processPath);

            // 记录指标
            metricsService.finishRateLimitCheck(sample, redisResult.allowed(), redisResult.reason(), processPath,
                    request.getTenantId(), request.getResourceKey());
            return response;

        } catch (Exception e) {
            processPath = "error";
            log.error("Error in checkAndConsume.  RequestId: {}, TenantId: {}",
                    request. getRequestId(), request.getTenantId(), e);

            CheckResponse response = buildDeniedResponse(request, "internal_error", 0L, null, now);
            publishEventWithMetrics(request, response, null, traceId, processPath);

            // 记录错误指标
            metricsService. finishRateLimitCheck(sample, false, "internal_error", processPath,
                    request.getTenantId(), request.getResourceKey());
            return response;
        }
    }

    /**
     * 发布事件并记录相关指标
     */
    private void publishEventWithMetrics(CheckRequest request,
                                         CheckResponse response,
                                         PolicyDto policy,
                                         String traceId,
                                         String processPath) {
        try {
            QuotaConsumedEvent event = QuotaConsumedEvent.create(
                    request. getRequestId(),
                    request.getTenantId(),
                    request.getResourceKey(),
                    request.getTokens() == null ? 1L : request.getTokens(),
                    response.isAllowed(),
                    response.getReason(),
                    response. getPolicyVersion(),
                    response.getRemaining(),
                    traceId
            );

            event.setProcessPath(processPath);
            eventPublisher.publishQuotaEvent(event);

            // 记录事件发布成功
            metricsService. recordEventPublished(request.getTenantId(), request.getResourceKey());

        } catch (Exception e) {
            log.warn("Failed to publish quota event.  RequestId: {}", request.getRequestId(), e);
            // 记录事件发布失败
            metricsService.recordEventPublishFailed(request. getTenantId(), request.getResourceKey(), e.getClass().getSimpleName());
        }
    }

    // 新增：构建允许的响应
    private CheckResponse buildAllowedResponse(CheckRequest request,
                                               long remaining,
                                               String policyVersion,
                                               long timestamp) {
        CheckResponse resp = new CheckResponse();
        resp.setAllowed(true);
        resp.setRemaining(remaining);
        resp.setPolicyVersion(policyVersion);
        resp.setReason("");
        resp.setTenantId(request.getTenantId());
        resp.setResourceKey(request.getResourceKey());
        resp.setRequestId(request.getRequestId());
        resp.setTimestamp(timestamp);
        return resp;
    }

    // 新增：构建拒绝的响应
    private CheckResponse buildDeniedResponse(CheckRequest request,
                                              String reason,
                                              long remaining,
                                              String policyVersion,
                                              long timestamp) {
        CheckResponse resp = new CheckResponse();
        resp.setAllowed(false);
        resp.setRemaining(remaining);
        resp.setPolicyVersion(policyVersion);
        resp.setReason(reason);
        resp.setTenantId(request.getTenantId());
        resp.setResourceKey(request.getResourceKey());
        resp.setRequestId(request.getRequestId());
        resp.setTimestamp(timestamp);
        return resp;
    }
}