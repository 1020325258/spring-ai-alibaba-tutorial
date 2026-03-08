# Contract Query Tool Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将合同数据查询重构为单一 `queryContractData` 工具，通过 `dataType` 参数支持4种查询场景，修复意图识别问题。

**Architecture:** 新增 `QueryDataType` 枚举定义4种查询类型；`MySQLQueryTool` 新增 `queryContractData` 方法（单 `@Tool`）+ 6个私有 helper 方法（每张表对应一个）；所有关联表查询通过 `CompletableFuture` 并行执行；删除上轮遗留的 `queryContractByCode` 方法。

**Tech Stack:** Spring AI (`@Tool`)、Spring JDBC (`JdbcTemplate`)、Java 21 (`CompletableFuture`、`switch` 表达式)、JUnit 5 + AssertJ（集成测试）

---

### Task 1: 创建 QueryDataType 枚举

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/tools/QueryDataType.java`

**Step 1: 创建枚举文件**

```java
package com.yycome.sremate.tools;

/**
 * 合同数据查询类型枚举
 * 对应 queryContractData 工具的 dataType 参数取值
 */
public enum QueryDataType {
    /** 全量数据：contract + contract_node + contract_user + contract_quotation_relation + contract_field_sharding */
    ALL,
    /** 节点日志：contract + contract_node + contract_log */
    CONTRACT_NODE,
    /** 字段数据：contract + contract_field_sharding_N（分表） */
    CONTRACT_FIELD,
    /** 签约人：contract + contract_user */
    CONTRACT_USER
}
```

**Step 2: 确认编译通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn compile -pl 05-SREmate -q
```
Expected: BUILD SUCCESS，无错误

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/tools/QueryDataType.java
git commit -m "feat: add QueryDataType enum for contract query tool"
```

---

### Task 2: 在 MySQLQueryTool 中新增 6 个私有 helper 方法

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/tools/MySQLQueryTool.java`

在 `resolveFieldShardingTable` 方法之后、`toErrorJson` 方法之前插入以下6个私有方法。

**Step 1: 新增 fetchContractBase**

```java
/**
 * 查询合同主表基本信息
 */
private Map<String, Object> fetchContractBase(String contractCode) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT contract_code, type, status, platform_instance_id, amount, project_order_id, ctime " +
            "FROM contract WHERE contract_code = ? AND del_status = 0 LIMIT 1",
            contractCode);
    if (rows.isEmpty()) return null;
    Map<String, Object> row = rows.get(0);
    Map<String, Object> base = new LinkedHashMap<>();
    base.put("contractCode", row.get("contract_code"));
    base.put("type", row.get("type"));
    base.put("status", row.get("status"));
    base.put("amount", row.get("amount"));
    base.put("platformInstanceId", row.get("platform_instance_id"));
    base.put("projectOrderId", row.get("project_order_id"));
    base.put("ctime", String.valueOf(row.get("ctime")));
    return base;
}
```

**Step 2: 新增 fetchNodes**

```java
/**
 * 查询合同节点记录
 */
private List<Map<String, Object>> fetchNodes(String contractCode) {
    return jdbcTemplate.queryForList(
            "SELECT node_type, fire_time FROM contract_node " +
            "WHERE contract_code = ? AND del_status = 0 ORDER BY fire_time",
            contractCode);
}
```

**Step 3: 新增 fetchLogs**

```java
/**
 * 查询合同操作日志
 */
private List<Map<String, Object>> fetchLogs(String contractCode) {
    return jdbcTemplate.queryForList(
            "SELECT operator, operate_type, content, ctime FROM contract_log " +
            "WHERE contract_code = ? AND del_status = 0 ORDER BY ctime DESC LIMIT 50",
            contractCode);
}
```

**Step 4: 新增 fetchUsers**

```java
/**
 * 查询合同参与人（签约人）
 */
private List<Map<String, Object>> fetchUsers(String contractCode) {
    return jdbcTemplate.queryForList(
            "SELECT role_type, name, phone, is_sign, is_auth " +
            "FROM contract_user WHERE contract_code = ? AND del_status = 0",
            contractCode);
}
```

**Step 5: 新增 fetchFields**

