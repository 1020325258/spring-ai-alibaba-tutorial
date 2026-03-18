# SREmate 开发规范

注意：
1. 开发完进行测试，保证功能正常实现。
2. **每次代码变更后必须运行全部测试**，确保已有功能不被破坏：
   - **特别注意**：修改 Java 代码逻辑时，必须同步检查 `sre-agent.md` 提示词是否与代码保持一致（见下方反思记录）。
   ```bash
   ./run-integration-tests.sh
   # 或直接运行
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
   mvn test
   ```

---

## 测试清单

| 测试类 | 测试数 | 用途 |
|--------|--------|------|
| QueryScopeTest | 7 | QueryScope 枚举解析 |
| EntityRegistryTest | 10 | 实体注册中心路径查找 |
| OntologyQueryEngineTest | 9 | 本体论查询引擎核心逻辑 |
| ToolExecutionTemplateTest | 3 | 工具执行模板 |
| ToolResultTest | 4 | 统一结果类 |
| ObservabilityAspectAnnotationTest | 2 | AOP 注解测试 |
| **总计** | **35** | - |

运行全量测试：
```bash
./run-integration-tests.sh
```

---

## QueryScope 枚举类（v2.4 新增）

### 定义

```java
public enum QueryScope {
    DEFAULT("default", "默认展开"),
    LIST("list", "仅返回起始实体"),
    CONTRACT("Contract", "展开到合同实体"),
    CONTRACT_NODE("ContractNode", "展开到合同节点"),
    // ... 更多实体
}
```

### 使用方式

```java
// 字符串版本（向后兼容）
Map<String, Object> result = engine.query("Order", "825123110000002753", "Contract");

// 枚举版本（类型安全）
Map<String, Object> result = engine.query("Order", "825123110000002753", QueryScope.CONTRACT);

// 解析字符串
QueryScope scope = QueryScope.fromString("ContractNode");  // -> CONTRACT_NODE
```

---

## DirectOutput 机制（核心优化）

### 原理

数据查询类工具（标记 `@DataQueryTool`）的结果绕过 LLM 二次处理，直接输出给用户。

```
传统流程：用户提问 → LLM 意图识别 → 工具执行 → LLM 归纳输出 → 用户
优化流程：用户提问 → LLM 意图识别 → 工具执行 → 直接输出 → 用户
```

### 性能提升

| 场景 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 单合同查询 | 8秒 | 1-2秒 | **4-8倍** |
| 订单→多合同→关联数据 | 16-20秒 | 2-4秒 | **5-10倍** |

### 耗时分析

测试输出示例：
```
⏱ 首字节: 1198ms | 工具耗时: 419ms | 总耗时: 1198ms
[DirectOutput] ✓ 已生效，绕过 LLM 处理
```

**关键优化**：DirectOutput 生效后立即 `dispose()` 终止流，总耗时 = 首字节时间。

- **首字节时间（ttfb）**：1-2.5秒，LLM 意图识别 + 工具调用决策（Qwen API 延迟）
- **工具耗时**：100-500ms，本体论引擎并行查询数据库
- **总耗时**：与首字节时间一致，无额外等待

### 使用方式

1. **工具方法标记注解**：
```java
@Tool(description = "...")
@DataQueryTool  // 标记后，结果直接输出
public String ontologyQuery(String entity, String value, String queryScope) {
    // ...
}
```

2. **测试类自动支持**：
```java
// BaseSREIT.ask() 已内置 DirectOutput 支持
ask("825123110000002753下的合同数据");
// 输出: ⏱ 首字节: 1506ms | 工具耗时: 414ms | 总耗时: 17361ms
// [DirectOutput] ✓ 已生效
```

### ⚠️ @DataQueryTool 注解使用规范（重要）

**核心原则**：`@DataQueryTool` 只能标记在**最外层的用户直调工具**上，内部工具禁止标记。

```
用户 → LLM 调用 → ontologyQuery (有 @DataQueryTool) → DirectOutput 输出
                    ↓ 内部调用
                    callPredefinedEndpoint (无 @DataQueryTool)
                    ↓
                    其他 Gateway 工具 (无 @DataQueryTool)
```

**为什么必须这样？**

