## Context

当前 HTTP 实体的数据解析存在两种模式：

| 模式 | 实体 | 解析方式 |
|------|------|---------|
| YAML 驱动 | BudgetBill, FormalSignableOrderInfo, PersonalSignableOrderInfo | `EntitySchemaMapper.map(entity, rawJson, queryParams)` |
| 硬编码 | SubOrder, PersonalQuote | 自定义 `parseXxx()` 方法 |

YAML 驱动模式的优势：
- 新增字段只需修改 YAML 配置
- 统一的解析逻辑，便于维护
- 支持 `$param.xxx` 注入查询参数

## Goals / Non-Goals

**Goals:**
- SubOrder 和 PersonalQuote 使用 YAML source 配置驱动解析
- 保持新旧解析输出一致性
- 复用现有的 `EntitySchemaMapper` 和 `JsonPathResolver`

**Non-Goals:**
- 不改造数据库实体（Contract、ContractNode 等）
- 不修改 `EntitySchemaMapper` 的核心逻辑
- 不改变 API 接口调用方式

## Decisions

### 1. YAML 配置结构

**SubOrder 配置**：
```yaml
- name: SubOrder
  sourceType: http
  endpoint: sub-order-info
  flattenPath: "data"  # 直接数组
  attributes:
    - { name: orderNo, source: "orderNo" }
    - { name: projectChangeNo, source: "projectChangeNo" }
    - { name: mdmCode, source: "mdmCode" }
    - { name: dueAmount, source: "dueAmount" }
    - { name: status, source: "status" }
```

**PersonalQuote 配置**：
```yaml
- name: PersonalQuote
  sourceType: http
  endpoint: contract-personal-data
  flattenPath: "data.personalContractDataList"  # 嵌套路径
  attributes:
    - { name: projectOrderId, source: "$param.projectOrderId" }
    - { name: billCode, source: "billCode" }
    - { name: personalContractPrice, source: "personalContractPrice" }
    - { name: organizationCode, source: "organizationCode" }
    - { name: organizationName, source: "organizationName" }
    - { name: createTime, source: "createTime" }
    - { name: quoteFileUrl, source: "quoteInfo.fileUrl" }
    - { name: quotePrevUrl, source: "quoteInfo.prevUrl" }
```

**rationale**: 与现有 BudgetBill 等实体保持一致的配置风格。

### 2. Gateway 改造模式

参考 `FormalSignableOrderInfoGateway` 的实现模式：

```java
// 1. 注入依赖
private final EntitySchemaMapper schemaMapper;
private final EntityRegistry entityRegistry;

// 2. 调用新解析方法
List<Map<String, Object>> newResult = schemaMapper.map(entity, rawJson, queryParams);

// 3. 一致性校验（临时）
if (hasSourceConfig) {
    List<Map<String, Object>> oldResult = parseXxxOld(rawJson, ...);
    if (!equals(newResult, oldResult)) {
        log.error("新旧方法输出一致性校验失败!");
    }
}

// 4. 返回新结果
return newResult;
```

**rationale**: 保持与已改造 Gateway 的一致性，便于后续移除旧代码。

### 3. flattenPath 支持验证

需要验证 `EntitySchemaMapper` 对以下场景的支持：
- `flattenPath: "data"` — 直接数组
- `flattenPath: "data.personalContractDataList"` — 嵌套路径中的数组

如不支持，需扩展 `JsonPathResolver.flattenWithInheritance()`。

## Risks / Trade-offs

**风险 1**: `flattenPath` 对扁平数组支持不确定
→ **缓解**: 先运行单元测试验证，必要时扩展 `JsonPathResolver`

**风险 2**: PersonalQuote 的响应结构可能更复杂
→ **缓解**: 解析失败时回退到旧方法，记录日志便于排查

**风险 3**: 新旧输出可能存在细微差异（如 null vs 空字符串）
→ **缓解**: 一致性校验使用宽松比较（`Objects.equals` 或忽略 null 差异）