```java
/**
 * 查询合同扩展字段（分表）
 */
private Map<String, Object> fetchFields(String contractCode) {
    String shardTable = resolveFieldShardingTable(contractCode);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT field_key, field_value FROM " + shardTable +
            " WHERE contract_code = ? AND del_status = 0 LIMIT 20",
            contractCode);
    Map<String, Object> fieldMap = new LinkedHashMap<>();
    rows.forEach(f -> fieldMap.put(
            String.valueOf(f.get("field_key")),
            tryParseJson(f.get("field_value"))));
    // 记录实际查询的分表名，便于排查
    fieldMap.put("_shardTable", shardTable);
    return fieldMap;
}
```

**Step 6: 新增 fetchQuotations**

```java
/**
 * 查询合同报价关联记录
 */
private List<Map<String, Object>> fetchQuotations(String contractCode) {
    return jdbcTemplate.queryForList(
            "SELECT * FROM contract_quotation_relation " +
            "WHERE contract_code = ? AND del_status = 0",
            contractCode);
}
```

**Step 7: 确认编译通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn compile -pl 05-SREmate -q
```
Expected: BUILD SUCCESS

**Step 8: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/tools/MySQLQueryTool.java
git commit -m "feat: add 6 private helper methods for contract sub-table queries"
```

---

### Task 3: 新增 queryContractData @Tool 方法，删除旧方法

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/tools/MySQLQueryTool.java`

**Step 1: 在 executeQuery 方法之后插入 queryContractData**

```java
/**
 * 根据合同编号查询合同数据（支持4种查询类型）
 *
 * @param contractCode 合同编号（contract_code），C前缀+数字，如 C1772925352128725
 * @param dataType     查询类型：ALL / CONTRACT_NODE / CONTRACT_FIELD / CONTRACT_USER
 * @return JSON格式聚合数据
 */
@Tool(description = "根据合同编号（contract_code）查询合同数据。" +
        "contractCode 参数为合同编号字符串，格式为C前缀+数字，如 C1772925352128725。" +
        "dataType 参数控制查询范围，根据用户意图填写：" +
        "- 用户说\"合同数据\"、\"合同详情\"、\"合同信息\" → 填 ALL（返回 contract + contract_node + contract_user + contract_quotation_relation + contract_field_sharding）；" +
        "- 用户说\"合同节点\"、\"节点数据\"、\"操作日志\"、\"合同日志\" → 填 CONTRACT_NODE（返回 contract + contract_node + contract_log）；" +
        "- 用户说\"合同字段\"、\"字段数据\" → 填 CONTRACT_FIELD（返回 contract + contract_field_sharding）；" +
        "- 用户说\"签约人\"、\"合同用户\"、\"参与人\" → 填 CONTRACT_USER（返回 contract + contract_user）。" +
        "注意：若用户提供的编号为纯数字（无C前缀），说明是订单号，应使用 queryContractsByOrderId 工具。")
