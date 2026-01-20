package com. ratelimiter. dataplane.domain;

import org.springframework.stereotype.Component;

import java. math.BigDecimal;
import java. util.Map;
import java. util.concurrent.ConcurrentHashMap;

@Component
public class LocalTokenBucketManager {

    private final Map<String, TokenBucketState> buckets = new ConcurrentHashMap<>();

    public synchronized boolean tryConsume(String tenantId,
                                           String resourceKey,
                                           long capacity,
                                           BigDecimal refillRateConfig,  // 外部传入精确值
                                           long tokensToConsume,
                                           long nowMillis) {

        String key = tenantId + "|" + resourceKey;
        TokenBucketState state = buckets.get(key);

        if (state == null) {
            state = new TokenBucketState(capacity, refillRateConfig, capacity, nowMillis);
            buckets. put(key, state);
        } else {
            refillTokens(state, nowMillis);
        }

        if (state.getTokens() >= tokensToConsume) {
            state.setTokens(state.getTokens() - tokensToConsume);
            return true;
        } else {
            return false;
        }
    }

    public synchronized long estimateRemaining(String tenantId, String resourceKey) {
        String key = tenantId + "|" + resourceKey;
        TokenBucketState state = buckets.get(key);
        if (state == null) {
            return 0L;
        }

        refillTokens(state, System.currentTimeMillis());
        return (long) Math.floor(state.getTokens());
    }

    private void refillTokens(TokenBucketState state, long nowMillis) {
        long elapsedMillis = nowMillis - state.getLastRefillTimestamp();

        if (elapsedMillis > 0) {
            // 使用缓存的 double 值进行高效计算
            double timeElapsedSeconds = elapsedMillis / 1000.0;
            double deltaTokens = state.getRefillRate() * timeElapsedSeconds;

            double newTokens = Math.min(state. getCapacity(), state.getTokens() + deltaTokens);
            state.setTokens(newTokens);
            state.setLastRefillTimestamp(nowMillis);
        }
    }
}