# SREmate 开发规范

注意：
1. 开发完进行测试，保证功能正常实现。
2. **每次代码变更后必须运行全部集成测试**，确保已有功能不被破坏：
   ```bash
   ./05-SREmate/scripts/run-integration-tests.sh
   ```

## Bug 修复记录

### [已修复] 多次查询同一订单时 Agent 不调用工具，直接复读历史数据

**现象：** 用户连续多次询问同一订单（如 `826031111000001859报价单`），第二次起 Agent 不再调用工具，直接流式输出上一次的查询结果，数据不实时。

**根因：** `SREConsole` 维护了 `conversationHistory` 并在每轮请求时通过 `.messages(conversationHistory)` 传给 LLM。当历史中已有相同查询的 JSON 结果时，LLM 识别到重复意图，倾向于直接复读历史，绕过工具调用。尽管系统提示词中写明"必须实时查询"，LLM 并不能保证每次遵守。

**修复方案（`SREConsole.java` 第 177 行）：**
数据查询走 `DirectOutputHolder` 直接输出路径时（`directOutputUsed = true`），不将实际数据写入对话历史，改为写入占位文本：

```java
// 数据查询结果不写入对话历史，防止 LLM 下次直接复读历史数据而跳过实时工具调用
String historyContent = directOutputUsed.get()
        ? "[已调用工具查询并直接输出数据，结果不保留在上下文中]"
        : response;
conversationHistory.add(new AssistantMessage(historyContent));
```

**原则：数据查询结果只输出给用户，不进对话历史。对话历史只存 LLM 的文字回复，不存工具返回的原始数据。**

---

## 项目结构

```
src/main/resources/
  endpoints/          # HTTP 接口模板（YAML）
  prompts/            # LLM 系统提示词
  skills/             # SRE 知识库（Markdown）
src/main/java/.../
  tools/              # @Tool 工具类（HttpQueryTool、MySQLQueryTool）
  config/             # Spring 配置（AgentConfiguration、DataSourceConfiguration）
```

---

## POST 接口接入规范

### 场景：接口需要带 Request Body 的 POST 请求

仅在 YAML 中追加配置即可，**无需修改 Java 代码**。需要用到 `requestBodyTemplate` 和 `responseFields` 两个扩展字段：

```yaml
- id: your-endpoint-id
  name: 接口名称
  method: POST
  urlTemplate: "http://host/path"
  requestBodyTemplate: '{"paramName":"${paramName}"}'   # POST body 模板，支持 ${} 占位符
  responseFields:                                        # 响应字段过滤（可选）
    arrayField1:                                         # data 下的数组字段名
      - fieldA
      - fieldB
    arrayField2:
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
- `requestBodyTemplate`：`${paramName}` 会被 `params` 中对应值替换，构建为 JSON body 发送
- `responseFields`：配置后，直接返回过滤后的**纯 JSON**（不带 wrapper 文字），只保留 `data` 下指定数组字段的指定列；不配置则返回完整响应

---

## 新增 HTTP 接口（GET）

在 `src/main/resources/endpoints/` 对应分类的 YAML 文件中追加，无需修改 Java 代码。

```yaml
- id: your-endpoint-id          # 唯一标识，供 callPredefinedEndpoint 调用
  name: 接口名称
  description: |
    用自然语言描述该接口的用途和触发场景，LLM 依赖此字段决策是否调用。
  category: contract             # 分类：contract / system / monitoring
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
    Content-Type: "application/json"
  timeout: 15
  examples:
    - "用户可能问的问题示例"
```

---

## 敏感配置管理

- `application.yml` 只存占位符默认值（可提交 git）
- 真实的数据库地址、账号密码写在 `src/main/resources/application-local.yml`（已被 `.gitignore` 忽略）
- 本地开发默认激活 `local` profile（`spring.profiles.active: local` 已在 `application.yml` 中配置）

`application-local.yml` 示例：
```yaml
spring:
  datasource:
    sre:
      jdbc-url: jdbc:mysql://host:port/db?useUnicode=true&characterEncoding=utf-8
      username: root
      password: your_password
