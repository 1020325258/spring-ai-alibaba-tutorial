# SREmate 集成测试执行报告

> 最后更新: 2026-03-14 00:23:37

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好！我是SRE值班客服Agent，专注于帮助研发人员快速排查和解决运维问题。你可以向我咨询关于合同、订单、报价单、子单等相关问题，或者描述你遇到的技术问题，我会尽力提供帮助。有什么可以帮你的吗？
- **耗时:** 1710ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 0ms
- **工具调用:** 无

---

## PersonalQuoteToolIT

### ✅ personalQuoteKeyword_withSubOrder_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"code":2000,"message":"操作成功","data":{"planAllDTO":null,"homeOrderNo":null,"previousHomeOrderNo":null,"nextHomeOrderNo":null,"personalContractDataList":[{"billCode":null,"changeOrderId":null,"subOrderNoList":["S15260312120004471"],"personalContractPrice":8.00,"comboCode":"2412","comboName":"新团装定软电默认...（截断）
- **耗时:** 11995ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 774ms
  - `callPredefinedEndpoint` ✓ 772ms

### ✅ personalQuoteKeyword_shouldNotCallBudgetBillTool

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"code":2000,"message":"操作成功","data":{"planAllDTO":null,"homeOrderNo":null,"previousHomeOrderNo":null,"nextHomeOrderNo":null,"personalContractDataList":[{"billCode":null,"changeOrderId":null,"subOrderNoList":["S15260312120004471"],"personalContractPrice":8.00,"comboCode":"2412","comboName":"新团装定软电默认...（截断）
- **耗时:** 11739ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 799ms
  - `callPredefinedEndpoint` ✓ 799ms

### ✅ personalQuoteKeyword_withBillCode_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** 该请求返回了错误信息，提示"查询团装报价单附件为空，请重试或联系客服"。这表明在查询个性化报价数据时遇到了问题。  ### 建议的解决步骤： 1. **重试查询**：有时可能是临时性的问题，尝试重新执行查询。 2. **检查输入参数**：确保提供的订单号和报价单号是正确的。 3. **联系客服**：如果问题仍然存在，建议联系客服以获取进一步的帮助。  如果需要进一步的帮助，请告知我。
- **耗时:** 3271ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 805ms
  - `callPredefinedEndpoint` ✓ 805ms

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** SRE运维知识库包含以下分类：  - **diagnosis**：问题诊断类，用于排查和解决技术问题 - **operations**：运维操作类，提供日常运维操作指导 - **knowledge**：知识类，包含技术原理、系统架构等知识性内容  如需查询具体分类下的内容，可以告诉我需要哪个分类的资料。
- **耗时:** 1685ms
- **工具调用:**
  - `listSkillCategories` ✓ 1ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** ### 服务超时处理建议  #### 短期解决 1. **扩容实例**：增加服务实例数以分担负载。 2. **调整超时时间**：临时增加客户端超时时间，避免因短暂延迟导致的超时。 3. **限流降级**：对非核心功能进行降级，确保核心功能的可用性。  #### 长期优化 1. **性能优化**：    - 优化慢接口，减少响应时间。    - 增加缓存机制，减少重复计算和数据库查询。    - 异步化处理，将耗时操作放入队列中异步执行。  2. **架构优化**：    - 服务拆分，将复杂业务拆分为独立服务，提高可维护性和扩展性。    - 引入消息队列，解耦系统组件，提高系统的可靠性和伸缩...（截断）
- **耗时:** 7031ms
- **工具调用:**
  - `querySkills` ✓ 13ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 1. **检查数据库连接数**：    - 使用以下SQL查询当前连接数和状态：      ```sql      SHOW PROCESSLIST;      SHOW STATUS LIKE 'Threads_connected';      SHOW STATUS LIKE 'Max_used_connections';      ```    - 关注指标：      - `Threads_connected`：当前活跃连接数      - `Max_used_connections`：历史最大连接数      - `max_connections`：最大允许连接数（通过 `SHOW ...（截断）
- **耗时:** 10408ms
- **工具调用:**
  - `querySkills` ✓ 4ms

---

## ContractConfigToolIT

### ✅ configKeyword_shouldCallQueryContractConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 7024ms
- **工具调用:**
  - `queryContractConfig` ✓ 428ms

### ✅ orderIdWithoutType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6946ms
- **工具调用:**
  - `queryContractConfig` ✓ 105ms

### ✅ orderIdWithConfigType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 5965ms
- **工具调用:**
  - `queryContractConfig` ✓ 104ms

---

## ContractInstanceToolIT

### ✅ instanceIdKeyword_shouldCallQueryContractInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1187ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 47ms

### ✅ formIdKeyword_shouldCallQueryContractFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据接口返回结果，查询合同版式表单数据时出现了服务端异常。建议您稍后重试，如果问题仍然存在，请联系客服人员进行进一步处理。
- **耗时:** 1457ms
- **工具调用:**
  - `queryContractFormId` ✓ 120ms
  - `callPredefinedEndpoint` ✓ 77ms

---

## SubOrderToolIT

### ✅ subOrderWithQuotation_shouldCallQuerySubOrderInfo

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1912ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 101ms
  - `callPredefinedEndpoint` ✓ 100ms

### ✅ subOrderKeyword_shouldCallQuerySubOrderInfo

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S1425123110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S1425123110...（截断）
- **耗时:** 22363ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 122ms
  - `callPredefinedEndpoint` ✓ 122ms

---

## ContractQueryToolIT

### ✅ contractCodePrefix_shouldCallQueryContractBasic

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 9675ms
- **工具调用:**
  - `queryContractData` ✓ 55ms

### ✅ pureDigits_shouldCallQueryContractsByOrderId

- **输入:** 825123110000002753的合同详情
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 17822ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 169ms

### ✅ contractCodeWithUserType_shouldCallQueryContractData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 2920ms
- **工具调用:**
  - `queryContractData` ✓ 50ms

### ✅ contractCode_shouldNotCallOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 15159ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 41ms
  - `queryContractSignedObjects` ✓ 42ms
  - `queryContractFields` ✓ 43ms
  - `queryContractNodes` ✓ 61ms
  - `queryContractBasic` ✓ 42ms

### ✅ contractCodeWithNodeType_shouldCallQueryContractNodes

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"code":5000,"message":"服务端异常，请稍后重试或联系客服。","data":null,"success":false}
- **耗时:** 3576ms
- **工具调用:**
  - `queryContractFormId` ✓ 123ms
  - `callPredefinedEndpoint` ✓ 81ms
  - `queryContractBasic` ✓ 43ms
  - `queryContractNodes` ✓ 43ms

### ✅ orderIdKeyword_shouldCallQueryContractsByOrderId

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 18257ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 161ms

---

## HttpEndpointToolIT

### ✅ listEndpointsByCategory_shouldCallListAvailableEndpoints

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息    - 描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。    - 使用场景：当用户询问以下问题时使用此接口：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"    - 参数说明：      - homeOrderNo：订单号（必填），纯数...（截断）
- **耗时:** 10498ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 0ms

### ✅ listEndpointsKeyword_shouldCallListAvailableEndpoints

- **输入:** 有哪些可用的预定义接口
- **输出:** 目前可用的预定义接口分类有： - system（系统相关） - database（数据库相关） - monitoring（监控相关） - contract（合同相关）
- **耗时:** 1182ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 0ms

---

