## Context

### 当前架构

```
YAML (source/flattenPath) → EntitySchemaMapper → Gateway 返回
```

存在两套实现模式：
- **YAML 驱动模式**：BudgetBill, PersonalQuote, SubOrder 等使用 EntitySchemaMapper
- **硬编码模式**：Contract, ContractNode 等直接解析

### 问题

1. EntitySchemaMapper 引入额外抽象层，学习 Source 语法成本高
2. 复杂转换（解密、额外查询）仍需硬编码，无法统一
3. 两套模式并存，认知负担大
4. YAML 定义与实际返回不一致

### 目标架构

```
YAML (属性定义) ← 作为规范文档
       ↓
Gateway (查询数据 + 按规范组装返回)
```

## Goals / Non-Goals

**Goals:**
- 移除 EntitySchemaMapper 和 JsonPathResolver
- 统一所有 Gateway 实现：直接编写解析逻辑
- YAML 作为属性规范文档，Gateway 实现遵循
- 提取通用工具方法减少重复代码
- 确保代码可读性，持续重构消除坏味道

**Non-Goals:**
- 不改变实体属性的业务含义
- 不修改 HTTP 接口调用逻辑
- 不改变 OntologyQueryEngine 的调用方式

## Decisions

### 1. Gateway 实现模式

**决定**：Gateway 直接编写解析逻辑，按 YAML 定义的属性组装返回

**替代方案**：
- EntitySchemaMapper 扩展更多能力 → 过度复杂，变成脚本引擎
- 完全依赖硬编码 → YAML 失去规范作用

**理由**：
- 代码直观易调试
- 灵活处理复杂转换（解密、额外查询）
- 无需学习额外语法

### 2. YAML 格式简化

**决定**：移除 `source`、`flattenPath` 等配置，仅保留属性声明

```yaml
# 简化后格式
attributes:
  - name: contractCode
    type: string
    description: "合同编号"
  - name: nodeType
    type: int
    description: "节点类型"
```

**理由**：
- YAML 定位为规范文档，不再驱动代码
- 属性定义清晰，易于理解
- 减少配置复杂度

### 3. 通用工具方法

**决定**：提取 `JsonMappingUtils` 工具类

```java
public class JsonMappingUtils {
    public static String getText(JsonNode node, String path);
    public static Integer getInt(JsonNode node, String path);
    public static String formatDateTime(JsonNode node, String path);
}
```

**理由**：
- 减少重复代码
- 统一处理空值、类型转换
- 保持代码简洁

### 4. 改造顺序

**决定**：先改造已使用 EntitySchemaMapper 的 Gateway，再改造硬编码 Gateway

**顺序**：
1. BudgetBillGateway, PersonalQuoteGateway, SubOrderGateway
2. PersonalSignableOrderInfoGateway, FormalSignableOrderInfoGateway
3. ContractGateway, ContractNodeGateway, ContractQuotationRelationGateway
4. ContractUserGateway, ContractFieldGateway, ContractInstanceGateway, ContractConfigGateway

**理由**：
- 先移除 EntitySchemaMapper 依赖
- 后修正不一致的属性定义

## Risks / Trade-offs

### 风险：YAML 与代码不同步
→ 缓解：添加单元测试验证返回字段与 YAML 定义一致

### 风险：改造过程中引入 Bug
→ 缓解：每个 Gateway 改造后运行测试验证

### 风险：重复代码增加
→ 缓解：持续重构，提取通用方法

## Migration Plan

### 阶段一：移除 EntitySchemaMapper 依赖
1. 改造 BudgetBillGateway 等 5 个 Gateway
2. 移除 EntitySchemaMapper、JsonPathResolver
3. 清理依赖注入

### 阶段二：修正属性定义
1. 补充/修正 YAML 中各实体属性定义
2. 改造剩余 Gateway
3. 确保返回字段与 YAML 一致

### 阶段三：验证
1. 单元测试验证每个 Gateway
2. 集成测试验证完整链路

## Open Questions

- 是否需要自动检查 YAML 与代码一致性？（可选：后续添加）
