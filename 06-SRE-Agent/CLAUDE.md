# SRE-Agent 开发规范

注意：
1. 开发完进行测试，保证功能正常实现。
2. **按变更范围选择测试**（避免不必要的 token 消耗）：

   | 变更范围 | 运行命令 |
   |---------|---------|
   | 仅修改单元测试对应的类（引擎、基础设施工具类） | `./scripts/run-unit-tests.sh` |
   | 修改 Gateway / Domain / Trigger / 提示词 | `./run-integration-tests.sh`（单元+集成） |
   | 仅修改文档、YAML 配置、注释 | 无需运行测试 |

3. **运行测试后必须检查日志**：每次运行测试后，检查 `/log/sre-agent.log` 是否存在 ERROR 或异常日志，特别是：
   - 一致性校验失败日志（"新旧方法输出一致性校验失败"）
   - 上下文未设置日志（"上下文未设置，跳过事件发送"）
   - 其他 ERROR 级别日志

   **单元测试脚本**（按需单独运行，无需外部环境）：
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
   mvn test -f ../pom.xml -pl 06-SRE-Agent \
     -Dtest="ObservabilityAspectAnnotationTest,ToolExecutionTemplateTest,ToolResultTest,QueryScopeTest,EntityRegistryTest,OntologyQueryEngineTest,PersonalQuoteGatewayTest" \
     -Dsurefire.failIfNoSpecifiedTests=false
   ```

   **集成测试脚本**（修改 Gateway / Domain / Trigger / 提示词 / 节点配置后必须运行，需真实外部环境）：
   ```bash
   ./run-integration-tests.sh
   ```
   > **重要**：集成测试的核心是 `QaPairEvaluationIT`，它从 `src/test/resources/qa-pairs/sre-agent-qa.yaml` 加载问答对，通过 LLM-as-Judge 语义评估每条问答的输出是否符合预期。每次修改代码逻辑后**必须**运行此测试，不能仅依赖单元测试。

   - **特别注意**：修改 Java 代码逻辑时，必须同步检查 `sre-agent.md` 提示词是否与代码保持一致（见下方反思记录）。
   - **git commit 前**：pre-commit hook 会自动检测 src/ 变更并运行 `./run-integration-tests.sh`，无需手动触发。

---

## 端到端测试

### 测试脚本

```bash
# 运行端到端测试（问题排查 + 数据查询 + Skill 机制）
./scripts/run-e2e-tests.sh
```

### 测试文件

| 文件 | 职责 | 说明 |
|------|------|------|
| `BaseSREAgentIT` | 端到端测试基类 | 提供 `ask()` 方法发起自然语言请求 |
| **`QaPairEvaluationIT`** | **核心集成测试（必跑）** | 从 `qa-pairs/sre-agent-qa.yaml` 加载问答对，LLM-as-Judge 语义评估，生成测试报告 |
| `InvestigateAgentIT` | 问题排查测试 | 验证 read_skill + ontologyQuery 能力 |
| `QueryAgentIT` | 数据查询测试 | 验证 ontologyQuery 参数正确性 |
| `SkillMechanismIT` | Skill 机制测试 | 验证 SkillRegistry + read_skill 工具 |

### 验证方式

- **工具调用层**：验证 LLM 调用了正确的工具（ontologyQuery、read_skill）
- **参数层**：验证工具参数正确（entity、queryScope、skillName）
- **输出层**：验证返回数据格式正确

### 测试报告

测试执行后自动生成报告：`06-SRE-Agent/docs/test-execution-report.md`

---

## 输出机制

所有输出都通过 LLM 处理并流式输出，保证输出的质量和一致性。

### 流式输出优势

- LLM 对工具返回的原始数据进行解释和总结
- 支持复杂场景的结论输出（如排查分析）
- 输出格式更友好，可读性强

### 耗时分析

首字节时间 1-2.5秒（LLM 意图识别），工具耗时 100-500ms，总耗时 = 首字节时间 + 工具耗时 + LLM 处理时间。

---

## Admin 节点智能推荐

当 RouterNode 无法识别用户意图时，请求会被路由到 admin 节点。AdminNode 会通过 LLM 分析用户输入和可用能力列表，输出语义相似的能力推荐。

### 工作流程

```
用户输入 → RouterNode → admin（无法识别时）
                         ↓
              buildAvailableCapabilities()
              (SkillRegistry + EntityRegistry)
                         ↓
              LLM 推荐相似能力
                         ↓
              "您可能想问：1. XXX 2. YYY"
