package com.ratelimiter.accounting.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ratelimiter.accounting.infrastructure.persistence.mysql.QuotaAuditEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuotaAuditMapper extends BaseMapper<QuotaAuditEntity> {
    // BaseMapper 提供基础方法
}
