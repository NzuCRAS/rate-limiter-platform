package com.ratelimiter.dataplane.infrastructure.persistence.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisRateLimiterRepositoryTest {

    @Autowired
    private RedisRateLimiterRepository redisRepository;

    @Test
    void shouldConnectToRedis() {
        RedisRateLimiterRepository. RateLimitResult result =
                redisRepository.tryConsumeTokens(
                        "test_tenant",
                        "/test/api",
                        100L,
                        BigDecimal.valueOf(10.00),
                        1L,
                        "test-request-" + System.currentTimeMillis(),
                        System.currentTimeMillis()
                );

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(99L);
        System.out.println("Redis test passed:  " + result.allowed() + ", remaining=" + result.remaining());
    }
}