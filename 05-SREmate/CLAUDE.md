# SREmate 开发规范

注意：
1. 开发完进行测试，保证功能正常实现。
2. **每次代码变更后必须运行全部集成测试**，确保已有功能不被破坏：
   ```bash
   ./05-SREmate/scripts/run-integration-tests.sh
   ```

---

## 项目结构

```
src/main/resources/
  endpoints/          # HTTP 接口模板（YAML）
  prompts/            # LLM 系统提示词
  skills/             # SRE 知识库（Markdown）
src/main/java/.../
  trigger/agent/      # @Tool 工具类（按业务领域拆分）
    ├── ContractQueryTool.java   # 合同查询
    ├── BudgetBillTool.java      # 报价单查询
    ├── SubOrderTool.java        # 子单查询
    └── HttpEndpointTool.java    # HTTP 接口调用
  infrastructure/
    ├── annotation/    # 注解（@DataQueryTool）
    └── service/       # 基础设施服务（ToolExecutionTemplate、ToolResult）
  domain/contract/     # 合同领域（DDD）
    ├── gateway/       # 网关接口（ContractFormGateway）
    └── service/       # 领域服务（ContractQueryService）
  config/              # Spring 配置（AgentConfiguration、DataSourceConfiguration）
  aspect/              # AOP 切面（ObservabilityAspect）
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

## 新增专用工具（⚠️ 强制规范）

### 禁止直接让 LLM 调用 `callPredefinedEndpoint`

`callPredefinedEndpoint` 需要推断 `endpointId` 和 `params`，LLM 高概率传入 null 导致报错。

### 正确做法：封装专用 `@Tool` 方法

根据业务领域，在对应的工具类中封装专用方法：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class XxxTool {

    private final HttpEndpointTool httpEndpointTool;

    @Tool(description = """
            【xxx查询】用户提到"xxx"时使用。
            触发条件：包含关键词"xxx"
            参数：projectOrderId（纯数字订单号，必填）
            示例："826031111000001859的xxx" → projectOrderId=826031111000001859""")
    @DataQueryTool  // 标记为数据查询工具，结果直接输出
    public String queryXxxList(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryXxxList", () ->
            httpEndpointTool.callPredefinedEndpoint("your-endpoint-id",
                Map.of("projectOrderId", projectOrderId))
        );
    }
}
```

### 必须添加 @DataQueryTool 注解

新增数据查询工具方法时，**必须**添加 `@DataQueryTool` 注解：

```java
@DataQueryTool  // 标记后，工具结果直接输出，绕过 LLM 归纳
public String queryXxxList(...) { ... }
```

**注意：** 旧版 `DATA_QUERY_TOOLS` 白名单已废弃，改为注解方式。

---

## 新增数据库查询工具

工具方法写在 `MySQLQueryTool`，使用 `@Tool` 注解。

**复合工具**（跨数据库和 HTTP 接口）必须在 Java 层完成，禁止依赖 LLM 串联多步调用：

```java
@Tool(description = "...自动完成：1) 查库得到 ID；2) 调用接口返回结果。")
public String queryXxxFull(String key) {
    Long id = findSomeId(key);
    if (id == null) return "未找到...";
    return httpEndpointTool.callPredefinedEndpoint("endpoint-id", Map.of("param", id.toString()));
}
```

---

## 工具日志规范

| 层级 | 日志内容 | 示例 |
|------|---------|------|
| AOP 层 | 入口：工具名 + 参数 | `[TOOL] queryContractData(contractCode=C..., dataType=ALL)` |
| 工具层 | 结果：耗时 + 摘要 | `[TOOL] queryContractData → 50ms, 5 rows` |

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
void contractCodePrefix_shouldCallQueryContractData() {
    ask("C1767173898135504的合同数据");
    assertToolCalled("queryContractData");
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
./05-SREmate/scripts/run-integration-tests.sh
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

### ContractFormGateway（合同版式网关）

领域层网关接口，根据 `platform_instance_id` 查询版式表单数据。实现类委托 `HttpEndpointTool` 调用预定义接口。

---

## 工具类职责划分

| 工具类 | 职责 | 方法 |
|--------|------|------|
| `ContractQueryTool` | 合同数据查询 | queryContractData, queryContractsByOrderId, queryContractInstanceId, queryContractFormId, queryContractConfig |
| `BudgetBillTool` | 报价单查询 | queryBudgetBillList |
| `SubOrderTool` | 子单查询 | querySubOrderInfo |
| `PersonalQuoteTool` | 个性化报价查询 | queryContractPersonalData |
| `HttpEndpointTool` | HTTP 接口调用 | callPredefinedEndpoint, callPredefinedEndpointRaw |

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

**修复记录：** `docs/bug-fix-records/2026-03-12-hikaricp-connection-validation-warning.md`
