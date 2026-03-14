# SREmate 集成测试执行报告

> 最后更新: 2026-03-14 19:31:04

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询技术问题、故障排查、运维操作等。有什么可以帮助你的吗？
- **耗时:** 1258ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 0ms
- **工具调用:** 无

---

## PersonalQuoteToolIT

### ✅ personalQuoteKeyword_withSubOrder_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"code":2000,"message":"操作成功","data":{"planAllDTO":null,"homeOrderNo":null,"previousHomeOrderNo":null,"nextHomeOrderNo":null,"personalContractDataList":[{"billCode":null,"changeOrderId":null,"subOrderNoList":["S15260312120004471"],"personalContractPrice":8.00,"comboCode":"2412","comboName":"新团装定软电默认...（截断）
- **耗时:** 12311ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 942ms
  - `callPredefinedEndpoint` ✓ 940ms

### ✅ personalQuoteKeyword_shouldNotCallBudgetBillTool

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"code":2000,"message":"操作成功","data":{"planAllDTO":null,"homeOrderNo":null,"previousHomeOrderNo":null,"nextHomeOrderNo":null,"personalContractDataList":[{"billCode":null,"changeOrderId":null,"subOrderNoList":["S15260312120004471"],"personalContractPrice":8.00,"comboCode":"2412","comboName":"新团装定软电默认...（截断）
- **耗时:** 11482ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 797ms
  - `callPredefinedEndpoint` ✓ 796ms

### ✅ personalQuoteKeyword_withBillCode_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** 根据接口返回的结果，当前查询的个性化报价数据为空，提示信息为：“查询团装报价单附件为空，请重试或联系客服”。这可能意味着以下几种情况：  1. **数据未正确生成**：该订单下的报价单可能尚未生成或存在异常。 2. **参数错误**：提供的订单号或报价单号可能有误。 3. **系统问题**：可能是系统临时故障或接口调用异常。  ### 建议的操作步骤： 1. **确认订单号和报价单号**：请再次检查输入的订单号 `826031210000003581` 和报价单号 `GBILL260312104241050001` 是否正确。 2. **重新尝试查询**：在确认信息无误后，重新执行查询操作。 ...（截断）
- **耗时:** 5406ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 873ms
  - `callPredefinedEndpoint` ✓ 872ms

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** 运维知识库包含以下分类： - diagnosis（问题诊断） - operations（运维操作） - knowledge（知识文档）
- **耗时:** 976ms
- **工具调用:**
  - `listSkillCategories` ✓ 1ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** 请提供服务的主机地址和端口号，以便我为您检查服务健康状态。例如："192.168.1.100:8080"。
- **耗时:** 3217ms
- **工具调用:**
  - `callPredefinedEndpoint` ✓ 4ms
  - `querySkills` ✓ 13ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 1. **检查数据库连接数**：使用MySQL查询工具查看当前连接数，关注Threads_connected、Max_used_connections和max_connections指标。 2. **检查连接池配置**：查看应用连接池配置是否合理，包括maximum-pool-size、minimum-idle、connection-timeout和idle-timeout等参数。 3. **检查网络连通性**：测试应用与数据库之间的网络，包括ping测试、telnet测试数据库端口和检查防火墙规则。 4. **检查数据库负载**：查看数据库负载情况，关注慢查询、锁等待和buffer pool...（截断）
- **耗时:** 3691ms
- **工具调用:**
  - `querySkills` ✓ 2ms

---

## SubOrderToolIT

### ✅ subOrderWithQuotation_shouldCallQuerySubOrderInfo

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1880ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 94ms
  - `callPredefinedEndpoint` ✓ 92ms

### ✅ subOrderKeyword_shouldCallQuerySubOrderInfo

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14251231110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S142512311...（截断）
- **耗时:** 21085ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 138ms
  - `callPredefinedEndpoint` ✓ 136ms

---