DirectOutput 机制会捕获**第一个**带有 `@DataQueryTool` 注解的工具结果并直接输出。如果内部工具（如 `callPredefinedEndpoint`）也有此注解，会导致：

| 错误场景 | 后果 |
|---------|------|
| `ontologyQuery` 内部调用 `callPredefinedEndpoint` | DirectOutput 捕获 `callPredefinedEndpoint` 的中间结果，而非 `ontologyQuery` 的最终结果 |
| 用户查询报价单子单 | 输出只有报价单列表，丢失了子单数据 |

**正确示例**：

```java
// ✅ 正确：最外层工具标记注解
@Tool(description = "本体论统一查询入口")
@DataQueryTool
public String ontologyQuery(String entity, String value, String queryScope) {
    // 内部调用其他工具，结果会被 DirectOutput 捕获
}

// ✅ 正确：内部工具不标记注解
// 注意：不添加 @DataQueryTool，因为此方法常被其他工具内部调用
// DirectOutput 应该只捕获最外层工具的结果
public String callPredefinedEndpoint(String endpointId, Map<String, String> params) {
    // 这个方法被 Gateway 内部调用，不应触发 DirectOutput
}
```

**设计原则**：

> 用户所有的查询请求都只触发 `ontologyQuery` 工具，其余工具仅在内部调用。

---

## 项目结构

```
src/main/resources/
  endpoints/          # HTTP 接口模板（YAML）
  prompts/            # LLM 系统提示词
  skills/             # SRE 知识库（Markdown）
  ontology/           # 本体论定义（YAML）
src/main/java/.../
  trigger/agent/      # @Tool 工具类（按业务领域拆分）
    ├── OntologyQueryTool.java   # 本体论统一查询入口（推荐）
    ├── ContractQueryTool.java   # 合同查询（旧工具，逐步废弃）
    └── PersonalQuoteTool.java   # 个性化报价查询
  domain/ontology/    # 本体论领域（核心）
    ├── model/        # 本体模型（OntologyEntity、OntologyRelation）
    ├── service/      # 实体注册中心
    ├── engine/       # 查询引擎（Gateway、Executor）
    └── gateway/      # 实体数据网关实现
  infrastructure/
    ├── annotation/    # 注解（@DataQueryTool）
    ├── client/        # HTTP 客户端（HttpEndpointClient）
    └── service/       # 基础设施服务（ToolExecutionTemplate、ToolResult）
  config/              # Spring 配置（AgentConfiguration、DataSourceConfiguration）
  aspect/              # AOP 切面（ObservabilityAspect）
```

---

## 本体论驱动查询引擎

### 核心概念

SREmate 采用**本体论驱动**的架构，LLM 只需调用 `ontologyQuery` 工具，引擎自动分析依赖并并行执行查询。

### 架构优势

| 传统方式 | 本体论驱动 |
|---------|-----------|
| LLM 串行调用多个工具 | LLM 调用一次 `ontologyQuery` |
| 34秒完成查询 | ~3秒完成查询 |
| 新增实体需修改多处代码 | 只需添加 Gateway 实现和 YAML 配置 |

### 使用方式

```java
// LLM 调用 - 默认只返回实体列表，不展开关联
ontologyQuery(entity="Order", value="825123110000002753")

// 返回结果（仅合同列表）
{
  "queryEntity": "Order",
  "queryValue": "825123110000002753",
  "records": [{"contractCode": "C1767150648920281", ...}]
}

// 指定 queryScope 展开关联数据
ontologyQuery(entity="Order", value="825123110000002753", queryScope="ContractNode,ContractQuotationRelation")

// 返回结果（包含指定的关联数据）
{
  "queryEntity": "Order",
  "contracts": [
    {
      "contractCode": "C1767150648920281",
      "nodes": [...],
      "signedObjects": [...]
    }
  ]
}
```

### 多目标查询（v2.3 新增）

`queryScope` 支持逗号分隔多个目标实体，只查询用户需要的数据：

