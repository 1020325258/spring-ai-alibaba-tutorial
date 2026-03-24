# SRE-Agent 开发规范

注意：
1. 开发完进行测试，保证功能正常实现。
2. **按变更范围选择测试**（避免不必要的 token 消耗）：

   | 变更范围 | 运行命令 |
   |---------|---------|
   | 仅修改单元测试对应的类（引擎、基础设施工具类） | `./scripts/run-unit-tests.sh` |
   | 修改 Gateway / Domain / Trigger / 提示词 | `./run-integration-tests.sh`（单元+集成） |
   | 仅修改文档、YAML 配置、注释 | 无需运行测试 |

   **单元测试脚本**（按需单独运行，无需外部环境）：
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
   mvn test -f ../pom.xml -pl 06-SRE-Agent \
     -Dtest="ObservabilityAspectAnnotationTest,ToolExecutionTemplateTest,ToolResultTest,QueryScopeTest,EntityRegistryTest,OntologyQueryEngineTest,PersonalQuoteGatewayTest" \
     -Dsurefire.failIfNoSpecifiedTests=false
   ```

   - **特别注意**：修改 Java 代码逻辑时，必须同步检查 `sre-agent.md` 提示词是否与代码保持一致（见下方反思记录）。
   - **git commit 前**：pre-commit hook 会自动检测 src/ 变更并运行 `./run-integration-tests.sh`，无需手动触发。

---

## DirectOutput 机制

### 原理与性能

数据查询类工具（标记 `@DataQueryTool`）的结果绕过 LLM 二次处理，直接输出给用户。

| 场景 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 单合同查询 | 8秒 | 1-2秒 | **4-8倍** |
| 订单→多合同→关联数据 | 16-20秒 | 2-4秒 | **5-10倍** |

**耗时分析**：首字节时间 1-2.5秒（LLM 意图识别），工具耗时 100-500ms，DirectOutput 生效后总耗时 = 首字节时间。

### @DataQueryTool 注解使用规范

**核心原则**：`@DataQueryTool` 只能标记在**最外层的用户直调工具**上，内部工具禁止标记。

```
用户 → LLM 调用 → ontologyQuery (有 @DataQueryTool) → DirectOutput 输出
                    ↓ 内部调用
                    callPredefinedEndpoint (无 @DataQueryTool)
```

| 错误场景 | 后果 |
|---------|------|
| `ontologyQuery` 内部调用带有 `@DataQueryTool` 的工具 | DirectOutput 捕获中间结果，而非最终结果 |

```java
// ✅ 正确：最外层工具标记注解
@Tool(description = "本体论统一查询入口")
@DataQueryTool
public String ontologyQuery(String entity, String value, String queryScope) { }

// ✅ 正确：内部工具不标记注解
public String callPredefinedEndpoint(String endpointId, Map<String, String> params) { }
```

**判断标准**：该工具是否会被 LLM 直接调用？ ✅ 是 → 添加；❌ 否（仅被内部调用）→ 不添加

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
        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("endpoint-id", Map.of("paramName", value));
            return parseAndTransform(json);
        } catch (Exception e) {
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

通过 `/env` 命令查看和切换环境。可用环境：`nrs-escrow`（测试环境，默认）、`offline-beta`（基准环境）。

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

---

## UI 验证规范（Playwright）

涉及 UI 代码修改时，使用 Playwright 验证 UI 正确性。

```bash
# 截图
playwright screenshot http://localhost:8089/ontology.html screenshot.png --full-page
```

---

## HikariCP 连接池配置

**告警识别**：`Failed to validate connection ... (No operations allowed after connection closed.)` → 需调小 `max-lifetime`。

**参数约束**：`idle-timeout` < `max-lifetime` < MySQL `wait_timeout` - 30s

**当前配置**：`idle-timeout: 180000`，`max-lifetime: 300000`，`keepalive-time: 60000`

---

## OpenSpec 变更管理规范

所有文档相关操作必须遵循 OpenSpec 规范。变更完成后使用 `/opsx:archive` 归档。

**相关命令**：
```bash
openspec list --json
openspec status --change "<name>" --json
/opsx:archive <change-name>
```
