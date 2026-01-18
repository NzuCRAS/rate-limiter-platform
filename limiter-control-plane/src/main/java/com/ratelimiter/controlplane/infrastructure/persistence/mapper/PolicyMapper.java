package com.ratelimiter.controlplane.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ratelimiter.controlplane.infrastructure.persistence.mysql.PolicyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略 Mapper
 * 继承 BaseMapper 自动获得 CRUD 方法
 */
@Mapper
public interface PolicyMapper extends BaseMapper<PolicyEntity> {

    // BaseMapper 已提供的方法（无需实现）：
    // - insert(entity)
    // - deleteById(id)
    // - updateById(entity)
    // - selectById(id)
    // - selectList(queryWrapper)

    // 你可以添加自定义查询方法：

    /**
     * 根据租户和资源查询策略（业务最常用）
     * 注意：也可以用 QueryWrapper，这里演示自定义方法
     */
    // 方式1：注解SQL
    // @Select("SELECT * FROM policy WHERE tenant_id = #{tenantId} AND resource_key = #{resourceKey}")
    // PolicyEntity selectByTenantAndResource(@Param("tenantId") String tenantId,
    //                                        @Param("resourceKey") String resourceKey);

    // 方式2：用 XML（推荐复杂 SQL）
    // 在 resources/mapper/PolicyMapper.xml 中定义

    // 方式3：用 MyBatis-Plus 的 QueryWrapper（推荐，无需写 SQL）
    // 在 Service 层使用：
    // policyMapper.selectOne(new LambdaQueryWrapper<PolicyEntity>()
    //     .eq(PolicyEntity::getTenantId, tenantId)
    //     .eq(PolicyEntity::getResourceKey, resourceKey));
}