# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 21:11:36

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询技术问题或运维相关的问题，我会根据你的需求调用相应的工具获取信息，并提供解决方案。
- **耗时:** 1973ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 1ms
- **工具调用:** 无

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** SRE运维知识库包含以下分类：  - **diagnosis**：问题诊断类知识，用于排查和解决技术问题 - **operations**：运维操作类知识，提供具体的操作步骤和指导 - **knowledge**：通用知识类内容，涵盖系统原理、最佳实践等  您可以根据具体需求选择对应的分类进行查询。
- **耗时:** 1575ms
- **工具调用:**
  - `listSkillCategories` ✓ 0ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** ### 服务超时排查与解决方案  #### 1. 检查服务健康状态 调用服务健康检查接口，确认服务是否正常运行： ``` GET /actuator/health ```  #### 2. 检查服务指标 查看关键性能指标，包括： - 响应时间（p95、p99） - 错误率 - QPS（每秒查询率）  #### 3. 检查日志 查看服务日志中的异常和慢请求记录，定位具体问题。  #### 4. 检查资源使用 监控以下资源使用情况： - CPU使用率 - 内存使用率 - GC（垃圾回收）情况 - 线程池状态  #### 工具调用建议  **查询服务健康状态：** ``` tool: callHtt...（截断）
- **耗时:** 5921ms
- **工具调用:**
  - `querySkills` ✓ 13ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** ### 短期解决 1. 重启应用释放空闲连接 2. 临时调大连接池配置  ### 长期优化 1. 优化连接池配置（maximum-pool-size、minimum-idle） 2. 排查连接泄漏 3. 优化慢查询
- **耗时:** 1877ms
- **工具调用:**
  - `querySkills` ✓ 3ms

---

## ContractConfigToolIT

### ✅ queryContractConfig_byOrderId_withoutType_shouldAskForType

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6084ms
- **工具调用:**
  - `queryContractConfig` ✓ 248ms

### ✅ queryContractConfig_byOrderId_withContractType_shouldReturnConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 4747ms
- **工具调用:**
  - `queryContractConfig` ✓ 49ms

### ✅ queryContractConfig_byContractCode_shouldReturnConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 4719ms
- **工具调用:**
  - `queryContractConfig` ✓ 42ms

---

## ContractInstanceToolIT

### ✅ queryContractInstanceId_shouldReturnInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1698ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 25ms

### ✅ queryContractFormId_shouldReturnFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据返回结果，查询合同版式表单数据时出现了服务端异常。建议您：  1. 稍后重试查询操作 2. 如果问题持续存在，请联系客服团队进行进一步排查和处理
- **耗时:** 1561ms
- **工具调用:**
  - `queryContractFormId` ✓ 225ms
  - `callPredefinedEndpoint` ✓ 206ms

---

## SubOrderToolIT

### ✅ querySubOrderInfo_byOrderAndQuotation_shouldReturnSubOrderData

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1641ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 160ms
  - `callPredefinedEndpoint` ✓ 157ms

### ✅ querySubOrderInfo_byOrderOnly_shouldReturnSubOrderData

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14251231110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S142512311...（截断）
- **耗时:** 23898ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 625ms
  - `callPredefinedEndpoint` ✓ 625ms

---

## ContractQueryToolIT

### ✅ queryContractsByOrderId_withOrderId_shouldReturnContractList

- **输入:** 825123110000002753的合同详情
- **输出:** {"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54"},{"nodeType"...（截断）
- **耗时:** 16908ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 82ms

### ✅ queryContractData_withContractCode_shouldReturnContractData

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 9644ms
- **工具调用:**
  - `queryContractData` ✓ 27ms

### ✅ queryContractData_contractUserType_shouldReturnUserData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 2174ms
- **工具调用:**
  - `queryContractData` ✓ 20ms

### ✅ queryContractData_withCPrefix_shouldNotUseOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 10133ms
- **工具调用:**
  - `queryContractData` ✓ 25ms

### ✅ queryContractData_contractNodeType_shouldReturnNodeData

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 6729ms
- **工具调用:**
  - `queryContractData` ✓ 39ms

### ✅ queryContractsByOrderId_pureDigits_shouldNotUseContractTool

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 15357ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 70ms

---

## HttpEndpointToolIT

### ✅ listAvailableEndpoints_shouldReturnEndpointList

- **输入:** 有哪些可用的预定义接口
- **输出:** 目前可用的预定义接口分类有： - system（系统相关） - database（数据库相关） - monitoring（监控相关） - contract（签约相关）
- **耗时:** 1720ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 2ms

### ✅ listAvailableEndpoints_byCategory_shouldFilterCorrectly

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息    - 描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。    - 使用场景:      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"    - 参数:      - homeOrderNo（必填）：订单号，纯数字格式      - quotati...（截断）
- **耗时:** 8269ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 1ms

---