```

---

## 新增数据库查询工具

工具方法写在 `MySQLQueryTool`，使用 `@Tool` 注解。

**原子查询**（只查库，返回单个字段）：
```java
@Tool(description = "...")
public String yourQuery(String param) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT col FROM table WHERE key = ? LIMIT 1", param);
    ...
}
```

**复合工具**（需要串联多步操作时，在 Java 层完成，不依赖 LLM 多步推理）：

```java
// 私有方法：返回原始值，供内部复用
private Long findSomeId(String key) { ... }

// 原子 Tool：只查 ID
@Tool(description = "...若只需要 ID 时使用，如需完整数据请用 queryXxxFull。")
public String queryId(String key) { ... }

// 复合 Tool：查 ID + 调接口，一步到位
@Tool(description = "...自动完成：1) 查库得到 ID；2) 调用 xxx 接口返回结果。")
public String queryXxxFull(String key) {
    Long id = findSomeId(key);
    if (id == null) return "未找到...";
    return httpQueryTool.callPredefinedEndpoint("endpoint-id",
        Map.of("paramName", id.toString()));
}
```

> 原则：当用户意图需要跨数据库和 HTTP 接口时，**必须**用复合工具，禁止依赖 LLM 自动串联多步工具调用。

---

## 新增 HTTP 专用工具（⚠️ 强制规范）

### 禁止直接让 LLM 调用 `callPredefinedEndpoint` 处理业务查询

`callPredefinedEndpoint` 是泛型工具，需要 LLM 同时推断 `endpointId`（字符串）和 `params`（Map）两个参数。**实测 LLM 高概率推断失败，传入 null**，导致报错：

```
params={arg1=null, arg0=null}
endpointId: null, params: null
错误：未找到接口模板: null
```

### 正确做法：在 `ContractTool` 中新增专用方法

每个业务查询接口必须在 `ContractTool`（或其他领域 Tool 类）中封装一个专用 `@Tool` 方法，内部调用 `callPredefinedEndpoint`：

```java
@Tool(description = """
        【xxx查询】用户提到"xxx"时使用。

        触发条件：包含关键词"xxx"

        参数：
        - projectOrderId：纯数字订单号（必填）

        示例：
        - "826031111000001859的xxx" → projectOrderId=826031111000001859""")
