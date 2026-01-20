# Rate Limiter Platform

一个高性能、分布式的多租户限流与配额管理平台，支持动态策略配置、实时限流决策、精确审计和全面监控。

## 🚀 项目概述

Rate Limiter Platform 是一个面向微服务架构的限流解决方案，采用 Control Plane + Data Plane + Accounting 的分层设计，为多租户环境提供灵活、高效的流量控制能力。

### 核心特性

- 🏗️ **分层架构**：Control Plane（策略管理）、Data Plane（限流执行）、Accounting（审计计量）
- 🔥 **高性能限流**：本地 Token Bucket + Redis 全局一致性，支持高并发场景
- 🏢 **多租户支持**：租户级别的策略隔离和配额管理
- ⚡ **动态策略**：支持策略热更新，实时同步到执行节点
- 🎯 **多种算法**：Token Bucket、Fixed Window、Sliding Window（规划中）
- 🔄 **幂等设计**：基于 requestId 的重复请求处理
- 📊 **精确审计**：完整的配额消耗记录和对账能力
- ☁️ **分布式友好**：Redis 集群支持，Kafka 消息队列
- 🔍 **全面监控**：Prometheus + Grafana 实时监控和告警

## 📋 系统架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│  Control Plane  │    │   Data Plane     │    │   Accounting        │
│  (策略管理)      │───▶│  (限流执行)       │───▶│   (审计计量)         │
│  Port:  8081     │    │  Port: 8082      │    │   Port: 8083        │
│                 │    │                  │    │                     │
│ - 策略 CRUD     │    │ - /api/v1/check  │    │ - Kafka 消费        │
│ - RESTful API   │    │ - Token Bucket   │    │ - 批量审计入库      │
│ - 策略发布      │    │ - Redis 一致性   │    │ - 重复处理保护      │
│ - 租户管理      │    │ - 策略动态同步   │    │ - 数据聚合分析      │
└─────────────────┘    └──────────────────┘    └─────────────────────┘
         │                       │                         ▲
         │                       │                         │
         ▼                       ▼                         │
    ┌─────────┐            ┌────────┐              ┌──────────┐
    │  MySQL  │            │  Redis  │              │  Kafka   │
    │(策略存储)│            │(限流状态)│              │(事件流)   │
    └─────────┘            └─────────┘              └──────────┘
                                                          │
                                                          ▼
                           ┌─────────────────────────────────────────┐
                           │          Monitoring Stack               │
                           │  ┌─────────────┐  ┌─────────────────┐   │
                           │  │ Prometheus  │  │     Grafana     │   │
                           │  │    : 9090    │  │      :3000      │   │
                           │  │(指标收集)    │  │  (可视化面板)    │   │
                           │  └─────────────┘  └─────────────────┘   │
                           └─────────────────────────────────────────┘
```

## 🛠️ 技术栈

- **后端框架**：Spring Boot 3.2.0
- **数据库**：MySQL 8.0 + MyBatis-Plus
- **缓存**：Redis 7.0 + Spring Data Redis (Lettuce)
- **消息队列**：Apache Kafka 2.8+
- **监控**：Micrometer + Prometheus + Grafana
- **构建工具**：Maven 3.8+
- **JDK版本**：Java 17+

## 📦 模块结构

```
rate-limiter-platform/
├── limiter-common/              # 公共基础模块
│   ├── dto/                     # 数据传输对象
│   ├── constant/                # 常量和错误码
│   └── exception/               # 异常定义
├── limiter-common-web/          # Web 通用组件
│   ├── dto/                     # API 请求响应模型
│   ├── event/                   # 事件定义 (QuotaConsumedEvent)
│   ├── filter/                  # 统一过滤器
│   └── config/                  # Web 配置
├── limiter-control-plane/       # 控制平面 (: 8081)
│   ├── api/                     # REST API 控制器
│   ├── application/             # 应用服务层 (PolicyService)
│   └─ infrastructure/          # 基础设施层 (MySQL持久化)
├── limiter-data-plane/          # 数据平面 (:8082)
│   ├── api/                     # 限流检查 API
│   ├── application/             # 限流业务逻辑
│   │   ├── CheckUseCaseService  # 核心限流逻辑
│   │   ├── PolicySyncService    # 策略动态同步
│   │   └── metrics/             # 监控指标服务
│   ├── domain/                  # 领域模型
│   │   ├── TokenBucketManager   # Token Bucket 算法
│   │   └── PolicyCache          # 策略缓存
│   └── infrastructure/          # Redis、Kafka、监控集成
├── limiter-accounting/          # 审计服务 (:8083)
│   ├── application/             # 审计业务逻辑
│   ├── infrastructure/          # 数据持久化
│   │   └── messaging/           # Kafka消费者
│   └── listener/                # 事件监听器
└── monitoring/                  # 监控配置
    ├── docker-compose.yml       # Prometheus + Grafana
    ├── prometheus.yml           # Prometheus 配置
    └── grafana/                 # Grafana 仪表板
