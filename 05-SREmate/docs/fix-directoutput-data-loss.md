# DirectOutput 数据丢失与智能合并修复

## 问题描述

用户查询订单合同数据时：
1. 多个工具被调用，但只输出第一个工具的结果（数据丢失）
2. 输出是平铺结构，没有按本体论关系嵌套组织

### 修复前现象
```
调用工具：queryContractListByOrderId → 有数据
调用工具：queryContractSignedObjects → 有数据（3次）

最终输出：[合同数据, 关联数据, 关联数据, 关联数据]  // 平铺，丢失部分数据
```

### 修复后期望
```json
[{
  "contract_code": "C1773200928952999",
  "type": 8,
  "contract_quotation_relation": [
    {"bill_code": "GBILL...", "bind_type": 1},
    {"bill_code": "S152...", "bind_type": 3}
  ],
  "contract_node": [],
  "contract_field": {}
}]
```

## 根本原因

1. **数据丢失**：`DirectOutputHolder.setIfAbsent()` 只保留第一个结果
2. **平铺结构**：没有按本体论关联关系嵌套组织

## 修复方案

### 核心改造：智能合并

`DirectOutputHolder` 新增智能合并逻辑：

```java
public synchronized void append(String output) {
    String type = detectType(output);  // 自动检测结果类型
    results.add(new ToolResult(type, output));
}

private String trySmartMerge() {
    // 1. 分类：contract / contract_quotation_relation / contract_node / contract_field
    // 2. 以 contract 为主表
    // 3. 按 contract_code 嵌套关联数据
    // 4. 返回嵌套结构的 JSON
}
```

### 类型自动检测

```java
private String detectType(String json) {
    if (contains "bill_code") return "contract_quotation_relation";
    if (contains "node_type" || "fire_time") return "contract_node";
    if (contains "_shardTable") return "contract_field";
    if (contains "platform_instance_id") return "contract";
    return "unknown";
}
```

## 扩展设计：配置驱动的通用合并

为支持未来扩展（报价单、订单等），新增 `OntologyMergeConfig` 配置类：

```java
@Component
public class OntologyMergeConfig {
    // 定义实体间的关联关系
    EntityConfig contractConfig = new EntityConfig(
        "contract",                                    // 主表类型
        List.of("contract_code", "contractCode"),      // 关联键
        List.of(
            new RelationConfig("contract_quotation_relation", "contract_quotation_relation", ONE_TO_MANY),
            new RelationConfig("contract_node", "contract_node", ONE_TO_MANY),
            new RelationConfig("contract_field", "contract_field", ONE_TO_ONE)
        )
    );

    // 未来可扩展
    // EntityConfig orderConfig = new EntityConfig("order", ...);
    // EntityConfig quotationConfig = new EntityConfig("quotation", ...);
}
```

### 扩展步骤

1. 在 `OntologyMergeConfig` 中添加新实体配置
2. 在 `detectType()` 中添加新类型的识别规则
3. 无需修改合并算法，自动支持新实体

## 修改文件

| 文件 | 变更 |
|------|------|
| `DirectOutputHolder.java` | 智能合并逻辑，按本体论嵌套 |
| `OntologyMergeConfig.java` | 新增：配置驱动的实体关系定义 |
| `ObservabilityAspect.java` | 调用 `append()` |
| `DirectOutputHolderTest.java` | 新增：智能合并单元测试 |

## 验证

```bash
# 单元测试
mvn test -Dtest=DirectOutputHolderTest

# 集成测试
mvn test -Dtest=ContractQueryToolIT
```

修复时间：2026-03-14
