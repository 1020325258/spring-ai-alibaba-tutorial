## Context

### 当前架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           当前数据流                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  domain-ontology.yaml                    Gateway 代码                   │
│  ┌─────────────────────────┐           ┌─────────────────────────┐    │
│  │ entities:               │           │ queryByField()           │    │
│  │   - name: FormalSign..  │           │   └→ HTTP 调用           │    │
│  │     attributes:         │           │   └→ parseSignableOrders │    │
│  │       - contractCode   │           │        └→ 硬编码字段映射   │    │
│  │       - goodsInfo      │           └─────────────────────────┘    │
│  └─────────────────────────┘                       ↑                  │
│         ↓                                        │                  │
│    仅声明字段名，无解析规则                        │                  │
│                                                  ↓                  │
│                                    最终返回的字段集合不透明            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 试点 Gateway 数据结构分析

#### 1. FormalSignableOrderInfoGateway

**接口返回结构**：
```json
{
  "code": 2000,
  "data": [
    {
      "companyName": "北京贝壳家居科技有限公司",
      "companyCode": "V201800236",
      "signableOrderInfos": [
        { "mustSelect": true, "goodsInfo": "木门平开门", "orderAmount": 200.00, ... },
        { "mustSelect": false, "goodsInfo": "定制柜-套外", "orderAmount": 10.00, ... }
      ]
    }
  ]
}
```

**解析特征**：
- 外层 `data[]` 是公司分组
- 内层 `signableOrderInfos[]` 是实际的 S 单列表，需**展平**为多条记录
- 每条展开的记录需要**继承**外层的 `companyName`、`companyCode`

**当前代码解析逻辑**（在 `parseSignableOrders` 方法中）：
```java
for (companyGroup : data) {
  String companyName = companyGroup.path("companyName").asText(null);
  String companyCode = companyGroup.path("companyCode").asText(null);
  for (item : signableOrderInfos) {
    order.put("companyName", companyName);  // 继承外层
    order.put("companyCode", companyCode);  // 继承外层
    order.put("goodsInfo", item.path("goodsInfo").asText(null));
    order.put("orderAmount", item.path("orderAmount").asDouble());
    // ... 更多字段
  }
}
```

#### 2. PersonalSignableOrderInfoGateway

**接口返回结构**：与 FormalSignableOrderInfo 完全相同（不同接口 ID）

**解析逻辑**：与 FormalSignableOrderInfoGateway 几乎相同，仅接口 ID 不同

---

## Goals / Non-Goals

**Goals:**
- 设计 YAML 解析规则语法，支持简单字段、嵌套字段、数组展平、多数组合并、查询参数注入
- 实现 `JsonPathResolver` 轻量级解析引擎
- 改造 FormalSignableOrderInfoGateway 和 PersonalSignableOrderInfoGateway，由 YAML 驱动字段解析
- 保留旧代码 + 验证新旧输出一致性

**Non-Goals:**
- 不一次性改造所有 Gateway（试点范围：2 个）
- 不改变 EntityDataGateway 接口签名
- 暂不支持复杂 transform（如类型转换、计算字段），仅支持字段路径映射
- 暂不实现启动时 schema 校验（后续独立任务）

---

## Decisions

### 决策 1：YAML source 语法设计

**选择**：基于 JsonNode path 语法设计 source 字段

```yaml
# 简单字段
source: "fieldName"

# 嵌套字段
source: "parent.child"

# 数组元素（取每项的 field）
source: "data[].field"

# 数组展平（末尾 [] 表示展平为多条记录）
source: "data[].items[].field"

# 多数组合并（逗号分隔）
source: "listA[].field,listB[].field"

# 查询参数注入（$param 前缀）
source: "$param.projectOrderId"

# 动态字段（不映射）
source: "$raw"
```

**备选**：使用 JsonPath 表达式（如 `$.data[*].items[*].field`）
**理由**：需要额外依赖，且数组展平的语义与 JsonPath 略有差异

---

### 决策 2：YAML 放置位置