```java
// 用户只要"签约单据和节点"
ontologyQuery(entity="Order", value="825123110000002753", queryScope="ContractNode,ContractQuotationRelation")

// 只返回 nodes 和 signedObjects，不查 fields、form、config
{
  "queryEntity": "Order",
  "contracts": [
    {
      "contractCode": "C1767150648920281",
      "nodes": [...],
      "signedObjects": [...]
      // 无 fields、form、config
    }
  ]
}
```

**性能对比**：

| 场景 | 优化前（scope=default） | 优化后（scope=ContractNode,ContractQuotationRelation） |
|------|------------------------|-------------------------------------------------------|
| 用户要"签约单据和节点" | 查询所有关联数据，36秒+ | 只查 nodes、signedObjects，2-3秒 |

### 新增实体 SOP（数据驱动化）

> **改造后，新增实体只需两步，无需修改 OntologyQueryTool 和 OntologyQueryEngine！**

#### 第一步：定义实体和关系（YAML）

在 `src/main/resources/ontology/domain-ontology.yaml` 添加：

```yaml
entities:
  - name: NewEntity                    # 实体名称（大驼峰）
    displayName: "新实体中文名"         # 中文显示名，注入 LLM 提示词
    aliases: ["别名1", "别名2"]         # 中文别名列表，帮助 LLM 理解
    description: "实体描述"
    table: table_name                  # 数据库表名（可选）
    lookupStrategies:                  # 查询入口策略（多格式支持）
      - field: contractCode
        pattern: "^C\\d+"              # 正则匹配 value 格式
      - field: projectOrderId
        pattern: "^\\d{15,}$"
    attributes:
      - { name: id, type: string, description: "主键" }

relations:
  - from: Contract                     # 从 Contract 出发
    to: NewEntity                      # 到 NewEntity
    label: has_new_entities            # 关系标签（自动推导 resultKey）
    via: { source_field: contractCode, target_field: contractCode }
```

**lookupStrategies 说明**：
- 引擎按顺序匹配 pattern，第一个命中的 strategy 决定传给 Gateway 的 fieldName
- 支持多格式入口，如合同号(C前缀)和订单号(纯数字)都能查询

**resultKey 推导规则**：
- `has_new_entities` → `newEntities`
- 去掉 `has_` 前缀，snake_case 转 camelCase

#### 第二步：实现 Gateway

在 `domain/ontology/gateway/` 创建 `NewEntityGateway.java`：

**数据库查询方式：**
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class NewEntityGateway implements EntityDataGateway {

    private final JdbcTemplate jdbcTemplate;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() { registry.register(this); }

    @Override
    public String getEntityName() { return "NewEntity"; }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[NewEntityGateway] queryByField: {} = {}", fieldName, value);
        return jdbcTemplate.queryForList("SELECT * FROM table WHERE " + fieldName + " = ?", value);
    }
}
```

**HTTP 接口方式：**
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class NewEntityGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() { registry.register(this); }

    @Override
    public String getEntityName() { return "NewEntity"; }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[NewEntityGateway] queryByField: {} = {}", fieldName, value);
        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("endpoint-id",
                    Map.of("paramName", value));
            return parseAndTransform(json);
        } catch (Exception e) {
            log.warn("[NewEntityGateway] 查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseAndTransform(String json) {
        // 解析 JSON 并转换为 List<Map>
    }
}
```

#### 第三步：更新 LLM 提示词

在 `prompts/sre-agent.md` 更新：

1. **场景表**：添加 `| **新实体关键词** | ontologyQuery | entity=NewEntity |`
2. **queryScope 参数**：添加 `NewEntity: 新实体描述`
3. **示例**：添加调用示例

#### 第四步：添加集成测试

在 `ContractOntologyIT.java` 添加：

```java
@Test
void newEntity_shouldCallOntologyQuery() {
    ask("C1767173898135504的新实体数据");
    assertToolCalled("ontologyQuery");
    assertAllToolsSuccess();
}
```

#### 第五步：运行测试验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn test -Dtest=ContractOntologyIT
```

---

## 环境配置

### 环境切换

通过 `/env` 命令查看和切换当前环境。

**可用环境：**
- `nrs-escrow`：测试环境（默认）
- `offline-beta`：基准环境

**新增环境：** 在 `EnvironmentConfig.AVAILABLE_ENVIRONMENTS` 中添加。

### 接口模板环境占位符

测试环境接口使用 `${env}` 占位符：

```yaml
urlTemplate: "http://utopia-nrs-sales-project.${env}.ttb.test.ke.com/api/..."
```

**域名格式：** `http://服务名.${env}.ttb.test.ke.com/api/...`

