package com.ratelimiter.dataplane.infrastructure. persistence. redis;

import com.ratelimiter.common.util.PrecisionUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org. springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class RedisRateLimiterRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    public RedisRateLimiterRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(loadScriptContent(), List.class);
    }

    public RateLimitResult tryConsumeTokens(String tenantId,
                                            String resourceKey,
                                            long capacity,
                                            BigDecimal refillRateConfig,  // 接收精确值
                                            long tokensToConsume,
                                            String requestId,
                                            long nowMillis) {

        String bucketKey = "rate_limiter:" + tenantId + ":" + resourceKey;
        String idempotencyKey = "rate_limiter:idempotent:" + requestId;

        List<String> keys = Arrays. asList(bucketKey, idempotencyKey);
        Object[] args = {
                String.valueOf(capacity),
                String.valueOf(PrecisionUtils.toDouble(refillRateConfig)),  // 转换为 double
                String.valueOf(tokensToConsume),
                String.valueOf(nowMillis),
                requestId,
                "300"
        };

        List result = redisTemplate.execute(tokenBucketScript, keys, args);

        // 解析返回结果
        int allowed = ((Number) result.get(0)).intValue();
        long remaining = ((Number) result.get(1)).longValue();
        String reason = (String) result.get(2);

        return new RateLimitResult(allowed == 1, remaining, reason);
    }

    // 加载 Lua 脚本内容
    private String loadScriptContent() {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/token-bucket.lua");
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load token bucket Lua script", e);
        }
    }

    // 结果封装类
        public record RateLimitResult(boolean allowed, long remaining, String reason) {
    }
}