**选择**：在 `domain-ontology.yaml` 的 entities 内部增加 `source` 配置

```yaml
entities:
  - name: FormalSignableOrderInfo
    sourceType: http
    endpoint: formal-sign-order-list    # 接口 ID（用于构建请求）
    queryParams:
      - name: projectOrderId
        from: "$param.projectOrderId"  # 从查询参数获取
    attributes:
      - { name: projectOrderId, source: "$param.projectOrderId" }
      - { name: companyName, source: "data[].companyName" }
      - { name: goodsInfo, source: "data[].signableOrderInfos[].goodsInfo" }
      # ...
```

**备选**：独立文件 `entity-schemas.yaml`
**理由**：保持单文件维护，与 entities 定义就近管理

---

### 决策 3：JsonPathResolver 实现策略

**选择**：基于 Jackson JsonNode 封装，约 150 行代码

```java
public class JsonPathResolver {
  private final JsonNode root;
  private final Map<String, Object> queryParams;

  public String get(String source)      // 简单/嵌套字段
  public List<Map<String, Object>> flatten(String source)  // 数组展平
  public List<Map<String, Object>> merge(String... sources)  // 多数组合并
}
```

**备选**：引入 JsonPath (Jayway) 库
**理由**：增加依赖，且数组展平需要额外处理

---

### 决策 4：Gateway 改造策略

**选择**：Gateway 返回原始 JSON String，由引擎层统一解析

```
┌─────────────────────────────────────────────────────────────────┐
│                      改造后的数据流                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Gateway.queryByField()                                         │
│    └→ HTTP 调用 → 返回原始 JSON String                          │
│                    ↓                                            │
│  OntologyQueryEngine                                           │
│    └→ JsonPathResolver 根据 YAML source 解析                   │
│                    ↓                                            │
│  最终返回 List<Map<String, Object>>                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**备选**：在 Gateway 内部解析后再返回 Map
**理由**：引擎层统一解析可以复用，且符合"声明式字段映射"的设计

---

### 决策 5：测试策略

**选择**：保留旧代码 + 新增一致性验证测试

```java
// 在 Gateway 内部
@Deprecated
private List<Map<String, Object>> parseSignableOrdersOld(String rawJson, String projectOrderId) {
    // 旧的硬编码解析逻辑
}

// 新方法：由 YAML 驱动
private List<Map<String, Object>> parseSignableOrdersNew(String rawJson, String projectOrderId) {
    // JsonPathResolver 解析
}

// 测试：验证新旧输出一致
@Test
void shouldProduceSameOutputAsOldParser() {
    String json = mockHttpResponse();
    List<Map<String, Object>> oldResult = parseSignableOrdersOld(json, "123");
    List<Map<String, Object>> newResult = parseSignableOrdersNew(json, "123");
    assertEquals(oldResult, newResult);
}
```

**理由**：渐进式改造，验证通过后再删除旧代码

---

## Risks / Trade-offs

- **[风险] 数组展平时外层字段继承逻辑复杂** → 缓解：在 YAML 中明确标记 `data[].companyName` 这种路径，解析器自动处理继承
- **[风险] 测试覆盖不足导致新旧输出不一致** → 缓解：基于真实接口返回构造测试用例，覆盖边界情况（空数组、null 值等）
- **[Trade-off] YAML 配置较多** → 现状可接受，每个字段一行，10+ 字段的工作量可接受

---

## Migration Plan

1. 设计 YAML source 语法并确定试点 Gateway（已完成）
2. 实现 `JsonPathResolver` 解析引擎
3. 修改 `domain-ontology.yaml`，为 FormalSignableOrderInfo 和 PersonalSignableOrderInfo 添加 source 配置
4. 改造 `FormalSignableOrderInfoGateway`：保留旧方法，新增 YAML 驱动方法，标记 `@Deprecated`
5. 改造 `PersonalSignableOrderInfoGateway`：同上
6. 新增一致性验证测试，运行通过后删除旧代码
7. 运行集成测试 `QaPairEvaluationIT` 验证无回归

---

## Open Questions

（无）