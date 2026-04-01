## Context

已完成 `FormalSignableOrderInfoGateway` 和 `PersonalSignableOrderInfoGateway` 的 YAML 驱动改造试点，验证了技术方案可行。当前需要将改造推广到其余 10 个 Gateway。

**当前 Gateway 列表（12个）：**
1. OrderGateway - 订单查询
2. BudgetBillGateway - 报价单查询
3. ContractGateway - 合同查询
4. ContractNodeGateway - 合同节点查询
5. ContractQuotationRelationGateway - 签约单据查询
6. ContractFieldGateway - 合同字段查询
7. ContractConfigGateway - 配置表查询
8. ContractInstanceGateway - 合同实例查询
9. SubOrderGateway - S单查询
10. PersonalQuoteGateway - 个性化报价查询
11. FormalSignableOrderInfoGateway - ✅ 已完成
12. PersonalSignableOrderInfoGateway - ✅ 已完成

## Goals / Non-Goals

**Goals:**
- 将所有 12 个 Gateway 的字段解析逻辑迁移到 `domain-ontology.yaml` 配置
- 保留每个 Gateway 的旧解析方法，新增一致性校验（不一致时打印 ERROR）
- 统一使用 `EntitySchemaMapper` + `JsonPathResolver` 进行 YAML 驱动的数据解析

**Non-Goals:**
- 不修改 LLM 提示词或 ontologyQuery 引擎逻辑
- 不修改实体关系定义（relations）
- 不删除各 Gateway 的旧方法，待全量验证通过后再删除

## Decisions

### 1. YAML 配置结构
参考试点经验，每个实体配置：
```yaml
- name: EntityName
  sourceType: http|db
  endpoint: endpoint-id  # 当 sourceType=http 时
  flattenPath: "data[].items[]"  # 数组展平路径（可选）
  lookupStrategies:
    - field: xxx
      pattern: "xxx"
  attributes:
    - { name: fieldName, source: "$param.xxx" | "field" | "data[].field" }
```

### 2. 解析模式选择
根据各 Gateway 数据源类型选择解析模式：
- **HTTP 接口返回**：使用 `flattenPath` + `EntitySchemaMapper.map()`
- **数据库查询**：直接返回结果，无需特殊处理

### 3. 一致性校验机制
每个 Gateway 的 `queryByField` 方法：
```java
List<Map<String, Object>> newResult = parseYAML(rawJson, params);
if (hasSourceConfig) {
    List<Map<String, Object>> oldResult = parseOld(rawJson, params);
    if (!equals(newResult, oldResult)) {
        log.error("[Gateway] 新旧方法输出一致性校验失败!");
    }
}
return newResult;
```

### 4. 实施顺序
按数据源复杂度分批：
1. **第一批（简单）**：Order、BudgetBill、ContractNode、ContractConfig、ContractField
2. **第二批（中等）**：Contract、ContractQuotationRelation、SubOrder
3. **第三批（复杂）**：ContractInstance、PersonalQuote

## Risks / Trade-offs

- [Risk] 部分 Gateway 数据结构复杂，可能无法完全用 YAML 表达
  - → Mitigation：保留旧方法作为备选，一致性校验会发现问题
- [Risk] 字段类型转换差异（如 Integer/Double）
  - → Mitigation：equals 方法已处理 Number 类型比较
- [Risk] 全量改造后测试覆盖不足
  - → Mitigation：每改造一个 Gateway 运行 QaPairEvaluationIT 验证

## Migration Plan

1. 为每个 Gateway 添加 YAML 配置
2. 改造 Gateway 代码，使用 EntitySchemaMapper
3. 运行集成测试验证一致性
4. 验证通过后删除旧方法
5. 更新 CLAUDE.md 文档

## Open Questions

- 是否需要为复杂嵌套字段扩展 JsonPathResolver 支持？
- 如何处理部分 Gateway 没有 HTTP endpoint 的情况？