```

### 能力列表来源

- **Skills**：从 `SkillRegistry.listAll()` 获取 skillName + description
- **实体**：从 `EntityRegistry` 获取 entityName + displayName + aliases

### 输出规则

| 场景 | 输出 |
|------|------|
| 有匹配能力 | "您可能想问：1. {能力描述} 2. {能力描述}" |
| 无匹配能力 | "抱歉，我无法理解您的问题。请描述您的业务需求..." |
| 输入太短（<5字符） | 直接返回默认帮助提示 |

---

## 项目结构

```
src/main/resources/
  endpoints/          # HTTP 接口模板（YAML）
  prompts/            # LLM 系统提示词
  ontology/           # 本体论定义（YAML）
src/main/java/.../
  trigger/agent/      # @Tool 工具类（按业务领域拆分）
    ├── OntologyQueryTool.java   # 本体论统一查询入口（推荐）
    ├── ContractQueryTool.java   # 合同查询（旧工具，逐步废弃）
    └── PersonalQuoteTool.java   # 个性化报价查询
  domain/ontology/    # 本体论领域（核心）
    ├── model/        # 本体模型
    ├── service/      # 实体注册中心
    ├── engine/       # 查询引擎
    └── gateway/      # 实体数据网关实现
  infrastructure/
    ├── annotation/   # 注解（@DataQueryTool）
    ├── client/       # HTTP 客户端
    └── service/      # 基础设施服务
```

---

## 本体论驱动查询引擎

### QueryScope 枚举

```java
public enum QueryScope {
    LIST("list", "仅返回起始实体"),
    CONTRACT("Contract", "展开到合同实体"),
    CONTRACT_NODE("ContractNode", "展开到合同节点"),
    // ... 更多实体
}
```

### 核心概念

LLM 只需调用 `ontologyQuery` 工具，引擎自动分析依赖并并行执行查询。

| 传统方式 | 本体论驱动 |
|---------|-----------|
| LLM 串行调用多个工具 | LLM 调用一次 `ontologyQuery` |
| 34秒完成查询 | ~3秒完成查询 |
| 新增实体需修改多处代码 | 只需添加 Gateway 和 YAML 配置 |

### 使用方式

```java
// 默认只返回实体列表
ontologyQuery(entity="Order", value="825123110000002753")

// 指定 queryScope 展开关联（支持逗号分隔多目标）
ontologyQuery(entity="Order", value="825123110000002753", queryScope="ContractNode,ContractQuotationRelation")
```

### 新增实体 SOP

#### 第一步：定义实体和关系（YAML）

在 `src/main/resources/ontology/domain-ontology.yaml` 添加：

```yaml
entities:
  - name: NewEntity
    displayName: "新实体中文名"
    aliases: ["别名1", "别名2"]
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
    attributes:
      - { name: id, type: string, description: "主键" }

relations:
  - from: Contract
    to: NewEntity
    label: has_new_entities
    via: { source_field: contractCode, target_field: contractCode }
```

#### 第二步：实现 Gateway

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class NewEntityGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() { registry.register(this); }

    @Override
    public String getEntityName() { return "NewEntity"; }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        // value 必须先做 null 检查，再调用 toString()
        if (value == null) {
            log.warn("[NewEntityGateway] fieldName={} 的值为 null，无法查询", fieldName);
            return Collections.emptyList();
        }
        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("endpoint-id", Map.of("paramName", value));
            return parseAndTransform(json);
        } catch (Exception e) {
            // 必须用 log.warn("msg", e) 保留完整堆栈，禁止用 e.getMessage()
            log.warn("[NewEntityGateway] 查询失败", e);
            return Collections.emptyList();
        }
    }
}
```

