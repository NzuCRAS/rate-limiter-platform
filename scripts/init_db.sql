-- ============================================
-- Rate Limiter Platform - Database Init Script
-- ============================================
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4（支持完整 Unicode，包括 emoji）
-- 引擎：InnoDB（支持事务、外键、MVCC）
-- ============================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS rate_limiter
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rate_limiter;

-- ============================================
-- 2. 租户表 (tenant)
-- ============================================
-- 用途：存储租户基本信息，关联策略与审计
-- 读写特点：低频读写
-- 关键查询：按 tenant_id 查询
-- ============================================

CREATE TABLE IF NOT EXISTS tenant (
    -- 主键
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '内部自增主键',

    -- 业务字段
                                      tenant_id VARCHAR(64) NOT NULL COMMENT '租户唯一标识（业务 ID）',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    contact_email VARCHAR(128) DEFAULT NULL COMMENT '联系邮箱',
    tier VARCHAR(32) NOT NULL DEFAULT 'BASIC' COMMENT '服务等级：FREE/BASIC/VIP/ENTERPRISE',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/SUSPENDED/DELETED',

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    UNIQUE KEY uk_tenant_id (tenant_id) COMMENT '租户 ID 唯一索引（业务查询入口）',
    KEY idx_status (status) COMMENT '按状态筛选索引',
    KEY idx_tier (tier) COMMENT '按服务等级筛选索引'

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci
    COMMENT='租户信息表';


-- ============================================
-- 3. 策略表 (policy) - 核心表
-- ============================================
-- 用途：存储每个租户对不同资源的限流/配额规则
-- 读写特点：中频读（每次 check 可能查询）、低频写
-- 关键查询：按 (tenant_id, resource_key) 查询
-- 面试要点：唯一联合索引保证业务约束
-- ============================================

CREATE TABLE IF NOT EXISTS policy (
    -- 主键
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '策略 ID',

    -- 关联字段
                                      tenant_id VARCHAR(64) NOT NULL COMMENT '租户标识',
    resource_key VARCHAR(128) NOT NULL COMMENT '资源标识（如 API path、服务名）',

    -- 策略配置
    policy_type VARCHAR(32) NOT NULL DEFAULT 'TOKEN_BUCKET' COMMENT '策略类型：TOKEN_BUCKET/FIXED_WINDOW/SLIDING_WINDOW',
    window_seconds INT NOT NULL DEFAULT 60 COMMENT '时间窗口（秒），如 60 表示 1 分钟',
    capacity BIGINT NOT NULL COMMENT '桶容量/窗口最大请求数',
    refill_rate DECIMAL(10,4) NOT NULL COMMENT '令牌补充速率（tokens/sec），如 16.67 表示每秒补充 16.67 个 token',
    burst_capacity BIGINT DEFAULT NULL COMMENT '突发容量（可选），允许短时超过 capacity',

    -- 优先级与控制
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级（数字越大越优先），VIP 租户可设高优先级',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用（软删除/灰度控制）',
    version VARCHAR(32) NOT NULL DEFAULT 'v1' COMMENT '策略版本号（用于灰度发布与回滚）',

    -- 扩展字段
    metadata JSON DEFAULT NULL COMMENT '扩展配置（JSON 格式），如自定义规则、标签等',
    description VARCHAR(512) DEFAULT NULL COMMENT '策略描述',

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    UNIQUE KEY uk_tenant_resource (tenant_id, resource_key) COMMENT '租户+资源唯一索引（业务约束：一个租户对一个资源只能有一条策略）',
    KEY idx_enabled (enabled) COMMENT '按启用状态筛选',
    KEY idx_priority (priority) COMMENT '按优先级排序',
    KEY idx_version (version) COMMENT '按版本查询',

    -- 约束检查（MySQL 8.0.16+ 支持）
    CONSTRAINT chk_capacity CHECK (capacity > 0),
    CONSTRAINT chk_refill_rate CHECK (refill_rate > 0),
    CONSTRAINT chk_window_seconds CHECK (window_seconds > 0)

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci
    COMMENT='限流/配额策略表';


-- ============================================
-- 4. 配额审计表 (quota_audit) - 高频写入表
-- ============================================
-- 用途：记录每次 checkAndConsume 的结果（允许/拒绝）
-- 读写特点：高频写入、中频读（报表/对账）
-- 关键查询：按 (tenant_id, timestamp) 范围查询
-- 面试要点：
--   1. request_id 唯一索引保证幂等性
--   2. 组合索引优化按租户+时间查询
--   3. 生产环境可考虑分区表（按月分区）
--   4. v2 可迁移到 ClickHouse 做 OLAP
-- ============================================

CREATE TABLE IF NOT EXISTS quota_audit (
    -- 主键
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计记录 ID',

    -- 请求标识
                                           request_id VARCHAR(64) NOT NULL COMMENT '请求幂等 ID（用于去重与追溯）',

    -- 关联字段
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户标识',
    resource_key VARCHAR(128) NOT NULL COMMENT '资源标识',

    -- 请求信息
    tokens BIGINT NOT NULL COMMENT '请求的 token 数量',
    allowed BOOLEAN NOT NULL COMMENT '是否允许（TRUE=通过，FALSE=拒绝）',
    remaining BIGINT DEFAULT NULL COMMENT '剩余配额（估算值，可选）',
    reason VARCHAR(128) DEFAULT NULL COMMENT '拒绝原因：quota_exceeded/blacklisted/policy_disabled 等',
    policy_version VARCHAR(32) DEFAULT NULL COMMENT '使用的策略版本号（便于追溯）',

    -- 客户端信息（可选，用于安全审计）
    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端 IP',
    user_agent VARCHAR(256) DEFAULT NULL COMMENT 'User-Agent',

    -- 性能指标
    latency_ms INT DEFAULT NULL COMMENT '处理耗时（毫秒），用于性能分析',

    -- 时间字段
    timestamp BIGINT NOT NULL COMMENT '请求时间戳（毫秒，高精度）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',

    -- 索引
    UNIQUE KEY uk_request_id (request_id) COMMENT '请求 ID 唯一索引（幂等性保证）',
    KEY idx_tenant_timestamp (tenant_id, timestamp) COMMENT '租户+时间组合索引（最常见查询：某租户某时间段的记录）',
    KEY idx_resource (resource_key) COMMENT '按资源统计',
    KEY idx_allowed (allowed) COMMENT '按通过/拒绝统计成功率',
    KEY idx_timestamp (timestamp) COMMENT '按时间范围查询（全局统计）'

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci
    COMMENT='配额消费审计表（高频写入）';

-- 可选：为 quota_audit 创建分区（按月分区，便于归档与查询性能优化）
-- 注意：创建分区表需要先删除上面的表，或用 ALTER TABLE
-- 示例（生产环境建议）：
/*
ALTER TABLE quota_audit PARTITION BY RANGE (YEAR(FROM_UNIXTIME(timestamp/1000))) (
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
*/


-- ============================================
-- 5. 策略变更历史表 (policy_version) - 可选表
-- ============================================
-- 用途：记录策略变更历史（审计、回滚）
-- 读写特点：低频写、低频读
-- MVP 阶段可不建，v2 添加
-- ============================================

CREATE TABLE IF NOT EXISTS policy_version (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '版本记录 ID',
                                              policy_id BIGINT NOT NULL COMMENT '关联的策略 ID',
                                              version VARCHAR(32) NOT NULL COMMENT '版本号',
    config_snapshot JSON NOT NULL COMMENT '策略配置快照（完整 JSON）',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型：CREATE/UPDATE/DELETE',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',

    KEY idx_policy_id (policy_id) COMMENT '按策略 ID 查询历史',
    KEY idx_created_at (created_at) COMMENT '按时间查询变更记录'

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci
    COMMENT='策略变更历史表（可选）';


-- ============================================
-- 6. 插入测试数据
-- ============================================

-- 6.1 插入测试租户
INSERT INTO tenant (tenant_id, tenant_name, contact_email, tier, status) VALUES
                                                                             ('tenant_001', 'Alpha Tech Inc', 'admin@alpha.com', 'VIP', 'ACTIVE'),
                                                                             ('tenant_002', 'Beta Solutions Ltd', 'admin@beta.com', 'BASIC', 'ACTIVE'),
                                                                             ('tenant_003', 'Gamma Startup', 'admin@gamma.com', 'FREE', 'ACTIVE'),
                                                                             ('tenant_999', 'Test Suspended Company', 'test@suspended.com', 'BASIC', 'SUSPENDED');

-- 6.2 插入测试策略
INSERT INTO policy (tenant_id, resource_key, policy_type, window_seconds, capacity, refill_rate, priority, enabled, version, description) VALUES
-- VIP 租户：高配额、高优先级
('tenant_001', '/api/v1/orders', 'TOKEN_BUCKET', 60, 10000, 166. 67, 10, TRUE, 'v1', 'VIP 租户订单 API 限流策略'),
('tenant_001', '/api/v1/payments', 'TOKEN_BUCKET', 60, 5000, 83.33, 10, TRUE, 'v1', 'VIP 租户支付 API 限流策略'),

-- 普通租户：标准配额
('tenant_002', '/api/v1/orders', 'TOKEN_BUCKET', 60, 1000, 16.67, 5, TRUE, 'v1', '基础租户订单 API 限流策略'),
('tenant_002', '/api/v1/payments', 'TOKEN_BUCKET', 60, 500, 8.33, 5, TRUE, 'v1', '基础租户支付 API 限流策略'),

-- 免费租户：低配额
('tenant_003', '/api/v1/orders', 'FIXED_WINDOW', 60, 100, 1.67, 0, TRUE, 'v1', '免费租户订单 API 限流策略'),

-- 测试场景：禁用的策略
('tenant_002', '/api/v1/admin', 'TOKEN_BUCKET', 60, 10, 0.17, 0, FALSE, 'v1', '管理 API（已禁用）');

-- 6.3 插入测试审计数据（模拟历史记录）
INSERT INTO quota_audit (request_id, tenant_id, resource_key, tokens, allowed, remaining, reason, policy_version, client_ip, latency_ms, timestamp) VALUES
                                                                                                                                                        ('req_test_001', 'tenant_001', '/api/v1/orders', 1, TRUE, 9999, NULL, 'v1', '192.168.1.100', 5, UNIX_TIMESTAMP(NOW() - INTERVAL 1 HOUR) * 1000),
                                                                                                                                                        ('req_test_002', 'tenant_001', '/api/v1/orders', 1, TRUE, 9998, NULL, 'v1', '192.168.1.100', 3, UNIX_TIMESTAMP(NOW() - INTERVAL 50 MINUTE) * 1000),
                                                                                                                                                        ('req_test_003', 'tenant_002', '/api/v1/orders', 1, TRUE, 999, NULL, 'v1', '10.0.0.50', 8, UNIX_TIMESTAMP(NOW() - INTERVAL 30 MINUTE) * 1000),
                                                                                                                                                        ('req_test_004', 'tenant_002', '/api/v1/orders', 1, FALSE, 0, 'quota_exceeded', 'v1', '10.0.0.50', 2, UNIX_TIMESTAMP(NOW() - INTERVAL 20 MINUTE) * 1000),
                                                                                                                                                        ('req_test_005', 'tenant_003', '/api/v1/orders', 1, FALSE, 0, 'quota_exceeded', 'v1', '172.16.0.10', 4, UNIX_TIMESTAMP(NOW() - INTERVAL 10 MINUTE) * 1000);


-- ============================================
-- 7. 验证数据
-- ============================================

-- 查看租户数据
SELECT '========== Tenants ==========' AS '';
SELECT tenant_id, tenant_name, tier, status FROM tenant;

-- 查看策略数据
SELECT '========== Policies ==========' AS '';
SELECT tenant_id, resource_key, capacity, refill_rate, priority, enabled, version FROM policy;

-- 查看审计数据
SELECT '========== Audit Records ==========' AS '';
SELECT request_id, tenant_id, resource_key, tokens, allowed, reason, FROM_UNIXTIME(timestamp/1000) as request_time FROM quota_audit ORDER BY timestamp DESC;

-- 验证索引
SELECT '========== Index Verification ==========' AS '';
SHOW INDEX FROM policy WHERE Key_name = 'uk_tenant_resource';
SHOW INDEX FROM quota_audit WHERE Key_name = 'uk_request_id';
SHOW INDEX FROM quota_audit WHERE Key_name = 'idx_tenant_timestamp';


-- ============================================
-- 8. 性能测试查询（验证索引有效性）
-- ============================================

-- 测试 1：按租户+资源查询策略（应使用唯一索引）
EXPLAIN SELECT * FROM policy WHERE tenant_id = 'tenant_001' AND resource_key = '/api/v1/orders';
-- 预期：type=const，key=uk_tenant_resource

-- 测试 2：按租户+时间范围查询审计（应使用组合索引）
EXPLAIN SELECT * FROM quota_audit
        WHERE tenant_id = 'tenant_001'
          AND timestamp BETWEEN UNIX_TIMESTAMP(NOW() - INTERVAL 1 DAY) * 1000 AND UNIX_TIMESTAMP(NOW()) * 1000;
-- 预期：type=range，key=idx_tenant_timestamp

-- 测试 3：按 request_id 查询（幂等性检查，应使用唯一索引）
EXPLAIN SELECT * FROM quota_audit WHERE request_id = 'req_test_001';
-- 预期：type=const，key=uk_request_id


-- ============================================
-- 9. 清理脚本（可选，用于重置测试环境）
-- ============================================

/*
-- 谨慎使用！这会删除所有数据
TRUNCATE TABLE quota_audit;
TRUNCATE TABLE policy;
TRUNCATE TABLE tenant;
TRUNCATE TABLE policy_version;

-- 或完全删除数据库
DROP DATABASE IF EXISTS rate_limiter;
*/