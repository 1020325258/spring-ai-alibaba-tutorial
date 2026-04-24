## Why

SRE-Agent 目前通过 `ContractDao` 直连数据库查询签约相关数据，存在以下问题：
1. 数据库直连增加运维复杂度，需要维护数据库连接配置
2. 跨系统数据访问缺乏统一接口层，难以追踪和审计
3. 数据库结构变更需要同步修改 SRE-Agent 代码，耦合度高

通过 HTTP 接口访问生产数据，可实现数据访问层的解耦，降低运维成本。

## What Changes

### 销售项目（数据提供方）
- 扩展 `SreQueryController` 暴露 9 个 HTTP 接口
- 直接返回数据库实体或 Map，无需创建 DTO

### SRE-Agent（数据消费方）
- 移除 7 个 Gateway 对 `ContractDao` 的依赖
- 改为通过 `HttpEndpointClient` 调用远程接口
- 新增 `sre-data-endpoints.yml` 配置 9 个接口定义
- **BREAKING**: 移除数据库连接配置，SRE-Agent 不再直连签约数据库

## Capabilities

### New Capabilities
- `sre-contract-query`: SRE 合同数据查询能力，通过 HTTP 接口查询合同基本信息、节点、签约人、扩展字段、签约单据等

### Modified Capabilities
- `contract-ontology`: 合同本体查询能力，数据源从数据库改为 HTTP 接口，查询能力保持不变

## Impact

### 销售项目
- 修改文件：`SreQueryController.java`
- 现有功能无影响

### SRE-Agent
- 修改文件：7 个 Gateway 实现类
- 新增文件：`endpoints/sre-data-endpoints.yml`
- 删除依赖：`ContractDao.java` 可能被其他模块使用，保留但 Gateway 不再依赖
- 配置变更：移除签约数据库连接配置（如 `spring.datasource.contract.*`）

### 运维影响
- SRE-Agent 部署不再需要配置签约数据库连接
- 需确保销售项目接口可用性（SLA）