public String queryContractData(String contractCode, String dataType) {
    log.info("queryContractData - contractCode: {}, dataType: {}", contractCode, dataType);
    try {
        QueryDataType type = QueryDataType.valueOf(dataType.toUpperCase());

        // base 查询与关联表查询并行发起（分表名由 contractCode 直接计算，无需等待 base 结果）
        CompletableFuture<Map<String, Object>> baseFuture = CompletableFuture.supplyAsync(
                () -> fetchContractBase(contractCode), dbQueryExecutor);

        Map<String, CompletableFuture<?>> futures = new LinkedHashMap<>();
        switch (type) {
            case ALL -> {
                futures.put("contract_node", CompletableFuture.supplyAsync(() -> fetchNodes(contractCode), dbQueryExecutor));
                futures.put("contract_user", CompletableFuture.supplyAsync(() -> fetchUsers(contractCode), dbQueryExecutor));
                futures.put("contract_field_sharding", CompletableFuture.supplyAsync(() -> fetchFields(contractCode), dbQueryExecutor));
                futures.put("contract_quotation_relation", CompletableFuture.supplyAsync(() -> fetchQuotations(contractCode), dbQueryExecutor));
            }
            case CONTRACT_NODE -> {
                futures.put("contract_node", CompletableFuture.supplyAsync(() -> fetchNodes(contractCode), dbQueryExecutor));
                futures.put("contract_log", CompletableFuture.supplyAsync(() -> fetchLogs(contractCode), dbQueryExecutor));
            }
            case CONTRACT_FIELD ->
                futures.put("contract_field_sharding", CompletableFuture.supplyAsync(() -> fetchFields(contractCode), dbQueryExecutor));
            case CONTRACT_USER ->
                futures.put("contract_user", CompletableFuture.supplyAsync(() -> fetchUsers(contractCode), dbQueryExecutor));
        }

        // 等待所有查询完成
        CompletableFuture.allOf(
                Stream.concat(Stream.of(baseFuture), futures.values().stream())
                        .toArray(CompletableFuture[]::new)
        ).join();

        Map<String, Object> base = baseFuture.join();
        if (base == null) {
            return toErrorJson("未找到合同编号为 " + contractCode + " 的合同记录");
        }

        // 按顺序组装结果
        Map<String, Object> result = new LinkedHashMap<>(base);
        futures.forEach((key, future) -> result.put(key, future.join()));

        return objectMapper.writeValueAsString(result);
    } catch (IllegalArgumentException e) {
        return toErrorJson("无效的 dataType 参数: " + dataType + "，可选值: ALL / CONTRACT_NODE / CONTRACT_FIELD / CONTRACT_USER");
    } catch (Exception e) {
        log.error("queryContractData 失败", e);
        return toErrorJson(e.getMessage());
    }
}
```

需要在文件顶部 import 中补充 `java.util.stream.Stream`。

**Step 2: 删除上轮遗留的 queryContractByCode 方法**

删除整个 `queryContractByCode` 方法（从 javadoc 注释到方法结束的 `}`）。

**Step 3: 确认编译通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn compile -pl 05-SREmate -q
```
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/tools/MySQLQueryTool.java
git commit -m "feat: add queryContractData tool, remove legacy queryContractByCode"
```

---

### Task 4: 更新 sre-agent.md 提示词

**Files:**
- Modify: `05-SREmate/src/main/resources/prompts/sre-agent.md`

**Step 1: 替换"可用工具"章节中关于合同查询的部分**

将原有 `2a. queryContractsByOrderId`（含上轮修改的禁止误用规则）之后、`2b. queryContractFormId` 之前的内容，替换为以下内容：

```markdown
### 2a. queryContractsByOrderId
根据项目订单号查询该订单下所有合同，并聚合关联数据。
- 参数：
  - projectOrderId: 项目订单号，格式为**纯数字**，如 826030619000001899
- 使用场景：用户询问"某订单有哪些合同"、"查询订单合同列表"、"订单下合同详情"时使用
- **重要**：若用户提供的编号以字母 `C` 开头（如 C1772925352128725），说明是合同编号而非订单号，**不得**调用本工具，应使用 `queryContractData`。
- 返回：每份合同的基本信息 + 节点记录 + 参与人 + 扩展字段

### 2aa. queryContractData（合同编号查询，推荐）
根据合同编号（C前缀）查询合同数据，通过 dataType 参数控制返回范围。
- 参数：
  - contractCode: 合同编号，格式为 **C前缀+数字**，如 C1772925352128725
  - dataType: 查询范围（见下表）
- dataType 取值：

| 用户意图 | dataType |
|---|---|
| "合同数据"、"合同详情"、"合同信息" | `ALL` |
| "合同节点"、"节点数据"、"操作日志"、"合同日志" | `CONTRACT_NODE` |
| "合同字段"、"字段数据" | `CONTRACT_FIELD` |
| "签约人"、"合同用户"、"参与人" | `CONTRACT_USER` |
```

**Step 2: 替换示例对话部分**

将原有"示例1b"替换为4条覆盖所有 dataType 的示例：

```markdown
**示例1b（合同编号 - 全量数据）：**

**用户：** C1772925352128725合同数据

**助手：**
[识别到 C 开头的合同编号，意图为"合同数据"，调用 queryContractData(contractCode=C1772925352128725, dataType=ALL)，直接裸输出工具返回的 JSON]

---

**示例1c（合同编号 - 节点日志）：**

**用户：** C1772925352128725合同节点数据

**助手：**
[意图为"节点数据"，调用 queryContractData(contractCode=C1772925352128725, dataType=CONTRACT_NODE)，直接裸输出 JSON]

---

**示例1d（合同编号 - 字段数据）：**

