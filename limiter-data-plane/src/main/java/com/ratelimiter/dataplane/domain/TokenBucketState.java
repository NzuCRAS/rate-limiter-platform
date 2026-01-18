package com.ratelimiter.dataplane.domain;

import lombok.Data;

@Data
public class TokenBucketState {

    private long capacity;              // 最大容量
    private double refillRate;          // 每秒补充多少 tokens
    private double tokens;              // 当前 token 数（用 double 支持小数补充）
    private long lastRefillTimestamp;   // 上次补充时间（毫秒）

    public TokenBucketState(long capacity, double refillRate, long initialTokens, long nowMillis) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = Math.min(initialTokens, capacity);
        this.lastRefillTimestamp = nowMillis;
    }
}