package com.ratelimiter.accounting.application;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ratelimiter.accounting.infrastructure.persistence.mapper.QuotaAuditMapper;
import com.ratelimiter.accounting.infrastructure.persistence.mysql.QuotaAuditEntity;
import org.springframework. stereotype.Service;

import java.util.List;

@Service
public class AuditService extends ServiceImpl<QuotaAuditMapper, QuotaAuditEntity> {

    /**
     * 批量插入审计记录
     * MyBatis-Plus 的 saveBatch 已优化为批量插入
     *
     * @param records 审计记录列表
     * @param batchSize 每批大小（如 1000）
     */
    public boolean batchInsert(List<QuotaAuditEntity> records, int batchSize) {
        return this.saveBatch(records, batchSize);
    }
}