public String queryXxxList(String projectOrderId) {
    log.info("queryXxxList - projectOrderId: {}", projectOrderId);
    try {
        return httpEndpointTool.callPredefinedEndpoint("your-endpoint-id",
                Map.of("projectOrderId", projectOrderId));
    } catch (Exception e) {
        log.error("queryXxxList 失败", e);
        return toErrorJson(e.getMessage());
    }
}
```

**原则：`callPredefinedEndpoint` 只能在 Java 层内部调用，不暴露给 LLM 直接使用业务接口。**

### 新增专用工具后必须同步 DATA_QUERY_TOOLS

`ObservabilityAspect.java` 中维护了一个 `DATA_QUERY_TOOLS` 集合，只有在此集合中的工具，其返回结果才会写入 `DirectOutputHolder`，从而绕过 LLM 归纳、直接输出原始数据（提升性能、保证准确性）。

```java
// ObservabilityAspect.java
private static final Set<String> DATA_QUERY_TOOLS = Set.of(
        "queryContractData",
        "queryContractsByOrderId",
        // ...
        "queryBudgetBillList"   // ← 每新增一个专用工具都要加进来
);
```

> **每次在 `ContractTool` 中新增 `@Tool` 方法后，必须同步将方法名加入 `DATA_QUERY_TOOLS`，否则该工具结果仍会经过 LLM 二次归纳，既慢又可能被改写。**

---

## 分库分表处理规范

`contract_field_sharding` 按合同号取模分为 10 张表（`_0` 至 `_9`）。

**分片规则**：去除合同号中的非数字字符，取数字部分对 10 取模。

```java
// 统一使用此私有方法计算表名，禁止在方法内硬编码表后缀
private String resolveFieldShardingTable(String contractCode) {
    String digits = contractCode.replaceAll("[^0-9]", "");
    int shard = (int) (Long.parseLong(digits) % 10);
    return "contract_field_sharding_" + shard;
}
```

示例：`C1772854666284956` → 数字部分 `1772854666284956` → `% 10 = 6` → `contract_field_sharding_6`

**查询时注意**：
- 表名由代码动态拼接，SQL 中不可用 `?` 绑定表名，需先调用 `resolveFieldShardingTable` 解析
- 扩展字段可能较多，查询时加 `LIMIT` 避免返回内容过大（当前限制 20 条）

---

## 更新 LLM 系统提示词

新增工具或接口后，同步更新 `src/main/resources/prompts/sre-agent.md`：

1. 在"可用工具"章节补充**专用工具**说明（含触发场景和参数），不要写 `callPredefinedEndpoint`
2. 在决策表中明确：关键词 → 专用工具名，防止 LLM 选错工具
3. 同类工具之间写清楚区分规则（如"报价单 ≠ 子单，不要混用"）
4. 不需要在提示词中暴露 `endpointId`，那是 Java 层内部细节

---

## 集成测试

- 测试文件命名：`*IT.java`，放在 `src/test/java/com/yycome/sremate/`
- 只写端到端集成测试，通过真实问题验证 Agent 完整链路响应
- 必须加 `sre.console.enabled=false` 禁用交互命令行，否则测试启动会阻塞

### 端到端测试规范（强制）

**必须验证 Agent 正确识别意图并输出期望数据**，不能仅验证"工具被调用"。

```java
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class YourFeatureIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void queryByContractCode_shouldReturnContractData() {
        String response = sreAgent.prompt()
                .user("C1772854666284956的合同配置表数据")
                .call()
                .content();

        System.out.println("=== Agent 回复 ===\n" + response);

        // ✅ 正确：验证返回了期望的业务数据
        assertThat(response).contains("contract_city_company_info");
        assertThat(response).contains("projectOrderId");
        assertThat(response).doesNotContain("未找到");
        assertThat(response).doesNotContain("error");
    }

    @Test
    void queryByOrderId_shouldUseProjectOrderId() {
        String response = sreAgent.prompt()
                .user("826030619000001899的合同配置")
                .call()
                .content();

        System.out.println("=== Agent 回复 ===\n" + response);

        // ✅ 验证 Agent 正确识别订单号，并使用正确的参数
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("contract_city_company_info"),
            r -> assertThat(r).contains("needAskType"),  // 需要询问合同类型
            r -> assertThat(r).contains("availableTypes")
        );
        // ❌ 不应该出现：说明 Agent 没有正确识别订单号
        assertThat(response).doesNotContain("未找到编号");
    }
}
```

### 测试检查清单

新增或修改 @Tool 工具后，端到端测试必须覆盖：

1. **入参识别**：验证 Agent 能正确区分不同格式的输入（合同号 C 前缀 vs 订单号纯数字）
2. **数据输出**：验证返回结果包含期望的业务字段
3. **错误处理**：验证数据不存在时有合理的提示，而非代码异常

运行全部集成测试（每次变更后必须执行）：
```bash
./05-SREmate/scripts/run-integration-tests.sh
```

运行指定集成测试：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=YourFeatureIT
```

测试数据常量维护：各 IT 文件顶部的 `CONTRACT_CODE`、`PROJECT_ORDER_ID` 等常量需与本地 DB 实际数据一致，数据变化时及时更新。详见 `docs/integration-tests.md`。