#### 第三步：更新 LLM 提示词

在 `prompts/sre-agent.md` 更新场景表、queryScope 参数说明和调用示例。

#### 第四步：添加集成测试

在 `ContractOntologyIT` 中补充两层验证：

```java
@Test
void newEntity_shouldUseContractEntityAndNewEntityScope() {
    ask("C1767173898135504的新实体数据");

    // 意图识别
    assertOntologyQueryParams("Contract", "NewEntity");
    assertAllToolsSuccess();

    // 数据输出
    assertOutputField("queryEntity", "Contract");
    assertOutputHasRecords();
    assertFirstRecordHasField("关键字段名");
}
```

---

## 环境配置

### 环境切换

通过页面输入框的"当前环境"、"切换到xxx环境"等关键词查看和切换环境。可用环境：`nrs-escrow`（测试环境，默认）、`offline-beta`（基准环境）。

**接口模板占位符**：`urlTemplate: "http://服务名.${env}.ttb.test.ke.com/api/..."`

### 敏感配置

- `application.yml` 存占位符默认值（可提交 git）
- 真实配置写在 `application-local.yml`（已被 `.gitignore` 忽略）

---

## 新增 HTTP 接口

在 `src/main/resources/endpoints/` 对应 YAML 文件中追加：

```yaml
- id: your-endpoint-id
  name: 接口名称
  description: 用自然语言描述用途和触发场景
  category: contract
  urlTemplate: "http://host/path?param=${paramName}"
  method: GET
  parameters:
    - name: paramName
      type: string
      required: true
  headers:
    X-NRS-User-Id: "1000000000000000"
  # POST 接口额外字段：
  # requestBodyTemplate: '{"paramName":"${paramName}"}'
  # responseFields: { arrayField: [fieldA, fieldB] }
```

---

## 新增专用工具

### 推荐方式：使用 OntologyQueryTool

对于合同相关查询，优先使用 `ontologyQuery`，无需新增工具方法。

