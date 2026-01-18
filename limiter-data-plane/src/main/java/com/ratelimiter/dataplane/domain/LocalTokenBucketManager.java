package com.ratelimiter.dataplane.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalTokenBucketManager {

    private final Map<String, TokenBucketState> buckets = new ConcurrentHashMap<>();

    // 统一对所有方法加锁，保证一致性
    public synchronized boolean tryConsume(String tenantId,
                                           String resourceKey,
                                           long capacity,
                                           double refillRate,
                                           long tokensToConsume,
                                           long nowMillis) {

        String key = tenantId + "|" + resourceKey;
        TokenBucketState state = buckets.get(key);

        if (state == null) {
            // 首次创建 bucket，给满容量
            state = new TokenBucketState(capacity, refillRate, capacity, nowMillis);
            buckets.put(key, state);
        } else {
            // 先根据时间补充 token
            refillTokens(state, nowMillis);
        }

        if (state.getTokens() >= tokensToConsume) {
            state.setTokens(state.getTokens() - tokensToConsume);
            return true;
        } else {
            return false;
        }
    }

    // 这个方法也需要同步保护
    public synchronized long estimateRemaining(String tenantId, String resourceKey) {
        String key = tenantId + "|" + resourceKey;
        TokenBucketState state = buckets.get(key);
        if (state == null) {
            return 0L;
        }

        // 在返回前也要先 refill，确保返回最新状态
        refillTokens(state, System.currentTimeMillis());
        return (long) Math.floor(state.getTokens());
    }

    // 抽取 refill 逻辑，避免重复
    private void refillTokens(TokenBucketState state, long nowMillis) {
        long elapsedMillis = nowMillis - state.getLastRefillTimestamp();

        if (elapsedMillis > 0) {
            double deltaTokens = (elapsedMillis / 1000.0) * state.getRefillRate();
            double newTokens = Math.min(state.getCapacity(), state.getTokens() + deltaTokens);
            state.setTokens(newTokens);
            state.setLastRefillTimestamp(nowMillis);
        }
        // elapsedMillis <= 0 时不做任何操作（时间没变化或倒退）
    }
}