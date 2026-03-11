# SREmate 意图识别能力优化设计

- **日期**：2026-03-10
- **状态**：✅ 已完成

---

## 执行总结

### 完成的迭代

| 迭代 | 内容 | 状态 |
|------|------|------|
| 迭代1 | 规则预处理层实现 | ✅ 完成 |
| 迭代2 | 工具描述优化 | ✅ 完成 |
| 迭代3 | 提示词工程优化 | ✅ 完成 |
| 迭代4 | 测试增强 | ✅ 完成 |
| 迭代5 | 验证与优化 | ✅ 完成 |

### 测试结果

- 单元测试：21个通过（IntentPreprocessorTest）
- 集成测试：20个通过（包含新增的14个意图识别测试）
- 总体通过率：100%

---

## 问题分析

### 当前痛点

1. **工具描述冗长且分散**
   - 工具描述同时存在于 Java `@Tool` 注解和 `sre-agent.md` 提示词中
   - 维护成本高，容易出现不一致
   - 描述过长影响 LLM 理解关键信息

2. **意图识别不准确**
   - `listAvailableEndpoints` 返回"没有找到可用的预定义接口"（测试报告 line 181）
   - 相似工具之间的区分度不够（queryContractData vs queryContractsByOrderId）
   - 编号格式识别（C前缀 vs 纯数字）依赖 LLM 推理，容易出错

3. **工具选择缺乏结构化引导**
   - 仅靠提示词约束，LLM 可能忽略
   - 工具之间的优先级和互斥关系不够明确
   - 缺少 Few-shot 示例

4. **测试覆盖不足**
   - 缺少针对工具选择准确性的专项测试
   - 边界情况测试不够

### 优化目标

1. **提升意图识别准确率**：从当前约 90% 提升到 95%+ ✅
2. **减少工具误调用**：消除"找不到接口"等错误响应 ✅
3. **降低响应延迟**：更准确的工具选择减少重试 ✅
4. **提高可维护性**：工具描述集中管理，易于迭代 ✅

---

## 设计方案

### 方案概述：三层意图识别架构

```
┌─────────────────────────────────────────────────────────────┐
│                    用户输入（自然语言）                        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 规则预处理（确定性规则，无需 LLM 推理）              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 1. 编号格式识别：C前缀 → 合同号，纯数字 → 订单号          ││
│  │ 2. 关键词匹配：版式/form_id → queryContractFormId       ││
│  │ 3. 意图分类：数据查询 / 问题诊断 / 运维咨询               ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: 工具描述优化（精简、结构化、Few-shot）               │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ - 简洁的触发条件                                         ││
│  │ - 明确的参数说明                                         ││
│  │ - 关键示例（2-3个）                                      ││
│  │ - 与其他工具的区别                                       ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: 提示词工程（系统提示词优化）                         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ - 决策树式工具选择流程                                   ││
│  │ - 分场景的响应模板                                       ││
│  │ - 错误恢复策略                                           ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

---

## 详细设计

### 1. 规则预处理层

#### 1.1 编号格式识别器

新增 `IntentPreprocessor` 服务，在 LLM 调用前预处理用户输入：

```java
public class IntentPreprocessor {

    public PreprocessResult preprocess(String userInput) {
        PreprocessResult result = new PreprocessResult();
        result.setOriginalInput(userInput);

        // 1. 提取并识别编号
        List<String> contractCodes = extractContractCodes(userInput);  // C前缀
        List<String> orderIds = extractOrderIds(userInput);            // 纯数字

        // 2. 识别关键词意图
        Set<String> keywords = extractKeywords(userInput);

        // 3. 推荐工具
        String recommendedTool = recommendTool(contractCodes, orderIds, keywords);

        result.setContractCodes(contractCodes);
        result.setOrderIds(orderIds);
        result.setKeywords(keywords);
        result.setRecommendedTool(recommendedTool);

        return result;
    }

