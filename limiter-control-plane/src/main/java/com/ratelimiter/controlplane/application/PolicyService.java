package com.ratelimiter.controlplane. application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.ratelimiter. common.web.constant.ErrorCode;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyRequest;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyResponse;
import com.ratelimiter.common.web.dto.controlPlane.GetPolicyResponse;
import com.ratelimiter.common.web.exception.BusinessException;
import com.ratelimiter.controlplane.application. metrics.PolicyMetricsService;
import com.ratelimiter.controlplane.infrastructure.persistence.mapper.PolicyMapper;
import com. ratelimiter.controlplane. infrastructure.persistence.mysql.PolicyEntity;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 策略服务
 * 继承 ServiceImpl 获得更多便捷方法
 */
@Service
@AllArgsConstructor
public class PolicyService extends ServiceImpl<PolicyMapper, PolicyEntity> {

    private final PolicyMetricsService metricsService;

    /**
     * 根据租户和资源查询策略
     */
    public PolicyEntity getByTenantAndResource(String tenantId, String resourceKey) {
        long startTime = System.currentTimeMillis();

        try {
            PolicyEntity result = this.getOne(new LambdaQueryWrapper<PolicyEntity>()
                    .eq(PolicyEntity::getTenantId, tenantId)
                    .eq(PolicyEntity:: getResourceKey, resourceKey));

            long duration = System. currentTimeMillis() - startTime;
            metricsService. recordDbQuery("select_by_tenant_resource", true, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_by_tenant_resource", false, duration);
            throw e;
        }
    }

    /**
     * 查询租户的所有启用策略
     */
    public List<PolicyEntity> listEnabledByTenant(String tenantId) {
        long startTime = System.currentTimeMillis();

        try {
            List<PolicyEntity> result = this.list(new LambdaQueryWrapper<PolicyEntity>()
                    .eq(PolicyEntity::getTenantId, tenantId)
                    .eq(PolicyEntity:: getEnabled, true));

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_enabled_by_tenant", true, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_enabled_by_tenant", false, duration);
            throw e;
        }
    }

    /**
     * 查询所有启用的策略（用于下发到 Data Plane）
     */
    public List<PolicyEntity> listAllEnabled() {
        long startTime = System.currentTimeMillis();

        try {
            List<PolicyEntity> result = this. list(new LambdaQueryWrapper<PolicyEntity>()
                    .eq(PolicyEntity::getEnabled, true));

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_all_enabled", true, duration);

            // 记录当前活跃策略数量
            metricsService.recordActivePolicyCount(result.size());

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_all_enabled", false, duration);
            throw e;
        }
    }

