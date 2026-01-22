package com.ratelimiter.accounting.application;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ratelimiter.accounting.infrastructure.persistence.mapper.QuotaAuditMapper;
import com.ratelimiter.accounting.infrastructure.persistence.mysql.QuotaAuditEntity;
import org.springframework. stereotype.Service;

import java.util.List;

@Service
public class AuditService extends ServiceImpl<QuotaAuditMapper, QuotaAuditEntity> {

    /**
     * 高性能批量插入
     */
    public boolean saveBatchHighPerformance(List<QuotaAuditEntity> records) {
        if (records.isEmpty()) {
            return true;
        }

        // 使用更大的批量大小，减少数据库交互次数
        int batchSize = Math.min(2000, records.size()); // 每批最多2000条

        try {
            return this.saveBatch(records, batchSize);
        } catch (Exception e) {
            log.error("High performance batch insert failed, trying smaller batches", e);

            // 如果大批量失败，尝试小批量
            return this.saveBatch(records, 500);
        }
    }
}