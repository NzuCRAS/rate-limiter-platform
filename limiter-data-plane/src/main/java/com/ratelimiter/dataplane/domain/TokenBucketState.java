package com.ratelimiter.dataplane.domain;

import com.ratelimiter.common.util.PrecisionUtils;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TokenBucketState {
    private long capacity;
    private BigDecimal refillRateConfig;    // 原始配置（精确）
    private double refillRateCache;         // 缓存的 double 值（计算用）
    private double tokens;
    private long lastRefillTimestamp;

    public TokenBucketState(long capacity, BigDecimal refillRate, long initialTokens, long nowMillis) {
        this.capacity = capacity;
        this.refillRateConfig = refillRate;
        this. refillRateCache = PrecisionUtils.toDouble(refillRate);  // 缓存 double 值
        this. tokens = Math.min(initialTokens, capacity);
        this.lastRefillTimestamp = nowMillis;
    }

    // 提供便捷的 getter
    public double getRefillRate() {
        return refillRateCache;
    }
}