**用户：** C1772925352128725合同字段数据

**助手：**
[意图为"字段数据"，调用 queryContractData(contractCode=C1772925352128725, dataType=CONTRACT_FIELD)，直接裸输出 JSON]

---

**示例1e（合同编号 - 签约人）：**

**用户：** C1772925352128725合同签约人数据

**助手：**
[意图为"签约人"，调用 queryContractData(contractCode=C1772925352128725, dataType=CONTRACT_USER)，直接裸输出 JSON]
```

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/resources/prompts/sre-agent.md
git commit -m "docs: update sre-agent prompt for queryContractData tool"
```

---

### Task 5: 编写集成测试 ContractDataQueryIT

**Files:**
- Create: `05-SREmate/src/test/java/com/yycome/sremate/ContractDataQueryIT.java`

使用真实合同编号（与现有测试一致，使用 `C1772854666284956`，如有实际可用编号请替换）。

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同数据查询端到端集成测试（4种 dataType 场景）
 *
 * 前置条件：application-local.yml 配置数据库连接，数据库网络可达
 *
 * 运行全部：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT
 *
 * 运行单个：
 *   JAVA_HOME=... mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT#queryAllData_returnsFullData
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractDataQueryIT {

    // 替换为数据库中真实存在的合同编号
    private static final String CONTRACT_CODE = "C1772854666284956";

    @Autowired
    private ChatClient sreAgent;

    @Test
    void queryAllData_returnsFullData() {
        String response = ask(CONTRACT_CODE + "合同数据");

        System.out.println("=== [ALL] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contractCode");
        assertThat(response).contains("contract_node");
        assertThat(response).contains("contract_user");
        assertThat(response).contains("contract_field_sharding");
        assertThat(response).contains("contract_quotation_relation");
        // ALL 不应返回 contract_log（那是 CONTRACT_NODE 的字段）
        assertThat(response).doesNotContain("contract_log");
    }

    @Test
    void queryNodeData_returnsNodeAndLog() {
        String response = ask(CONTRACT_CODE + "合同节点数据");

        System.out.println("=== [CONTRACT_NODE] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contract_node");
        assertThat(response).contains("contract_log");
        // CONTRACT_NODE 不应返回 contract_user / contract_field_sharding
        assertThat(response).doesNotContain("contract_user");
        assertThat(response).doesNotContain("contract_field_sharding");
    }

    @Test
    void queryFieldData_returnsFieldSharding() {
        String response = ask(CONTRACT_CODE + "合同字段数据");

        System.out.println("=== [CONTRACT_FIELD] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contract_field_sharding");
        // CONTRACT_FIELD 不应返回 contract_node / contract_user
        assertThat(response).doesNotContain("contract_node");
        assertThat(response).doesNotContain("contract_user");
    }

    @Test
    void queryUserData_returnsContractUser() {
        String response = ask(CONTRACT_CODE + "合同签约人数据");

        System.out.println("=== [CONTRACT_USER] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contract_user");
        // CONTRACT_USER 不应返回 contract_node / contract_field_sharding
        assertThat(response).doesNotContain("contract_node");
        assertThat(response).doesNotContain("contract_field_sharding");
    }

    private String ask(String question) {
        return sreAgent.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

**Step 2: Commit（测试先行，尚未运行）**

```bash
git add 05-SREmate/src/test/java/com/yycome/sremate/ContractDataQueryIT.java
git commit -m "test: add ContractDataQueryIT for 4 contract query scenarios"
```

---

### Task 6: 运行集成测试验证

**Step 1: 运行全部4个测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT 2>&1 | tail -40
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

**Step 2: 若某个测试失败，定位原因**

- 若报 `未找到合同编号` → 替换 `CONTRACT_CODE` 为数据库中真实存在的合同编号
- 若报字段断言失败（如 `contract_log` 不存在）→ 检查 `contract_log` 表是否有该合同的数据；可降级为只断言 `"contract_log"` key 存在（即使为空数组）
- 若 LLM 路由错误（调用了 `queryContractsByOrderId`）→ 检查 `sre-agent.md` 示例是否保存正确

**Step 3: 全部通过后最终 Commit**

```bash
git add -A
git commit -m "feat: complete contract query tool with 4 dataType scenarios and integration tests"
```