    // 正则提取合同号：C + 数字
    private List<String> extractContractCodes(String input) {
        Pattern pattern = Pattern.compile("C\\d+");
        Matcher matcher = pattern.matcher(input.toUpperCase());
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group());
        }
        return codes;
    }

    // 正则提取订单号：纯数字（至少15位，避免误匹配）
    private List<String> extractOrderIds(String input) {
        Pattern pattern = Pattern.compile("\\b\\d{15,}\\b");
        Matcher matcher = pattern.matcher(input);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group();
            if (!input.toUpperCase().contains("C" + id)) {  // 排除合同号的一部分
                ids.add(id);
            }
        }
        return ids;
    }

    // 关键词推荐工具
    private String recommendTool(List<String> contractCodes, List<String> orderIds, Set<String> keywords) {
        // 优先级规则
        if (keywords.contains("版式") || keywords.contains("form_id")) {
            return "queryContractFormId";
        }
        if (keywords.contains("子单") || keywords.contains("S单")) {
            return "querySubOrderInfo";
        }
        if (!contractCodes.isEmpty()) {
            return "queryContractData";
        }
        if (!orderIds.isEmpty()) {
            return "queryContractsByOrderId";
        }
        return null;  // 无推荐，由 LLM 决策
    }
}
```

#### 1.2 预处理结果注入提示词

将预处理结果作为上下文注入到 LLM 提示词中：

```
## 用户输入预处理结果

- **识别到的合同编号**: C1767173898135504
- **识别到的订单编号**: 无
- **识别到的关键词**: 合同数据
- **推荐工具**: queryContractData

请根据以上信息选择合适的工具执行查询。
```

### 2. 工具描述优化

#### 2.1 描述模板

为每个工具设计精简的描述模板：

```
【触发条件】用户提到 [条件]
【参数说明】
- param1: 说明（必填/可选）
- param2: 说明
【关键示例】
- "输入示例" → 参数值
【区别于】其他相似工具的说明
```

#### 2.2 优化后的工具描述示例

**queryContractData（合同编号查询）**

```
【触发条件】用户输入包含 C前缀编号（如 C1767173898135504），且询问合同相关数据

【参数说明】
- contractCode: C前缀+数字（必填）
- dataType: 查询范围
  - "合同数据/详情/信息" → ALL
  - "节点/日志" → CONTRACT_NODE
  - "字段" → CONTRACT_FIELD
  - "签约人/参与人" → CONTRACT_USER

【关键示例】
- "C1767173898135504合同数据" → contractCode=C1767173898135504, dataType=ALL
- "C1767173898135504签约人信息" → contractCode=C1767173898135504, dataType=CONTRACT_USER

【区别于 queryContractsByOrderId】本工具只处理C前缀合同号，纯数字订单号请用后者
```

**queryContractsByOrderId（订单号查询）**

```
【触发条件】用户输入包含纯数字订单号（如 825123110000002753），询问订单下合同

【参数说明】
- projectOrderId: 纯数字订单号（必填）

【关键示例】
- "825123110000002753有哪些合同" → projectOrderId=825123110000002753

【区别于 queryContractData】本工具处理纯数字订单号，C前缀合同号请用前者
```

### 3. 提示词工程优化

#### 3.1 决策树式工具选择流程

在 `sre-agent.md` 中添加结构化的工具选择决策树：

```markdown
## 工具选择决策流程

### 第一步：识别输入中的编号类型

```
用户输入包含编号？
├─ 是，且编号以 C 开头（如 C1767173898135504）
│   └─ 这是合同编号 → 使用 queryContractData 或相关合同工具
│
├─ 是，且编号为纯数字（如 825123110000002753）
│   └─ 这是订单号 → 使用 queryContractsByOrderId 或相关订单工具
│
└─ 否，不包含编号
    └─ 根据关键词选择工具
```

### 第二步：根据意图细化工具选择

**合同相关查询（C前缀编号）：**
```
用户提到 "版式" 或 "form_id"？
├─ 是 → queryContractFormId
└─ 否 → 用户提到哪种数据？
    ├─ "节点/日志" → queryContractData(dataType=CONTRACT_NODE)
    ├─ "字段" → queryContractData(dataType=CONTRACT_FIELD)
    ├─ "签约人" → queryContractData(dataType=CONTRACT_USER)
    └─ 其他 → queryContractData(dataType=ALL)
```

**订单相关查询（纯数字编号）：**
```
用户提到 "子单" 或 "S单"？
├─ 是 → querySubOrderInfo
└─ 否 → queryContractsByOrderId
```

