package com.ratelimiter.dataplane.application;

import com.ratelimiter.common.web.dto.dataPlane.CheckRequest;
import com.ratelimiter.common.web.dto.dataPlane.CheckResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class CheckUseCaseServiceTest {

    @Autowired
    private CheckUseCase checkUseCase;

    private CheckRequest buildRequest(String requestId, String tenantId, String resourceKey, Long tokens) {
        CheckRequest req = new CheckRequest();
        req.setRequestId(requestId);
        req.setTenantId(tenantId);
        req.setResourceKey(resourceKey);
        req.setTokens(tokens);
        req.setTimestamp(System.currentTimeMillis());
        return req;
    }

    // 测试在容量范围内的允许行为
    @Test
    void shouldAllowRequestsWithinCapacity() {
        // tenant_001 的策略是 capacity=1000, refillRate=10. 0
        String tenantId = "tenant_001";
        String resourceKey = "/api/v1/orders";

        // 连续请求 10 次，每次 1 token，应该都成功
        for (int i = 0; i < 10; i++) {
            CheckRequest req = buildRequest("req-" + i, tenantId, resourceKey, 1L);
            CheckResponse resp = checkUseCase.checkAndConsume(req);

            assertThat(resp.isAllowed()).isTrue();
            assertThat(resp.getReason()).isEmpty();
            assertThat(resp.getPolicyVersion()).isEqualTo("v1");

            // 剩余应该在递减
            System.out.println("Request " + i + ": remaining=" + resp.getRemaining() + ", allowed=" + resp.isAllowed());
        }
    }

    // 测试超出容量时的拒绝行为
    @Test
    void shouldDenyWhenCapacityExceeded() {
        String tenantId = "tenant_001";
        String resourceKey = "/api/v1/orders";

        // 先消耗大部分 tokens（capacity=1000，消耗 995）
        CheckRequest bigRequest = buildRequest("big-req", tenantId, resourceKey, 995L);
        CheckResponse bigResp = checkUseCase.checkAndConsume(bigRequest);
        assertThat(bigResp.isAllowed()).isTrue();
        System.out.println(bigRequest);
        System.out.println(bigResp);
        System.out.println("After big request: remaining=" + bigResp. getRemaining());

        // 再请求 10 tokens，应该被拒绝（只剩 5 tokens）
        CheckRequest smallRequest = buildRequest("small-req", tenantId, resourceKey, 10L);
        CheckResponse smallResp = checkUseCase. checkAndConsume(smallRequest);

        assertThat(smallResp.isAllowed()).isFalse();
        assertThat(smallResp.getReason()).isEqualTo("quota_exceeded");
        System.out.println(smallRequest);
        System.out.println(smallResp);
        System.out.println("Small request denied: remaining=" + smallResp.getRemaining());
    }

    // 测试令牌随时间补充的行为
    @Test
    void shouldRefillTokensOverTime() throws InterruptedException {
        String tenantId = "tenant_001";
        String resourceKey = "/api/v1/orders";

        // 先消耗一些 tokens
        CheckRequest req1 = buildRequest("req-1", tenantId, resourceKey, 100L);
        CheckResponse resp1 = checkUseCase. checkAndConsume(req1);
        assertThat(resp1.isAllowed()).isTrue();
        long remainingAfterConsume = resp1.getRemaining();
        System.out.println("After consuming 100 tokens:  remaining=" + remainingAfterConsume);

        // 等待 1 秒（refillRate=10.0，应该补充约 10 tokens）
        Thread.sleep(1000);

        // 再次查询，应该看到 tokens 有所增加
        CheckRequest req2 = buildRequest("req-2", tenantId, resourceKey, 1L);
        CheckResponse resp2 = checkUseCase. checkAndConsume(req2);

        System.out.println("After 1 second: remaining=" + resp2.getRemaining());
        // 应该大约比之前多了 10 tokens（减去刚消耗的 1 个）
        assertThat(resp2.getRemaining()).isGreaterThan(remainingAfterConsume);
    }

    // 测试没有找到相应策略时的行为
    @Test
    void shouldDenyWhenPolicyNotFound() {
        CheckRequest req = buildRequest("req-1", "unknown_tenant", "/api/unknown", 1L);
        CheckResponse resp = checkUseCase.checkAndConsume(req);

        System.out.println(req);
        System.out.println(resp);

        assertThat(resp.isAllowed()).isFalse();
        assertThat(resp.getReason()).isEqualTo("policy_not_found");
        assertThat(resp. getPolicyVersion()).isNull();
        assertThat(resp. getRemaining()).isEqualTo(0L);
    }

    // 测试并发请求的行为
    @Test
    void shouldHandleConcurrentRequests() throws InterruptedException {
        String tenantId = "tenant_001";
        String resourceKey = "/api/v1/orders";

        // 并发 50 个线程，每个消耗 1 token
        int threadCount = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待信号一起开始

                    CheckRequest req = buildRequest("concurrent-" + finalI, tenantId, resourceKey, 1L);
                    CheckResponse resp = checkUseCase.checkAndConsume(req);

                    if (resp.isAllowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        deniedCount.incrementAndGet();
                    }

                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 启动所有线程
        doneLatch.await(); // 等待所有线程完成

        System.out. println("Concurrent test results:  allowed=" + allowedCount.get() + ", denied=" + deniedCount.get());

        // 因为初始 capacity=1000，50 个请求都应该被允许
        // （但如果之前的测试消耗了一些，可能会有部分被拒绝）
        assertThat(allowedCount.get()).isGreaterThan(0);
        assertThat(allowedCount.get() + deniedCount. get()).isEqualTo(threadCount);
    }
}