    /**
     * 创建策略（带业务校验）
     */
    public CreatePolicyResponse createPolicy(CreatePolicyRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 校验是否已存在
            PolicyEntity existing = getByTenantAndResource(request.getTenantId(), request.getResourceKey());
            if (existing != null) {
                throw new BusinessException(ErrorCode.POLICY_ALREADY_EXISTS,
                        "Policy already exists for this tenant and resource",
                        Map.of("tenantId", request.getTenantId(), "resourceKey", request. getResourceKey()));
            }

            // 2. request -> entity
            PolicyEntity policyEntity = new PolicyEntity();
            policyEntity.setTenantId(request.getTenantId());
            policyEntity.setResourceKey(request.getResourceKey());
            policyEntity.setPolicyType(request.getPolicyType());
            policyEntity.setWindowSeconds(request.getWindowSeconds());
            policyEntity.setCapacity(request.getCapacity());
            policyEntity.setRefillRate(request.getRefillRate());
            policyEntity.setBurstCapacity(request.getBurstCapacity());
            policyEntity.setPriority(request.getPriority());
            policyEntity.setEnabled(request.getEnabled());
            policyEntity.setVersion(request.getVersion());
            policyEntity.setMetadata(request.getMetadata());
            policyEntity.setDescription(request.getDescription());

            // 3. 保存（MyBatis-Plus 会回填自增id 和自动填充的 createdAt/updatedAt）
            long insertStartTime = System.currentTimeMillis();
            boolean saved = this.save(policyEntity);
            long insertDuration = System.currentTimeMillis() - insertStartTime;

            metricsService.recordDbQuery("policy_insert", saved, insertDuration);

            if (! saved) {
                throw new BusinessException(ErrorCode.POLICY_PERSISTENCE_ERROR,
                        "Policy creating failed",
                        Map.of("tenantId", request.getTenantId(),
                                "resourceKey", request.getResourceKey(),
                                "policyId", policyEntity.getId()));
            }

            // 4. 记录总耗时并返回结果
            long totalDuration = System.currentTimeMillis() - startTime;
            metricsService.recordPolicyOperationLatency("create", request.getTenantId(), totalDuration);

            return new CreatePolicyResponse(
                    policyEntity.getId(),
                    policyEntity.getTenantId(),
                    policyEntity.getResourceKey(),
                    policyEntity.getPolicyType(),
                    policyEntity.getWindowSeconds(),
                    policyEntity.getCapacity(),
                    policyEntity.getRefillRate(),
                    policyEntity.getBurstCapacity(),
                    policyEntity. getPriority(),
                    policyEntity.getEnabled(),
                    policyEntity.getVersion(),
                    policyEntity.getMetadata(),
                    policyEntity.getDescription(),
                    policyEntity.getCreatedAt(),
                    policyEntity.getUpdatedAt()
            );

        } catch (Exception e) {
            long duration = System. currentTimeMillis() - startTime;
            metricsService. recordDbQuery("policy_create_transaction", false, duration);
            throw e;
        }
    }

    public GetPolicyResponse getPolicyById(Long id) {
        long startTime = System.currentTimeMillis();

        try {
            PolicyEntity existing = getById(id);
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_by_id", existing != null, duration);

            if (existing == null) {
                throw new BusinessException(ErrorCode. POLICY_NOT_FOUND,
                        "Policy doesn't exist",
                        Map. of("policyId", id));
            }

            return new GetPolicyResponse(
                    existing.getId(),
                    existing.getTenantId(),
                    existing.getResourceKey(),
                    existing.getPolicyType(),
                    existing.getWindowSeconds(),
                    existing.getCapacity(),
                    existing.getRefillRate(),
                    existing.getBurstCapacity(),
                    existing.getPriority(),
                    existing.getEnabled(),
                    existing.getVersion(),
                    existing.getMetadata(),
                    existing.getDescription(),
                    existing.getCreatedAt(),
                    existing.getUpdatedAt());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("select_by_id", false, duration);
            throw e;
        }
    }

    /**
     * 删除策略（带业务检验）
     */
    public boolean deletePolicy(Long id) {
        long startTime = System.currentTimeMillis();

        try {
            PolicyEntity existing = getById(id);
            if (existing == null) {
                throw new BusinessException(ErrorCode.POLICY_NOT_FOUND,
                        "Policy doesn't exist",
                        Map.of("policyId", id));
            }

            boolean deleted = this.removeById(id);
            long duration = System. currentTimeMillis() - startTime;

            metricsService.recordDbQuery("policy_delete", deleted, duration);
            metricsService.recordPolicyDelete(existing.getTenantId(), existing.getResourceKey(), deleted);

            return deleted;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("policy_delete", false, duration);
            throw e;
        }
    }

    /**
     * 更新策略
     */
    public boolean updatePolicy(PolicyEntity policy) {
        long startTime = System.currentTimeMillis();

        try {
            boolean updated = this.updateById(policy);
            long duration = System.currentTimeMillis() - startTime;

            metricsService.recordDbQuery("policy_update", updated, duration);
            metricsService.recordPolicyUpdate(policy.getTenantId(), policy.getResourceKey(), updated);

            return updated;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("policy_update", false, duration);
            throw e;
        }
    }

    /**
     * 查询策略列表
     */
    public List<GetPolicyResponse> listPolicies(String tenantId, Boolean enabledOnly) {
        long startTime = System.currentTimeMillis();

        try {
            LambdaQueryWrapper<PolicyEntity> query = new LambdaQueryWrapper<>();

            if (tenantId != null && !tenantId.isBlank()) {
                query. eq(PolicyEntity::getTenantId, tenantId);
            }

            if (enabledOnly != null && enabledOnly) {
                query.eq(PolicyEntity::getEnabled, true);
            }

            List<PolicyEntity> entities = this.list(query);
            long duration = System.currentTimeMillis() - startTime;

            String operation = (tenantId != null ?  "list_by_tenant" :  "list_all") +
                    (enabledOnly != null && enabledOnly ? "_enabled" :  "");
            metricsService.recordDbQuery(operation, true, duration);

            // 如果是查询特定租户的策略，记录租户策略数量
            if (tenantId != null) {
                metricsService.recordPolicyCountByTenant(tenantId, entities.size());
            }

            return entities.stream()
                    .map(this:: toGetPolicyResponse)
                    .toList();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordDbQuery("list_policies", false, duration);
            throw e;
        }
    }

    /**
     * 查询所有启用策略
     */
    public List<GetPolicyResponse> listAllEnabledPolicies() {
        return listPolicies(null, true);
    }

    /**
     * Entity -> GetPolicyResponse 转换
     */
    private GetPolicyResponse toGetPolicyResponse(PolicyEntity entity) {
        return new GetPolicyResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getResourceKey(),
                entity.getPolicyType(),
                entity.getWindowSeconds(),
                entity.getCapacity(),
                entity.getRefillRate(),
                entity.getBurstCapacity(),
                entity.getPriority(),
                entity.getEnabled(),
                entity.getVersion(),
                entity.getMetadata(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}