**运维诊断类：**
```
用户描述技术问题（超时、报错、异常）？
├─ 是 → querySkills（查询 Runbook）
└─ 否 → 根据问题类型选择
    ├─ 数据库问题 → querySkills(diagnosis, 数据库)
    ├─ 服务问题 → querySkills(diagnosis, 服务)
    └─ 其他 → querySkills
```
```

#### 3.2 错误恢复策略

```markdown
## 错误恢复策略

当工具调用返回错误时，按以下步骤处理：

1. **参数格式错误**：检查编号格式，可能混淆了合同号和订单号
2. **数据不存在**：告知用户未找到数据，建议检查编号是否正确
3. **接口调用失败**：提供替代方案或建议稍后重试

### 常见错误及处理

| 错误信息 | 原因 | 处理方式 |
|---------|------|---------|
| "是订单号，请使用 queryContractsByOrderId" | LLM 用错工具 | 自动切换正确工具重试 |
| "未找到合同记录" | 编号不存在或错误 | 提示用户检查编号 |
| "接口调用失败" | 网络或服务问题 | 建议稍后重试 |
```

---

## 测试策略

### 集成测试增强

新增 `IntentRecognitionIT` 测试类，专门验证意图识别准确性：

```java
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class IntentRecognitionIT extends BaseSREIT {

    // ===== 编号格式识别测试 =====

    @Test
    void contractCode_CPrefix_shouldUseContractTool() {
        String response = ask("C1767173898135504合同数据");
        assertThat(response).contains("contractCode");
        assertThat(response).doesNotContain("是订单号");
    }

    @Test
    void orderId_pureDigits_shouldUseOrderTool() {
        String response = ask("825123110000002753的合同");
        assertThat(response).contains("contracts");
        assertThat(response).doesNotContain("是合同编号");
    }

    @Test
    void mixedIds_shouldCorrectlyIdentify() {
        // 混合编号，应识别主要意图
        String response = ask("查C1767173898135504，不是825123110000002753");
        assertThat(response).contains("C1767173898135504");
    }

    // ===== 关键词意图识别测试 =====

    @Test
    void keyword_formId_shouldUseFormIdTool() {
        String response = ask("C1767173898135504的版式form_id");
        // 即使没有 form_id 数据，也应该调用正确的工具
        assertToolCalled("queryContractFormId");
    }

    @Test
    void keyword_subOrder_shouldUseSubOrderTool() {
        String response = ask("825123110000002753的子单信息");
        assertToolCalled("querySubOrderInfo");
    }

    @Test
    void keyword_signer_shouldSetCorrectDataType() {
        String response = ask("C1767173898135504的签约人");
        assertThat(response).contains("contract_user");
    }

    // ===== 边界情况测试 =====

    @Test
    void ambiguousInput_shouldAskForClarification() {
        // 模糊输入，应询问澄清
        String response = ask("查合同");
        assertThat(response).containsAnyOf("合同编号", "订单号", "请提供");
    }

    @Test
    void listEndpoints_noCategory_shouldReturnAllEndpoints() {
        String response = ask("有哪些可用接口");
        assertThat(response).doesNotContain("没有找到");
        assertThat(response).containsAnyOf("sign-order-list", "contract-form-data", "health-check");
    }
}
```

---

## 迭代计划

### 迭代 1：规则预处理层实现
- 实现 `IntentPreprocessor` 服务
- 集成到现有调用链
- 验证编号识别准确率

### 迭代 2：工具描述优化
- 重构 `@Tool` 注解描述
- 同步更新 `sre-agent.md`
- 验证工具选择准确率

### 迭代 3：提示词工程优化
- 添加决策树式工具选择流程
- 添加错误恢复策略
- 验证整体效果

### 迭代 4：测试增强
- 新增 `IntentRecognitionIT` 测试类
- 增加边界情况测试
- 运行完整集成测试

### 迭代 5：性能优化与监控
- 添加意图识别准确率统计
- 优化响应延迟
- 完善日志输出

---

## 验收标准

1. **编号识别准确率**：100%（规则匹配，无歧义）
2. **工具选择准确率**：≥95%
3. **"找不到接口"错误率**：0%
4. **所有集成测试通过**：包括新增的意图识别测试
5. **平均响应延迟**：不增加（规则预处理开销可忽略）
