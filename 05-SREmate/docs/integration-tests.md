# SREmate 集成测试文档

## 测试原则

- 所有测试通过真实 `ChatClient` 发起自然语言请求，验证完整 Agent 链路
- 不使用 Mock，连接真实数据库和 HTTP 接口
- 每次代码变更后必须运行全部集成测试

## 快速运行

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

或直接运行 Maven 命令：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate \
  -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT"
```

## 测试文件说明

| 文件 | 覆盖工具 | 核心验证点 |
|---|---|---|
| `StartupIT` | 无 | 应用上下文加载、Bean 注入正常 |
| `ContractQueryToolIT` | `queryContractData`、`queryContractsByOrderId` | 合同号/订单号格式识别、数据字段返回 |
| `ContractInstanceToolIT` | `queryContractInstanceId`、`queryContractFormId` | instanceId 查询、DB+HTTP 串联链路 |
| `ContractConfigToolIT` | `queryContractConfig` | 配置表查询、类型识别、无类型时询问 |
| `SubOrderToolIT` | `querySubOrderInfo` | HTTP 接口连通性、GBILL 前缀识别 |
| `SkillQueryToolIT` | `querySkills`、`listSkillCategories` | Runbook 检索、知识库分类列表 |
| `HttpEndpointToolIT` | `listAvailableEndpoints` | YAML 模板加载、接口列表返回 |

## 测试数据维护

各 IT 文件顶部有 `private static final String` 声明的测试数据常量，**需确保与本地数据库中实际存在的数据一致**：

| 常量 | 说明 | 涉及文件 |
|---|---|---|
| `CONTRACT_CODE` | 合同编号（C 前缀+数字） | `ContractQueryToolIT`、`ContractInstanceToolIT`、`ContractConfigToolIT` |
| `PROJECT_ORDER_ID` | 项目订单号（纯数字） | `ContractQueryToolIT`、`ContractConfigToolIT` |
| `HOME_ORDER_NO` | 子单查询订单号 | `SubOrderToolIT` |
| `QUOTATION_ORDER_NO` | 报价单号（GBILL 前缀） | `SubOrderToolIT` |

> 数据库中没有对应数据时，测试会因"未找到"断言失败。请更新常量后重新运行。

## 验收标准

| 检查项 | 验证方式 |
|---|---|
| 应用能启动 | `StartupIT.applicationContext_shouldLoad` 通过 |
| 合同号不被当作订单号 | `ContractQueryToolIT.queryContractData_withCPrefix_shouldNotUseOrderTool` 通过 |
| 订单号不被当作合同号 | `ContractQueryToolIT.queryContractsByOrderId_pureDigits_shouldNotUseContractTool` 通过 |
| HTTP 接口可连通 | `SubOrderToolIT` 两个用例无 `ConnectException` |
| 知识库文档可加载 | `SkillQueryToolIT.listSkillCategories_shouldReturnCategories` 通过 |
| YAML 模板可加载 | `HttpEndpointToolIT.listAvailableEndpoints_shouldReturnEndpointList` 通过 |

## 常见失败原因

| 失败现象 | 可能原因 | 解决方式 |
|---|---|---|
| `StartupIT` 失败，Bean 注入报错 | 新增/修改配置类有问题 | 检查 `@Configuration`、`@Bean` 注解及 `application-local.yml` |
| 合同查询返回"未找到" | 测试数据常量与本地 DB 不匹配 | 更新 `CONTRACT_CODE` / `PROJECT_ORDER_ID` 常量 |
| 子单查询返回"接口调用失败" | HTTP 接口地址不可达 | 检查网络/VPN，确认 endpoint YAML 中的 URL 正确 |
| Skill 返回"未找到任何匹配" | `skills/` 目录 Markdown 为空或关键词不匹配 | 检查 `src/main/resources/skills/` 下的文件内容 |
| 版式查询（`ContractInstanceToolIT`）失败 | DB 中合同无 instanceId，或版式 HTTP 接口不通 | 换一个有版式数据的合同号，或检查版式接口地址 |
