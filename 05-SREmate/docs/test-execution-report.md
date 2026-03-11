# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 22:16:16

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询技术问题、故障排查、运维咨询等。有什么可以帮助你的吗？
- **耗时:** 1346ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 1ms
- **工具调用:** 无

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** SRE运维知识库包含以下分类：  - **diagnosis**：问题诊断类，用于排查和解决技术问题（如超时、报错、异常等） - **operations**：运维操作类，用于指导日常运维工作（如重启服务、配置调整等） - **knowledge**：知识类，提供运维相关的背景知识和最佳实践
- **耗时:** 2045ms
- **工具调用:**
  - `listSkillCategories` ✓ 1ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** ### 服务超时排查与解决方案  #### 短期解决 1. **检查服务健康状态**：    - 调用健康检查接口：`GET /actuator/health`    - 如果返回 `DOWN` 状态，说明服务异常，需要立即处理。  2. **检查服务指标**：    - 查看关键性能指标（如响应时间、错误率、QPS）。    - 如果响应时间过高或错误率上升，可能是服务负载过高或存在性能瓶颈。  3. **检查日志**：    - 查看服务日志中的异常和慢请求记录，定位具体问题。  4. **资源使用情况**：    - 检查 CPU、内存、GC 和线程池状态，确认是否存在资源不足或线程阻塞...（截断）
- **耗时:** 6168ms
- **工具调用:**
  - `querySkills` ✓ 17ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 我来帮你排查数据库连接超时的问题。首先查询相关的排查经验。  [调用querySkills工具，查询类型：diagnosis，关键词：数据库 连接 超时]  根据排查经验，我需要检查数据库连接状态和性能指标。建议：  ### 短期解决 1. 重启应用释放空闲连接 2. 临时调大连接池配置  ### 长期优化 1. 优化连接池配置（maximum-pool-size、minimum-idle） 2. 排查连接泄漏 3. 优化慢查询
- **耗时:** 2653ms
- **工具调用:**
  - `querySkills` ✓ 9ms

---

## ContractConfigToolIT

### ✅ configKeyword_shouldCallQueryContractConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 5368ms
- **工具调用:**
  - `queryContractConfig` ✓ 333ms

### ✅ orderIdWithoutType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 5873ms
- **工具调用:**
  - `queryContractConfig` ✓ 53ms

### ✅ orderIdWithConfigType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 5561ms
- **工具调用:**
  - `queryContractConfig` ✓ 59ms

---

## ContractInstanceToolIT

### ✅ instanceIdKeyword_shouldCallQueryContractInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1270ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 24ms

### ✅ formIdKeyword_shouldCallQueryContractFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据查询结果，获取合同版式 form_id 时出现了服务端异常。建议您稍后重试或联系客服处理此问题。如果需要进一步帮助，请提供更多信息。
- **耗时:** 1821ms
- **工具调用:**
  - `queryContractFormId` ✓ 156ms
  - `callPredefinedEndpoint` ✓ 136ms

---

## SubOrderToolIT

### ✅ subOrderWithQuotation_shouldCallQuerySubOrderInfo

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1773ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 262ms
  - `callPredefinedEndpoint` ✓ 261ms

### ✅ subOrderKeyword_shouldCallQuerySubOrderInfo

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14251231110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S142512311...（截断）
- **耗时:** 21633ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 133ms
  - `callPredefinedEndpoint` ✓ 133ms

---

## ContractQueryToolIT

### ✅ pureDigits_shouldCallQueryContractsByOrderId

- **输入:** 825123110000002753的合同详情
- **输出:** {"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54"},{"nodeType"...（截断）
- **耗时:** 17235ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 68ms

### ✅ contractCodePrefix_shouldCallQueryContractData

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 8714ms
- **工具调用:**
  - `queryContractData` ✓ 30ms

### ✅ contractCodeWithUserType_shouldCallQueryContractData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 2023ms
- **工具调用:**
  - `queryContractData` ✓ 24ms

### ✅ contractCode_shouldNotCallOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 8974ms
- **工具调用:**
  - `queryContractData` ✓ 27ms

### ✅ orderIdKeyword_shouldCallQueryContractsByOrderId

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 18046ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 59ms

### ✅ contractCodeWithNodeType_shouldCallQueryContractData

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 6983ms
- **工具调用:**
  - `queryContractData` ✓ 24ms

---

## HttpEndpointToolIT

### ✅ listEndpointsByCategory_shouldCallListAvailableEndpoints

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息      描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。      使用场景：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"      参数说明：      - `homeOrderNo`：订单号（必填），纯数字格式      - `qu...（截断）
- **耗时:** 7783ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 1ms

### ✅ listEndpointsKeyword_shouldCallListAvailableEndpoints

- **输入:** 有哪些可用的预定义接口
- **输出:** 以下是可用的预定义接口列表：  1. **sub-order-info** - 查询子单基本信息      描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。      使用场景：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"      参数说明：      - homeOrderNo（必需）：订单号，纯数字格式      - quotationOrder...（截断）
- **耗时:** 6537ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 1ms

---

