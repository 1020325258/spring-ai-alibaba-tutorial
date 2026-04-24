## Context

### 当前架构
SRE-Agent 通过 `ContractDao` 直连签约数据库查询数据：
- `ContractDao` 使用 `JdbcTemplate` 执行 SQL
- 包含分表路由逻辑（`contract_field_sharding_*`）
- 7 个 Gateway 依赖 `ContractDao` 进行数据查询

### 销售项目现状
- 使用 MyBatis Mapper 访问数据库
- 实体类位于 `dao/model/contract/` 目录
- 已有实体类：Contract、ContractNode、ContractUser、ContractField、ContractQuotationRelation、ContractCityCompanyInfo

### 约束
- 销售项目接口必须兼容现有查询语义
- 分表路由逻辑必须在服务端保留
- 接口鉴权需确保安全性（app=sreAgent 参数）
- HTTP 调用需合理设置超时，避免影响 SRE-Agent 响应

### 涉及系统
- **销售项目**：`/Users/zqy/work/project/nrs-sales-project/utopia-nrs-sales-project-start/src/main/java/com/ke/utopia/nrs/salesproject/controller/contract/tool/SreQueryController.java`
- **SRE-Agent**：`/Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test/06-SRE-Agent/`

## Goals / Non-Goals

**Goals:**
- 解耦 SRE-Agent 与签约数据库的直连依赖
- 提供标准化的 HTTP 接口供 SRE-Agent 调用
- 保持现有查询能力和返回数据格式不变
- 销售项目接口实现轻量化，直接返回实体或 Map

**Non-Goals:**
- 不改变现有业务逻辑或数据结构
- 不优化查询性能
- 不引入新的数据查询能力
- 不创建新的 DTO 类

## Decisions

### 决策 1：接口返回格式

**选择**: 直接返回数据库实体或 Map<String, Object>

**原因**:
- 销售项目已有现成的实体类（Contract、ContractNode 等）
- 无需创建额外的 DTO 层，保持接口轻量
- SRE-Agent 端负责字段映射和格式转换

**实现**:
```java
// 销售项目 SreQueryController
@GetMapping("/contract")
public ResultDTO<Contract> getContract(@RequestParam String contractCode,
                                        @RequestParam String app) {
    if (!SRE_AGENT.equals(app)) return success(null);
    Contract contract = contractMapper.selectByContractCode(contractCode);
    return success(contract);
}

@GetMapping("/contract/field")
public ResultDTO<Map<String, Object>> getContractFields(@RequestParam String contractCode,
                                                         @RequestParam String app) {
    if (!SRE_AGENT.equals(app)) return success(null);
    // 分表查询返回 Map
    return success(contractFieldShardingMapper.selectByContractCode(contractCode));
}
```

### 决策 2：现有实体类映射

| 接口 | 返回类型 | 对应实体类 |
|------|---------|-----------|
| GET /sre/contract | Contract | `dao/model/contract/Contract.java` |
| GET /sre/contract/node | List\<ContractNode\> | `dao/model/contract/ContractNode.java` |
| GET /sre/contract/user | List\<ContractUser\> | `dao/model/contract/ContractUser.java` |
| GET /sre/contract/field | Map\<String,Object\> | 无实体，分表查询 |
| GET /sre/contract/quotation-relation | List\<ContractQuotationRelation\> | `dao/model/contract/ContractQuotationRelation.java` |
| GET /sre/contract/list-by-order | List\<Contract\> | `dao/model/contract/Contract.java` |
| GET /sre/contract/config-field | Contract | `dao/model/contract/Contract.java`（部分字段） |
| GET /sre/project-config-snap/config-id | String | 直接返回字符串 |
| GET /sre/contract-city-company-info | List\<ContractCityCompanyInfo\> | `dao/model/contract/ContractCityCompanyInfo.java` |

### 决策 3：分表路由处理

**选择**: 分表路由逻辑保留在销售项目服务端

**原因**:
- 分表规则（`contract_code` 数字部分 % 10）属于数据层细节
- SRE-Agent 不应感知分表逻辑
- 接口参数只需 `contractCode`，返回聚合数据

**实现**:
```java
// 销售项目 - 使用现有 ContractFieldShardingMapper
@GetMapping("/contract/field")
public ResultDTO<Map<String, Object>> getContractFields(@RequestParam String contractCode,
                                                         @RequestParam String app) {
    if (!SRE_AGENT.equals(app)) return success(null);
    // Mapper 内部已处理分表路由
    List<ContractFieldSharding> fields = contractFieldShardingMapper.selectByContractCode(contractCode);
    Map<String, Object> fieldMap = fields.stream()
        .collect(Collectors.toMap(ContractFieldSharding::getFieldKey,
                                   ContractFieldSharding::getFieldValue));
    return success(fieldMap);
}
```

### 决策 4：接口鉴权

**选择**: 复用现有 `app=sreAgent` 参数鉴权

**原因**:
- 与现有 `/sre/encrypt`、`/sre/decrypt` 接口一致
- 实现简单，无需引入复杂认证机制

## Risks / Trade-offs

### 风险 1：接口超时
**风险**: HTTP 调用可能比数据库直连慢，影响 SRE-Agent 响应时间
**缓解**:
- 设置合理的超时时间（10s）
- 异步化查询（如需要）

### 风险 2：接口可用性
**风险**: 销售项目接口不可用时，SRE-Agent 查询失败
**缓解**:
- 添加重试机制
- 监控接口可用性

### 风险 3：实体字段变化
**风险**: 销售项目实体类字段变化可能影响 SRE-Agent 解析
**缓解**:
- SRE-Agent 端做字段兼容处理
- 字段变化需通知双方

## Migration Plan

### 阶段 1：销售项目接口开发（1.5天）
1. 扩展 `SreQueryController`（9个接口）
2. 复用现有 Mapper 和实体类
3. 单元测试

### 阶段 2：SRE-Agent 改造（1.5天）
1. 创建 `sre-data-endpoints.yml`
2. 改造 7 个 Gateway
3. 移除 `ContractDao` 依赖
4. 集成测试

### 阶段 3：验证和上线（0.5天）
1. 联调测试
2. 文档更新
3. 灰度发布

### 回滚策略
- 销售项目接口独立，不影响现有功能
- SRE-Agent 改造后如有问题，可快速回滚到数据库直连版本
