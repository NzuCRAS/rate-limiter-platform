package com.ratelimiter. dataplane.application;

import com.ratelimiter. common.web.dto.dataPlane.PolicyDto;
import com.ratelimiter. dataplane.domain. PolicyCache;
import com.ratelimiter.dataplane. infrastructure.integration. ControlPlaneClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org. springframework.stereotype.Service;

import java. util.List;

@Slf4j
@Service
public class PolicySyncService implements CommandLineRunner {

    private final ControlPlaneClient controlPlaneClient;
    private final PolicyCache policyCache;

    public PolicySyncService(ControlPlaneClient controlPlaneClient, PolicyCache policyCache) {
        this.controlPlaneClient = controlPlaneClient;
        this.policyCache = policyCache;
    }

    /**
     * 启动时同步一次策略
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("Starting initial policy sync...");
        syncPolicies();
    }

    /**
     * 定时同步策略（每30秒）
     */
    @Scheduled(fixedDelay = 30000)
    public void syncPolicies() {
        try {
            log. debug("Starting policy sync from Control Plane...");

            List<PolicyDto> policies = controlPlaneClient.fetchAllEnabledPolicies();
            policyCache.updatePolicies(policies);

            log.debug("Policy sync completed, cached {} policies", policies.size());
        } catch (Exception e) {
            log.error("Policy sync failed", e);
        }
    }
}