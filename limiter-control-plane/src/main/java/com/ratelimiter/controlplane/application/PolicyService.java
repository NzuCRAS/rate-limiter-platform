package com.ratelimiter.controlplane.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service. impl.ServiceImpl;

import com.ratelimiter.common.web.constant.ErrorCode;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyRequest;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyResponse;
import com.ratelimiter.common.web.dto.controlPlane.GetPolicyResponse;
import com.ratelimiter.common.web.exception.BusinessException;
import com.ratelimiter.controlplane.infrastructure.persistence.mapper.PolicyMapper;
import com.ratelimiter.controlplane.infrastructure.persistence.mysql.PolicyEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 策略服务
 * 继承 ServiceImpl 获得更多便捷方法
 */
@Service
public class PolicyService extends ServiceImpl<PolicyMapper, PolicyEntity> {

    /**
     * 根据租户和资源查询策略
     */
    public PolicyEntity getByTenantAndResource(String tenantId, String resourceKey) {
        return this.getOne(new LambdaQueryWrapper<PolicyEntity>()
                .eq(PolicyEntity::getTenantId, tenantId)
                .eq(PolicyEntity::getResourceKey, resourceKey));
    }

    /**
     * 查询租户的所有启用策略
     */
    public List<PolicyEntity> listEnabledByTenant(String tenantId) {
        return this.list(new LambdaQueryWrapper<PolicyEntity>()
                .eq(PolicyEntity::getTenantId, tenantId)
                .eq(PolicyEntity::getEnabled, true));
    }

    /**
     * 查询所有启用的策略（用于下发到 Data Plane）
     */
    public List<PolicyEntity> listAllEnabled() {
        return this.list(new LambdaQueryWrapper<PolicyEntity>()
                .eq(PolicyEntity::getEnabled, true));
    }

    /**
     * 创建策略（带业务校验）
     */
    public CreatePolicyResponse createPolicy(CreatePolicyRequest request) {
        // 1. 校验是否已存在
        PolicyEntity existing = getByTenantAndResource(request.getTenantId(), request.getResourceKey());
        if (existing != null) {
            throw new BusinessException(ErrorCode.POLICY_ALREADY_EXISTS,
                    "Policy already exists for this tenant and resource",
                    Map.of("tenantId", request.getTenantId(), "resourceKey", request.getResourceKey()));
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
        boolean saved = this.save(policyEntity);
        if (!saved) {
            // 抛出业务异常
            throw new BusinessException(ErrorCode.POLICY_PERSISTENCE_ERROR,
                    "Policy creating failed",
                    Map.of("tenantId", request.getTenantId(),
                            "resourceKey", request.getResourceKey(),
                            "policyId", policyEntity.getId()));
        }

        // 4. entity
        return new CreatePolicyResponse(
                policyEntity.getId(),
                policyEntity.getTenantId(),
                policyEntity.getResourceKey(),
                policyEntity.getPolicyType(),
                policyEntity.getWindowSeconds(),
                policyEntity.getCapacity(),
                policyEntity.getRefillRate(),
                policyEntity.getBurstCapacity(),
                policyEntity.getPriority(),
                policyEntity.getEnabled(),
                policyEntity.getVersion(),
                policyEntity.getMetadata(),
                policyEntity.getDescription(),
                policyEntity.getCreatedAt(),
                policyEntity.getUpdatedAt()
        );
    }


    public GetPolicyResponse getPolicyById(Long id) {
        PolicyEntity existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.POLICY_NOT_FOUND,
                    "Policy doesn't exist",
                    Map.of("policyId", id));
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
    }

    /**
     * 删除策略（带业务检验）
     */
    public boolean deletePolicy(Long id) {
        PolicyEntity existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.POLICY_NOT_FOUND,
                    "Policy doesn't exist",
                    Map.of("policyId", id));
        }
        return this.removeById(id);
    }

    /**
     * 更新策略
     */
    public boolean updatePolicy(PolicyEntity policy) {
        // 业务校验...
        return this.updateById(policy);
    }

    /**
     * 查询策略列表
     */
    public List<GetPolicyResponse> listPolicies(String tenantId, Boolean enabledOnly) {
        LambdaQueryWrapper<PolicyEntity> query = new LambdaQueryWrapper<>();

        if (tenantId != null && !tenantId. isBlank()) {
            query.eq(PolicyEntity:: getTenantId, tenantId);
        }

        if (enabledOnly != null && enabledOnly) {
            query.eq(PolicyEntity::getEnabled, true);
        }

        List<PolicyEntity> entities = this.list(query);
        return entities.stream()
                .map(this:: toGetPolicyResponse)
                .toList();
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
                entity. getTenantId(),
                entity.getResourceKey(),
                entity. getPolicyType(),
                entity. getWindowSeconds(),
                entity. getCapacity(),
                entity.getRefillRate(),
                entity. getBurstCapacity(),
                entity.getPriority(),
                entity.getEnabled(),
                entity.getVersion(),
                entity. getMetadata(),
                entity.getDescription(),
                entity. getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}