### 传统方式：封装专用 `@Tool` 方法

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class XxxTool {

    private final HttpEndpointClient httpEndpointClient;

    @Tool(description = """
            【xxx查询】用户提到"xxx"时使用。
            触发条件：包含关键词"xxx"
            参数：projectOrderId（纯数字订单号，必填）""")
    @DataQueryTool
    public String queryXxxList(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryXxxList", () ->
            httpEndpointClient.callPredefinedEndpointFiltered("your-endpoint-id",
                Map.of("projectOrderId", projectOrderId))
        );
    }
}
```

---

## 工具日志规范

| 层级 | 日志内容 | 示例 |
|------|---------|------|
| AOP 层 | 入口：工具名 + 参数 | `[TOOL] ontologyQuery(entity=Order, value=xxx)` |
| 工具层 | 结果：耗时 + 摘要 | `[TOOL] ontologyQuery → 133ms, ok` |

---

## 分库分表处理

`contract_field_sharding` 按合同号取模分 10 张表（`_0` 至 `_9`）。

```java
private String resolveFieldShardingTable(String contractCode) {
    String digits = contractCode.replaceAll("[^0-9]", "");
    int shard = (int) (Long.parseLong(digits) % 10);
    return "contract_field_sharding_" + shard;
}
```

---

## 更新 LLM 系统提示词

新增工具后，同步更新 `prompts/sre-agent.md`：
1. 在"可用工具"章节补充专用工具说明
2. 在决策表中明确：关键词 → 专用工具名
3. 不要暴露 `endpointId`

---

## 集成测试

### 测试文件职责

| 文件 | 职责 | 说明 |
|------|------|------|
| `ContractOntologyIT` | **核心集成测试**，验证所有查询场景 | 每次迭代必须全部通过 |
| `OntologyQueryEngineTest` | 单元测试：引擎多跳/多目标逻辑 | Mock，不依赖外部环境 |
| `PersonalQuoteGatewayTest` | 单元测试：bindType 参数映射规则 | Mock，验证纯业务规则 |
| `EntityRegistryTest` | 单元测试：YAML 解析和路径查找 | 不依赖外部环境 |

**不要创建以下类型的测试**（无独立价值，应合并或删除）：
- 只验证 Bean 注入的启动测试（隐含在所有集成测试中）
- 只验证工具名被调用、不验证参数和输出的意图识别测试（粒度太粗）
- 与 `ContractOntologyIT` 重叠的测试（合并进去）

### ContractOntologyIT 两层验证规范

每个测试方法必须覆盖两层：

```java
@Test
void contractBasic_shouldUseContractEntity() {
    ask("C1767173898135504的合同基本信息");

    // 第一层：意图识别 —— LLM 传了正确的参数？
    assertOntologyQueryParams("Contract", null);
    assertAllToolsSuccess();

    // 第二层：数据输出 —— 返回了正确的数据结构？
    assertOutputField("queryEntity", "Contract");
    assertOutputHasRecords();
    assertFirstRecordHasField("contractCode");
}
```

### 输出验证方法（BaseSREIT）

| 方法 | 验证内容 | 使用场景 |
|------|---------|---------|
| `assertOutputField(path, value)` | 顶层字段精确值 | `queryEntity`、`queryValue` 元数据 |
| `assertOutputHasRecords()` | `records` 非空 | 所有查询用例必加 |
| `assertFirstRecordHasField(path)` | 字段**存在**（不验证值） | 关键字段存在性，支持嵌套路径如 `"formData/id"` |
| `assertFirstRecordFieldEquals(path, value)` | 字段精确值 | 仅用于 ID 回显等极稳定字段 |
| `assertOutputIsInvestigationConclusion(response)` | 输出是自然语言排查结论（LLM-as-Judge） | **排查类测试必加**，使用无工具绑定的 `ChatModel` 评估，避免污染 `TracingService` |
| `assertSkillProcessCompliance(skillName, response)` | 执行过程符合 Skill 步骤（LLM-as-Judge） | **涉及 readSkill 的排查测试**，读取 SKILL.md 评估：readSkill 是否第一步、ontologyQuery 参数是否符合 Skill 步骤、输出是否四段式；不检查条件分支（Agent 可能并行调用） |

### 验证策略：宽松原则

避免维护成本剧增：

| 验证对象 | 策略 | 原因 |
|---------|------|------|
| `queryEntity` / `queryValue` | **精确匹配** | 元数据，非常稳定 |
| 关键字段是否存在 | **只验证存在** | 业务数据会变，值不稳定 |
| ID 回显字段（如 `instanceId`） | **精确匹配** | 值等于查询输入，极稳定 |
| 深层嵌套数据（如个性化报价明细） | **只验证第一层展开字段存在** | 防止深层数据变动导致脆断 |

### 新增 @Tool 后测试模板

```java
@Test
void newFeature_shouldUseCorrectTool() {
    ask("触发关键词的自然语言问题");

    // 第一层：意图识别
    assertOntologyQueryParams("EntityName", "QueryScope");  // 或 assertToolCalled
    assertAllToolsSuccess();

    // 第二层：数据输出
    assertOutputField("queryEntity", "EntityName");
    assertOutputHasRecords();
    assertFirstRecordHasField("关键字段名");
}
```

---

## 基础设施组件

| 组件 | 用途 |
|------|------|
| `ToolExecutionTemplate` | 统一处理计时、日志、异常 |
| `ToolResult` | 统一 JSON 结果格式：`success(data)`、`error(message)`、`notFound(type, id)` |

---

## 工具类职责划分

| 组件 | 职责 |
|------|------|
| `OntologyQueryTool` | 本体论统一查询入口 |
| `ContractQueryTool` | 合同数据查询（逐步废弃） |
| `PersonalQuoteTool` | 个性化报价查询 |
| `HttpEndpointClient` | HTTP 接口调用基础设施（不作为 Agent 工具暴露） |

---

## 反思记录：代码与提示词同步问题

### 问题复盘（2026-03-17）

**现象**：测试失败。LLM 传递 `queryScope=form`，但代码已删除 `SCOPE_ALIAS` 简写映射。

**根本原因**：删除 `SCOPE_ALIAS` 时，只更新了 Java 代码，未同步更新 `sre-agent.md` 提示词。

**教训**：凡是修改工具参数的合法取值范围（增删枚举值、删除简写别名、重命名参数），必须同步检查并更新 `sre-agent.md`，然后运行 `ContractOntologyIT` 集成测试验证。

### 问题复盘（2026-03-27）

**现象**：测试失败。`InvestigateAgentIT#investigate_missing_personal_quote` 断言 `readSkill` 被调用，但实际 LLM 只调用了 `ontologyQuery`，完全跳过了 `readSkill`。

