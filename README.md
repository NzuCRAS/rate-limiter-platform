# Rate Limiter Platform

一个高性能、分布式的多租户限流与配额管理平台，支持动态策略配置、实时限流决策和精确审计。

## 🚀 项目概述

Rate Limiter Platform 是一个面向微服务架构的限流解决方案，采用 Control Plane + Data Plane + Accounting 的分层设计，为多租户环境提供灵活、高效的流量控制能力。

### 核心特性

- 🏗️ **分层架构**：Control Plane（策略管理）、Data Plane（限流执行）、Accounting（审计计量）
- 🔥 **高性能限流**：本地 Token Bucket + Redis 全局一致性，支持高并发场景
- 🏢 **多租户支持**：租户级别的策略隔离和配额管理
- ⚡ **动态策略**：支持策略热更新，无需重启服务
- 🎯 **多种算法**：Token Bucket、Fixed Window、Sliding Window
- 🔄 **幂等设计**：基于 requestId 的重复请求处理
- 📊 **精确审计**：完整的配额消耗记录和对账能力
- ☁️ **分布式友好**：Redis 集群支持，Kafka 消息队列

## 📋 系统架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│  Control Plane  │    │   Data Plane     │    │   Accounting        │
│  (策略管理)      │───▶│  (限流执行)       │───▶│   (审计计量)         │
│                 │    │                  │    │                     │
│ - 策略 CRUD     │    │ - /api/v1/check  │    │ - quota_audit 表    │
│ - 策略发布      │    | - Token Bucket   │    │ - 计费聚合          │
│ - 租户管理      │    │ - Redis 一致性   │    │ - 报表生成          │
└─────────────────┘    └──────────────────┘    └─────────────────────┘
         │                       │                         ▲
         │                       │                         │
         ▼                       ▼                         │
    ┌─────────┐            ┌─────────┐              ┌──────────┐
    │  MySQL  │            │  Redis  │              │  Kafka   │
    │(策略存储)│            │(限流状态)│              │(事件流)   │
    └─────────┘            └─────────┘              └──────────┘
```

## 🛠️ 技术栈

- **后端框架**：Spring Boot 3.2.0
- **数据库**：MySQL 8.0 + MyBatis-Plus
- **缓存**：Redis 7.0 + Spring Data Redis (Lettuce)
- **消息队列**：Apache Kafka 2.8+
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
│   ├── filter/                  # 统一过滤器
│   └── config/                  # Web 配置
├── limiter-control-plane/       # 控制平面
│   ├── api/                     # REST API 控制器
│   ├── application/             # 应用服务层
│   └── infrastructure/          # 基础设施层
├── limiter-data-plane/          # 数据平面
│   ├── api/                     # 限流检查 API
│   ├── application/             # 限流业务逻辑
│   ├── domain/                  # 领域模型
│   └── infrastructure/          # Redis、Kafka 集成
└── limiter-accounting/          # 审计服务
    ├── application/             # 审计业务逻辑
    └── infrastructure/          # 数据持久化
```

## 🚦 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+
- Kafka 2.8+ (可选，用于生产环境)

### 本地开发

1. **克隆项目**
   ```bash
   git clone https://github.com/yourusername/rate-limiter-platform.git
   cd rate-limiter-platform
   ```

2. **启动依赖服务**
   ```bash
   # 启动 MySQL
   docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=password mysql:8.0
   
   # 启动 Redis  
   docker run -d --name redis -p 6379:6379 redis:7.0
   ```

3. **数据库初始化**
   ```sql
   CREATE DATABASE rate_limiter;
   -- 执行 docs/sql/schema.sql 中的表结构脚本
   ```

4. **编译项目**
   ```bash
   mvn clean compile
   ```

5. **启动服务**
   ```bash
   # 启动 Control Plane (端口 8080)
   cd limiter-control-plane
   mvn spring-boot:run
   
   # 启动 Data Plane (端口 8081) 
   cd limiter-data-plane
   mvn spring-boot:run
   ```

## 📖 API 使用示例

### 创建限流策略

```bash
curl -X POST http://localhost:8080/api/v1/policies \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-trace-123" \
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
curl -X POST http://localhost:8081/api/v1/check \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-trace-456" \
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
  "traceId": "test-trace-456",
  "requestId": "order-req-789"
}
```

## 🧪 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
cd limiter-data-plane
mvn test

# 运行集成测试
mvn test -Dtest=CheckControllerTest
```

## 📊 监控指标

项目集成了多维度监控能力：

- **TraceId 链路追踪**：每个请求都有唯一 traceId
- **RequestId 业务追踪**：支持业务级别的请求去重和审计
- **性能指标**：限流决策延迟、成功率等
- **业务指标**：租户配额使用情况、热点资源等

## 🗺️ Roadmap

### v0.2 计划 
- [ ] Kafka 事件流集成
- [ ] 策略热下发 (Control Plane -> Data Plane)
- [ ] Accounting 服务完整实现
- [ ] 监控看板 (Grafana Dashboard)

### v0.3 计划
- [ ] 多种限流算法支持 (Fixed Window, Sliding Window)
- [ ] 策略优先级和覆盖规则
- [ ] 多区域部署支持

### v1.0 计划
- [ ] 管理控制台 (Admin UI)
- [ ] 完整的计费和报表功能
- [ ] 集群模式+高可用部署

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

- GitHub Issues: [项目问题反馈](https://github.com/yourusername/rate-limiter-platform/issues)
- Email: 1351573471@qq.com

---

⭐ 如果这个项目对你有帮助，请给我们一个 Star！
