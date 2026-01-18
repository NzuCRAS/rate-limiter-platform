package com.ratelimiter.controlplane.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ratelimiter.controlplane.infrastructure.persistence.mysql.PolicyEntity;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional  // 测试后回滚
public class PolicyMapperTest {

    @Autowired
    private PolicyMapper policyMapper;

    @Test
    void testInsertAndSelect() {
        // 1. 插入
        PolicyEntity policy = new PolicyEntity();
        policy.setTenantId("test_tenant");
        policy.setResourceKey("/api/test");
        policy.setPolicyType("TOKEN_BUCKET");
        policy.setWindowSeconds(60);
        policy.setCapacity(1000L);
        policy.setRefillRate(new BigDecimal("16.67"));
        policy.setPriority(0);
        policy.setEnabled(true);
        policy.setVersion("v1");

        int result = policyMapper.insert(policy);
        assertThat(result).isEqualTo(1);
        assertThat(policy.getId()).isNotNull();  // 自增 ID 已回填

        // 2. 查询
        PolicyEntity found = policyMapper.selectById(policy.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTenantId()).isEqualTo("test_tenant");
        assertThat(found.getCreatedAt()).isNotNull();  // 自动填充
    }

    @Test
    void testQueryByTenantAndResource() {
        // 插入测试数据
        PolicyEntity policy = new PolicyEntity();
        policy.setTenantId("tenant_a");
        policy.setResourceKey("/api/orders");
        policy.setPolicyType("TOKEN_BUCKET");
        policy.setWindowSeconds(60);
        policy.setCapacity(500L);
        policy.setRefillRate(new BigDecimal("8.33"));
        policy.setEnabled(true);
        policy.setVersion("v1");
        policyMapper.insert(policy);

        // 查询
        PolicyEntity found = policyMapper.selectOne(new LambdaQueryWrapper<PolicyEntity>()
                .eq(PolicyEntity::getTenantId, "tenant_a")
                .eq(PolicyEntity::getResourceKey, "/api/orders"));

        assertThat(found).isNotNull();
        assertThat(found.getCapacity()).isEqualTo(500L);
    }

    @Test
    void testUpdate() {
        // 插入
        PolicyEntity policy = new PolicyEntity();
        policy.setTenantId("tenant_b");
        policy.setResourceKey("/api/payments");
        policy.setPolicyType("TOKEN_BUCKET");
        policy.setWindowSeconds(60);
        policy.setCapacity(100L);
        policy.setRefillRate(new BigDecimal("1.67"));
        policy.setEnabled(true);
        policy.setVersion("v1");
        policyMapper.insert(policy);

        // 更新
        policy.setCapacity(200L);
        int result = policyMapper.updateById(policy);
        assertThat(result).isEqualTo(1);

        // 验证
        PolicyEntity updated = policyMapper.selectById(policy.getId());
        assertThat(updated. getCapacity()).isEqualTo(200L);
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());  // 更新时间已变
    }
}