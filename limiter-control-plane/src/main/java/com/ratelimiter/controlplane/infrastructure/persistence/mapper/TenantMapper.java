package com.ratelimiter.controlplane.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ratelimiter.controlplane.infrastructure.persistence.mysql.TenantEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 *
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

}
