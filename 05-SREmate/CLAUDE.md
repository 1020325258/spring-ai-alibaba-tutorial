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
  tools/              # @Tool 工具类（HttpEndpointTool、MySQLQueryTool、ContractTool）
  config/             # Spring 配置（AgentConfiguration、DataSourceConfiguration）
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

在 `ContractTool` 中封装专用方法：

```java
@Tool(description = """
        【xxx查询】用户提到"xxx"时使用。
        触发条件：包含关键词"xxx"
        参数：projectOrderId（纯数字订单号，必填）
        示例："826031111000001859的xxx" → projectOrderId=826031111000001859""")
public String queryXxxList(String projectOrderId) {
    long start = System.currentTimeMillis();
    try {
        String result = httpEndpointTool.callPredefinedEndpoint("your-endpoint-id",
                Map.of("projectOrderId", projectOrderId));
        log.info("[TOOL] queryXxxList → {}ms, ok", System.currentTimeMillis() - start);
        return result;
    } catch (Exception e) {
        log.error("[TOOL] queryXxxList → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
        return toErrorJson(e.getMessage());
    }
}
```

### 必须同步 DATA_QUERY_TOOLS

新增工具后，在 `ObservabilityAspect.DATA_QUERY_TOOLS` 中添加方法名：

```java
private static final Set<String> DATA_QUERY_TOOLS = Set.of(
        "queryContractData",
        "queryBudgetBillList"   // ← 新增工具要加进来
);
```

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