```

## 🚦 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+
- Kafka 2.8+
- Docker & Docker Compose (用于监控)

### 本地开发

1. **克隆项目**
   ```bash
   git clone https://github.com/yourusername/rate-limiter-platform.git
   cd rate-limiter-platform
   ```

2. **启动基础服务**
   ```bash
   # 启动 MySQL
   docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=password mysql:8.0
   
   # 启动 Redis  
   docker run -d --name redis -p 6379:6379 redis:7.0
   
   # 启动 Kafka
   docker run -d --name kafka -p 9092:9092 \
     -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
     -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
     confluentinc/cp-kafka: latest
   ```

3. **启动监控系统**
   ```bash
   docker-compose up -d prometheus grafana
   ```

4. **数据库初始化**
   ```sql
   CREATE DATABASE rate_limiter;
   -- 执行 docs/sql/schema.sql 中的表结构脚本
   ```

5. **编译项目**
   ```bash
   mvn clean compile
   ```

6. **启动微服务**
   ```bash
   # 启动 Control Plane (端口 8081)
   cd limiter-control-plane && mvn spring-boot:run
   
   # 启动 Data Plane (端口 8082) 
   cd limiter-data-plane && mvn spring-boot:run
   
   # 启动 Accounting Service (端口 8083)
   cd limiter-accounting && mvn spring-boot:run
   ```

### 验证部署

```bash
# 检查服务健康状态
curl http://localhost:8081/actuator/health  # Control Plane
curl http://localhost:8082/actuator/health  # Data Plane  
curl http://localhost:8083/actuator/health  # Accounting

# 访问监控面板
open http://localhost:3000  # Grafana (admin/admin123)
open http://localhost:9090  # Prometheus
```

## 📖 API 使用示例

### 创建限流策略

```bash
curl -X POST http://localhost:8081/api/v1/policies \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: create-policy-001" \
  -d '{
    "tenantId": "tenant_001",
    "resourceKey": "/api/v1/orders",
    "policyType": "TOKEN_BUCKET", 
    "capacity": 1000,
    "refillRate": 16.67,
    "windowSeconds": 60,
    "enabled": true
  }'
```

### 执行限流检查

```bash
curl -X POST http://localhost:8082/api/v1/check \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: rate-limit-check-001" \
  -d '{
    "requestId": "order-req-789",
    "tenantId":  "tenant_001", 
    "resourceKey": "/api/v1/orders",
    "tokens": 1,
    "timestamp": 1700000000000
  }'
```

### 响应格式

```json
{
  "success": true,
  "data":  {
    "allowed": true,
    "remaining": 999,
    "policyVersion": "v1", 
    "reason": "",
    "tenantId": "tenant_001",
    "resourceKey": "/api/v1/orders",
    "requestId": "order-req-789",
    "timestamp": 1700000000000
  },
  "error": null,
  "traceId": "rate-limit-check-001",
  "requestId": "order-req-789"
}
```

## 📊 监控指标

### 业务指标

| 指标名称 | 类型 | 描述 | 标签 |
|---------|------|------|------|
| `rate_limit_check_total` | Counter | 限流检查总数 | tenant_id, resource_key |
| `rate_limit_allowed_total` | Counter | 允许的请求数 | tenant_id, resource_key, process_path |
| `rate_limit_denied_total` | Counter | 拒绝的请求数 | tenant_id, resource_key, reason |
| `rate_limit_check_duration_seconds` | Histogram | 限流检查延迟 | tenant_id, resource_key, process_path |
| `rate_limit_policy_cache_size` | Gauge | 策略缓存大小 | - |
| `quota_event_published_total` | Counter | 已发布的事件数 | tenant_id, resource_key |

### 技术指标

- **JVM 指标**：内存使用、GC 频率、线程数
- **数据库指标**：连接池状态、查询延迟
- **Redis 指标**：连接数、命令延迟、内存使用
- **Kafka 指标**：消息积压、消费延迟、分区状态

### Grafana 仪表板

访问 `http://localhost:3000` 查看预配置的仪表板：

- **系统总览**：整体性能和健康状态
- **限流业务**：请求量、成功率、热点租户
- **性能分析**：延迟分布、处理路径、瓶颈分析
- **基础设施**：JVM、数据库、缓存、消息队列状态

## 🧪 运行测试

```bash
# 运行所有测试
mvn test

# 运行集成测试
mvn test -Dtest=*IntegrationTest

# 性能压测
chmod +x test-metrics.sh && ./test-metrics. sh

# 验证监控指标
curl http://localhost:8082/actuator/prometheus | grep rate_limit
```

## 🗺️ Roadmap

### v0.3 计划 (进行中)
- [ ] 完善 Grafana 仪表板模板和告警规则
- [ ] 支持更多限流算法 (Fixed Window, Sliding Window)
- [ ] 增强错误处理和熔断机制
- [ ] 性能优化和压力测试

### v0.4 计划
- [ ] 管理控制台 Web UI
- [ ] 多区域部署和高可用架构
- [ ] 自动扩缩容和负载均衡
- [ ] 机器学习驱动的智能限流

### v1.0 计划
- [ ] 完整的计费和报表功能  
- [ ] 企业级安全和权限控制
- [ ] 云原生部署 (Kubernetes)
- [ ] 完整的运维工具链

## 📊 版本历史

### v0.2. 0 (Current)
- ✅ 全面监控可观测性系统
- ✅ Prometheus + Grafana 集成
- ✅ 多维度业务和技术指标
- ✅ Kafka 事件流优化

### v0.1.0 
- ✅ 核心限流功能 (Token Bucket)
- ✅ 多租户策略管理
- ✅ 分布式一致性 (Redis)
- ✅ 审计事件流 (Kafka)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送分支 (`git push origin feature/amazing-feature`) 
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 📞 联系我们

- GitHub Issues: [项目问题反馈](https://github.com/NzuCRAS/rate-limiter-platform/issues)
- Email: 1351573471@qq.com

---

⭐ 如果这个项目对你有帮助，请给我们一个 Star！

**当前版本**:  v0.2.0 - 全面监控可观测性版本  
**更新时间**: 2026-01-20