**根本原因**：`readSkill` 工具已通过 `@Tool` 注解注册并绑定到 `ChatClient`，但 `sre-agent.md` 的"可用工具"章节从未描述该工具（只有一个不存在的 `querySkills` 引用）。LLM 看不到 `readSkill`，自然不会调用它。

**教训**：**工具的存在本身也必须同步到提示词。** 新增 `@Tool` 方法后，除了在"新增专用工具"章节说明用法，还必须在"可用工具"列表中补充该工具的描述（包括使用时机、参数说明和可用值列表）。缺少描述 ≈ 工具不存在。

### 问题复盘（2026-03-27 第三次）

**现象**：`InvestigateAgentIT#investigate_sales_contract_sign_dialog_no_quote_keyword` 测试失败。用户描述"发起提示无定软电报价"，LLM 却调用了 `ontologyQuery(queryScope=PersonalQuote)` 而非 `readSkill`。

**根本原因**：`sre-agent.md` 的 `readSkill` 使用时机写的是"当用户意图是'排查/诊断'时触发"，但用户描述中没有"排查""诊断"等明确关键词。同时，`sales-contract-sign-dialog-diagnosis` 的触发描述只写了"请先完成报价"，没有"无定软电报价"。LLM 看到"报价"二字，将其识别为个性化报价查询。

**教训**：**`readSkill` 的触发不能依赖"排查/诊断"等元关键词。** 症状描述（如"弹窗提示XXX""发起提示XXX"）也应触发对应技能。在 `sre-agent.md` 的 `readSkill` 章节中，必须为每个技能明确列出触发场景（包括症状短语），并说明"描述已知业务症状即触发，无需排查等关键词"。

### 问题复盘（2026-03-27 第四次）

**现象**：`assertSkillProcessCompliance` 中 judge 报告"未先调用 readSkill"，但日志显示 readSkill 确实是第一个被调用的。

**根本原因**：`TracingService` 的 `ConcurrentLinkedDeque` 以"最新在前"的顺序存储，`captureNewToolCalls` 迭代时得到的是反时间顺序（最新 → 最旧）。传给 judge LLM 的工具调用序列是倒序的，judge 自然误判。

**教训**：在 `assertSkillProcessCompliance` 中，构建工具调用序列字符串前，必须先对 `getToolCalls()` 返回的列表调用 `Collections.reverse()` 得到时间正序，再传给 judge。

### 问题复盘（2026-03-28）

**现象一**：`@SpringBootTest` 集成测试挂起，JVM 进程 30+ 分钟无任何测试输出。

**根本原因**：`SREConsole` 是 `CommandLineRunner`，实现了交互式终端输入循环（JLine `readLine()`）。`@SpringBootTest` 内部调用 `SpringApplication.run()`，它会同步执行所有 `CommandLineRunner`。`SREConsole.run()` 阻塞在 `readLine()` 上，导致 Spring 上下文启动永不完成，测试永远无法运行。

