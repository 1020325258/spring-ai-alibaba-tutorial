# SREmate 开发规范

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

## 新增 HTTP 接口

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

1. 在"可用工具"章节补充工具说明（含触发场景和参数）
2. 复合工具标注"推荐/优先"，原子工具说明何时退化使用
3. `callPredefinedEndpoint` 的常用接口列表同步补充新 endpointId

---

## 集成测试

- 测试文件命名：`*IT.java`，放在 `src/test/java/com/yycome/sremate/`
- 只写端到端集成测试，通过真实问题验证 Agent 完整链路响应
- 必须加 `sre.console.enabled=false` 禁用交互命令行，否则测试启动会阻塞

```java
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")   // 加载 application-local.yml
class YourFeatureIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void askSomething_returnsExpectedData() {
        String response = sreAgent.prompt()
                .user("你的自然语言问题")
                .call()
                .content();

        System.out.println("=== Agent 回复 ===\n" + response);
        assertThat(response).containsIgnoringCase("期望关键词");
        assertThat(response).doesNotContain("查询失败");
    }
}
```

运行指定集成测试：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=YourFeatureIT
```