---

## 敏感配置管理

- `application.yml` 只存占位符默认值（可提交 git）
- 真实的数据库地址、账号密码写在 `application-local.yml`（已被 `.gitignore` 忽略）

---

## 新增 HTTP 接口

在 `src/main/resources/endpoints/` 对应分类的 YAML 文件中追加，无需修改 Java 代码。

### GET 接口

```yaml
- id: your-endpoint-id
  name: 接口名称
  description: 用自然语言描述用途和触发场景，LLM 依赖此字段决策是否调用。
  category: contract
  urlTemplate: "http://host/path?param=${paramName}"
  method: GET
  parameters:
    - name: paramName
      type: string
      description: 参数说明
      required: true
      example: "示例值"
  headers:
    X-NRS-User-Id: "1000000000000000"
  timeout: 15
  examples:
    - "用户可能问的问题示例"
```

### POST 接口

```yaml
- id: your-endpoint-id
  name: 接口名称
  method: POST
  urlTemplate: "http://host/path"
  requestBodyTemplate: '{"paramName":"${paramName}"}'   # POST body 模板
  responseFields:                                        # 响应字段过滤（可选）
    arrayField1:
      - fieldA
      - fieldB
  parameters:
    - name: paramName
      type: string
      required: true
  headers:
    Content-Type: "application/json"
```

**行为说明：**
- `requestBodyTemplate`：`${paramName}` 被实际值替换，构建 JSON body
- `responseFields`：配置后返回过滤后的纯 JSON，只保留指定字段

---

## 新增专用工具

### 推荐方式：使用 OntologyQueryTool

对于合同相关的查询，优先使用 `ontologyQuery`，无需新增工具方法。

### 传统方式：封装专用 `@Tool` 方法

根据业务领域，在对应的工具类中封装专用方法：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class XxxTool {

    private final HttpEndpointClient httpEndpointClient;

    @Tool(description = """
            【xxx查询】用户提到"xxx"时使用。
            触发条件：包含关键词"xxx"
            参数：projectOrderId（纯数字订单号，必填）
            示例："826031111000001859的xxx" → projectOrderId=826031111000001859""")
    @DataQueryTool  // 标记为数据查询工具，结果直接输出
    public String queryXxxList(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryXxxList", () ->
            httpEndpointClient.callPredefinedEndpointFiltered("your-endpoint-id",
                Map.of("projectOrderId", projectOrderId))
        );
    }
}
```

### @DataQueryTool 注解使用判断

**添加注解**：最外层、用户直接调用的数据查询工具

```java
@DataQueryTool  // 标记后，工具结果直接输出，绕过 LLM 归纳
public String queryXxxList(...) { ... }
```

**不添加注解**：内部工具、被其他工具调用的方法

```java
// 不添加 @DataQueryTool，避免中间结果覆盖最终结果
public String callPredefinedEndpoint(String endpointId, Map<String, String> params) { ... }
```

**判断标准**：该工具是否会被 LLM 直接调用？
- ✅ 是 → 添加 `@DataQueryTool`
- ❌ 否（仅被其他工具内部调用）→ 不添加

---

## 工具日志规范

| 层级 | 日志内容 | 示例 |
|------|---------|------|
| AOP 层 | 入口：工具名 + 参数 | `[TOOL] ontologyQuery(entity=Order, value=xxx)` |
| 工具层 | 结果：耗时 + 摘要 | `[TOOL] ontologyQuery → 133ms, ok` |

**结果摘要规则：**
- SQL 查询：`N rows` 或 `0 rows (not found)`
- HTTP 请求：`status=200` 或 `error: xxx`

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

### 测试原则

**验证工具调用行为**，而非输出内容。测试更稳定，不受业务数据变化影响。

### 断言方法

| 方法 | 说明 |
|------|------|
| `assertToolCalled(toolName)` | 断言指定工具被调用且成功 |
| `assertToolNotCalled(toolName)` | 断言指定工具未被调用 |
| `assertAllToolsSuccess()` | 断言所有工具调用都成功 |

### 测试示例

```java
@Test
void orderContract_allData_shouldCallOntologyQuery() {
    ask("825123110000002753下的合同数据");
    assertToolCalled("ontologyQuery");
    // 禁止调用旧的工具
    assertToolNotCalled("queryContractsByOrderId");
    assertAllToolsSuccess();
}
```

### 测试检查清单

新增 @Tool 后，测试必须覆盖：
1. **意图识别**：验证 Agent 根据输入格式选择正确的工具
2. **关键词触发**：验证关键词能触发对应工具
3. **互斥验证**：验证相似输入不会触发错误的工具

运行测试：
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn test -Dtest=ContractOntologyIT
```