**教训**：`SREConsole` 已有 `@ConditionalOnProperty(name="sre.console.enabled", matchIfMissing=true)` 注解，只需在测试类的 `@SpringBootTest(properties="sre.console.enabled=false")` 中禁用即可。**所有使用 `@SpringBootTest` 且项目有阻塞式 CLI 的测试类必须加此配置。**

**现象二**：`SREAgentGraph.streamMessages()` 返回空流，`ask()` 得到空字符串，`assertOutputIsInvestigationConclusion` 断言失败。

**根本原因**：`Agent.streamMessages()` = `stream().transform(this::extractMessages)`，`extractMessages` 仅过滤 `StreamingOutput` 中 `outputType == AGENT_MODEL_STREAMING || AGENT_TOOL_FINISHED` 的条目。`SREAgentGraph` 的 `AgentNode` 内部用 `blockLast()` 消费了 `ReactAgent.streamMessages()` 的所有流，只向 StateGraph 状态写入一个 `Map.of("result", text)`。`StateGraph.stream()` 发出的是普通 `NodeOutput`，不是 `StreamingOutput`，因此 `extractMessages` 过滤掉所有内容，外层收不到任何文本。

**教训**：继承 `Agent` 并用 `NodeAction` 封装内部 `ReactAgent` 时，必须**覆写 `streamMessages(String)`**，直接将用户请求路由到对应的 `ReactAgent.streamMessages()` 并透传其 `Flux<Message>`。StateGraph 继续保留用于 Studio 可视化，但实际流式执行走覆写路径。具体实现见 `SREAgentGraph.streamMessages()` / `determineRouting()`。

### 问题复盘（2026-03-28 第二次）

**现象一**：Studio 页面 queryAgent 代码块显示 `undefined`。

**根本原因**：Studio 前端 `react-markdown` 在流式渲染时，收到 ` ```json ` token 后内容还未到达，`code` 组件的 `children = undefined`，`String(undefined) = "undefined"` 被写入 DOM，且 streaming 结束后不再更新（最终态仍为 `undefined`）。另一个诱因是 LLM 有时输出 ` ```json{JSON} `（无换行），导致解析器把 JSON 当作 info string 而非内容。

**修复方案**：`SREAgentGraph.streamMessages()` 查询路径改为收集全部 token、`normalizeAndPrettifyJson()` 规范化 + pretty-print 后**一次性**发送给前端，彻底绕过部分渲染问题。

**教训**：查询类 Agent 的输出（有明确结束点的 JSON）不需要 token 级流式，应先收集再发送；排查类 Agent（逐步推理）才需要 token 级流式。两种路径在 `SREAgentGraph.streamMessages()` 中已通过 `if ("query"...)` 分支分开处理。

**现象二**：改完提示词后，QueryAgentIT 测试全量失败（工具调用为零），但排查路径正常。

**根本原因**：`sre-agent.md` 输出规则中加入了带 `{工具返回的原始 JSON}` 占位符的代码块模板。LLM 把这个 `{...}` 解读为"填空题"，直接生成 JSON 文本而不调用 `ontologyQuery` 工具。

**教训**：**系统提示词中禁止使用 `{占位符}` 形式的模板示例来说明输出格式。** 应使用纯文字描述（如"用 \`\`\`json 代码块包裹"），不要用含花括号的模板变量，否则 LLM 会尝试自己填充而跳过工具调用。

---

---

## HikariCP 连接池配置

**告警识别**：`Failed to validate connection ... (No operations allowed after connection closed.)` → 需调小 `max-lifetime`。

**参数约束**：`idle-timeout` < `max-lifetime` < MySQL `wait_timeout` - 30s

**当前配置**：`idle-timeout: 180000`，`max-lifetime: 300000`，`keepalive-time: 60000`

---

## Skill 编写规范

### 触发条件

在 `sre-agent.md` 的 `readSkill` 章节中，每个技能必须：
- 列出触发词/触发场景（包括**症状短语**，不只是"排查XXX"类措辞）
- 说明"描述已知业务症状即触发，无需排查、诊断等关键词"

