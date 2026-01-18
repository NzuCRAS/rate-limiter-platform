package com.ratelimiter.dataplane.application;

import com.ratelimiter.common.web.dto.dataPlane.CheckRequest;
import com.ratelimiter.common.web.dto.dataPlane.CheckResponse;
import com.ratelimiter.dataplane.domain.InMemoryPolicyStore;
import com.ratelimiter.dataplane.domain.LocalTokenBucketManager;
import com.ratelimiter.dataplane.infrastructure.persistence.redis.RedisRateLimiterRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CheckUseCaseService implements CheckUseCase {

    private final LocalTokenBucketManager localBucketManager;
    private final InMemoryPolicyStore policyStore;
    private final RedisRateLimiterRepository redisRepository; // 新增

    public CheckUseCaseService(LocalTokenBucketManager localBucketManager,
                               InMemoryPolicyStore policyStore,
                               RedisRateLimiterRepository redisRepository) {
        this.localBucketManager = localBucketManager;
        this.policyStore = policyStore;
        this.redisRepository = redisRepository;
    }

    @Override
    public CheckResponse checkAndConsume(CheckRequest request) {
        long now = Instant.now().toEpochMilli();

        // 1. 查策略
        InMemoryPolicyStore.SimplePolicy policy =
                policyStore.findPolicy(request.getTenantId(), request.getResourceKey());

        if (policy == null) {
            return buildDeniedResponse(request, "policy_not_found", 0L, null, now);
        }

        long tokensToConsume = request.getTokens() == null ? 1L : request.getTokens();

        // 2. 先尝试本地 token bucket (fast path)
        boolean localAllowed = localBucketManager. tryConsume(
                request. getTenantId(),
                request.getResourceKey(),
                policy.capacity,
                policy.refillRate,
                tokensToConsume,
                now
        );

        if (localAllowed) {
            // 本地成功，直接返回允许
            long remaining = localBucketManager. estimateRemaining(
                    request.getTenantId(), request.getResourceKey());
            return buildAllowedResponse(request, remaining, policy.version, now);
        }

        // 3. 本地不足，走 Redis 全局检查 (slow path)
        RedisRateLimiterRepository. RateLimitResult redisResult = redisRepository.tryConsumeTokens(
                request.getTenantId(),
                request.getResourceKey(),
                policy.capacity,
                policy.refillRate,
                tokensToConsume,
                request.getRequestId(),
                now
        );

        if (redisResult.allowed()) {
            return buildAllowedResponse(request, redisResult.remaining(), policy. version, now);
        } else {
            return buildDeniedResponse(request, redisResult.reason(), redisResult.remaining(), policy.version, now);
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