---

## 基础设施组件

### ToolExecutionTemplate（工具执行模板）

统一处理计时、日志、异常，消除重复的 try-catch-log 模式：

```java
public static String execute(String toolName, ToolAction action)
```

### ToolResult（统一结果类）

统一的 JSON 结果格式：

```java
ToolResult.success(data)     // 成功结果
ToolResult.error(message)    // 错误结果 {"error":"xxx"}
ToolResult.notFound("合同", "C123")  // 资源未找到
```

### @DataQueryTool（数据查询工具注解）

标记工具为数据查询工具，结果直接输出，绕过 LLM 归纳。

---

## 工具类职责划分

| 组件 | 位置 | 职责 | @DataQueryTool |
|------|------|------|----------------|
| `OntologyQueryTool` | trigger/agent | 本体论统一查询入口 | ✅ 有 |
| `ContractQueryTool` | trigger/agent | 合同数据查询 | ✅ 有（逐步废弃） |
| `PersonalQuoteTool` | trigger/agent | 个性化报价查询 | ✅ 有 |
| `HttpEndpointClient` | infrastructure/client | HTTP 接口调用基础设施 | ❌ 无 |

**说明**：`HttpEndpointClient` 是基础设施组件，不作为 Agent 工具暴露，仅供 Gateway 和 Tool 内部调用。

---

## ⚠️ 反思记录：代码与提示词同步问题

### 问题复盘（2026-03-17）

**现象**：`ContractOntologyIT#contractForm_shouldCallOntologyQuery` 测试失败。
LLM 传递 `queryScope=form`，但代码已删除 `SCOPE_ALIAS` 简写映射，导致 `找不到路径: Contract -> form`。

**根本原因**：删除 `OntologyQueryTool` 中的 `SCOPE_ALIAS`（`form→ContractForm`、`config→ContractConfig` 等简写）时，**只更新了 Java 代码，未同步更新 `sre-agent.md` 提示词**。提示词的决策表和示例中仍然写着 `queryScope=form`、`queryScope=config`，导致 LLM 按旧格式调用工具。

**教训与规范**：

> **凡是修改工具参数的合法取值范围（增删枚举值、删除简写别名、重命名参数），必须同步检查并更新 `sre-agent.md`。**

具体检查清单：
1. 删除/新增 `SCOPE_ALIAS` 或类似映射 → 更新提示词决策表中对应的 `queryScope` 值
2. 新增 `@Tool` 方法 → 在提示词的"可用工具"和"快速决策表"中补充
3. 修改工具参数名或含义 → 更新提示词中的参数说明和示例
4. 修改完提示词后 → **必须运行 `ContractOntologyIT` 集成测试验证 LLM 行为**

---

## HikariCP 连接池配置

**告警识别：** `Failed to validate connection ... (No operations allowed after connection closed.)` → `max-lifetime` 大于 MySQL `wait_timeout`，需调小。

**参数约束（必须满足）：**
- `idle-timeout` < `max-lifetime`
- `max-lifetime` < MySQL `wait_timeout` - 30s

**查询 MySQL wait_timeout：** `SHOW VARIABLES LIKE 'wait_timeout';`

**当前配置（application.yml）：**
- `idle-timeout: 180000`（3 分钟）
- `max-lifetime: 300000`（5 分钟）
- `keepalive-time: 60000`（1 分钟心跳，防止 MySQL 提前关闭空闲连接）