### 排查型 Skill 结构规范

每个排查步骤必须明确两件事：
1. **当前步骤的查询动作**（调用哪个 ontologyQuery）
2. **根据结果判断用户描述是否属实**，并明确后续动作

```
步骤 N：执行 XXX 查询
  - 若结果非空 → [用户描述属实/不属实]，[结束排查 or 继续下一步]
  - 若结果为空 → [用户描述属实/不属实]，[结束排查 or 继续下一步]
```

**核心原则**：每步查询都是"验证用户描述"的机会，应明确给出"属实/不属实"的判断，而不是模糊地说"数据正常，请确认场景"。

### 决策矩阵规范

排查型 Skill 的决策矩阵必须包含"用户描述是否属实"列：

| 步骤结果 | 后续 | 结论 | 用户描述是否属实 |
|---------|------|------|----------------|
| 步骤1 有数据 | 排查结束 | 系统数据正常，用户描述不符实际 | ❌ 不属实 |
| 步骤1 无数据 | 继续步骤2 | 用户描述属实，继续定位原因 | ✅ 属实 |

---

## OpenSpec 变更管理规范

所有文档相关操作必须遵循 OpenSpec 规范。变更完成后使用 `/opsx:archive` 归档。

**相关命令**：
```bash
openspec list --json
openspec status --change "<name>" --json
/opsx:archive <change-name>
```

**变更文件存放位置**：
- 针对特定项目的变更（如 06-SRE-Agent），应将 openspec 变更文件创建在对应项目目录下（如 `06-SRE-Agent/openspec/changes/`）
- **禁止**先在根目录创建变更，再移动到项目目录
- 原因：避免变更文件散落在根目录，保持项目目录的完整性

---

## 统一事件分发机制（2026-04-01）

### 背景

重构前 SSE 事件发送逻辑分散在 3 处：
1. **Router 节点**（SREAgentGraphProcess.resolveContent）：发送路由决策的 thinking 事件
2. **ObservabilityAspect**：在 @Tool 方法调用后直接发送 thinking 事件
3. **QueryAgent 节点**（SREAgentGraphProcess.resolveContent）：发送 conclusion 事件

### 设计原则

参考 DeepResearch 项目，采用 **nodeName 驱动** 的统一事件分发：
- 后端：基于 `nodeName` 差异化构建 SSE 事件
- 前端：使用 `findNode(nodeName)` 差异化解析

### 核心组件

| 组件 | 职责 |
|------|------|
| `SREAgentNodeName` 枚举 | 定义所有 Agent 节点名称和 displayTitle 映射 |
| `SREAgentEventDispatcher` | 统一事件分发器，基于 nodeName 构建差异化事件 |
| `ThinkingContextHolder` | 线程无关的上下文持有者，支持工具事件列表收集 |
| `ThinkingEvent` | 事件数据结构，移除 stepNumber，使用 nodeName/displayTitle |

### 事件结构

```json
// router 节点
{"nodeName": "router", "displayTitle": "意图识别", "stepTitle": "路由至 queryAgent", ...}

// 工具调用
{"nodeName": "tool_call", "displayTitle": "工具调用", "stepTitle": "ontologyQuery", ...}

// queryAgent
{"nodeName": "queryAgent", "displayTitle": "数据查询", "content": "..."}

// investigateAgent / admin
{"nodeName": "investigateAgent", "displayTitle": "问题排查", "content": "..."}
```

### 事件发送流程

```
@Tool 方法执行 → ObservabilityAspect 收集到 ThinkingContextHolder 
            → Agent 节点完成 → SREAgentEventDispatcher 统一发送
```

### 前端解析

```typescript
// 基于 nodeName 差异化解析
if (jsonData.nodeName === 'tool_call') {
  // 处理工具调用事件
} else if (jsonData.nodeName === 'queryAgent') {
  // 处理结论事件
}
```
