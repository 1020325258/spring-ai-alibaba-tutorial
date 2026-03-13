# SREmate 集成测试执行报告

> 最后更新: 2026-03-14 00:15:27

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好！我是SRE值班客服Agent，专注于帮助研发人员快速排查和解决运维问题。你可以向我咨询关于合同、订单、报价单、子单等相关问题，或者描述你遇到的系统异常、报错等问题，我会提供相应的解决方案和建议。有什么需要帮助的吗？
- **耗时:** 1944ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 1ms
- **工具调用:** 无

---

## PersonalQuoteToolIT

### ✅ personalQuoteKeyword_withSubOrder_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"code":2000,"message":"操作成功","data":{"planAllDTO":null,"homeOrderNo":null,"previousHomeOrderNo":null,"nextHomeOrderNo":null,"personalContractDataList":[{"billCode":null,"changeOrderId":null,"subOrderNoList":["S15260312120004471"],"personalContractPrice":8.00,"comboCode":"2412","comboName":"新团装定软电默认...（截断）
- **耗时:** 12304ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 811ms
  - `callPredefinedEndpoint` ✓ 810ms

### ✅ personalQuoteKeyword_shouldNotCallBudgetBillTool

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"code":2000,"message":"操作成功","data":{"planAllDTO":null,"homeOrderNo":null,"previousHomeOrderNo":null,"nextHomeOrderNo":null,"personalContractDataList":[{"billCode":null,"changeOrderId":null,"subOrderNoList":["S15260312120004471"],"personalContractPrice":8.00,"comboCode":"2412","comboName":"新团装定软电默认...（截断）
- **耗时:** 13882ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 1054ms
  - `callPredefinedEndpoint` ✓ 1054ms

### ✅ personalQuoteKeyword_withBillCode_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** 该请求返回的错误信息为：  ``` "查询团装报价单附件为空，请重试或联系客服" ```  ### 建议操作： 1. **确认报价单编号**：请核对 `GBILL260312104241050001` 是否正确，确保该报价单存在且包含附件。 2. **重新尝试**：在确认无误后，重新发起请求。 3. **联系客服**：若问题持续存在，建议联系系统客服获取进一步帮助。
- **耗时:** 3614ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 625ms
  - `callPredefinedEndpoint` ✓ 624ms

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** 运维知识库的分类包括：diagnosis（诊断）、operations（运维）、knowledge（知识）。
- **耗时:** 1093ms
- **工具调用:**
  - `listSkillCategories` ✓ 1ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** 服务超时的处理方案如下：  ### 短期解决 1. **扩容实例**：增加服务实例数，提升系统处理能力 2. **调整超时时间**：临时增加客户端超时时间（如将超时时间从3秒调整为5秒） 3. **限流降级**：对非核心功能进行降级，保障核心业务可用性  ### 长期优化 1. **性能优化**：    - 优化慢接口，减少响应时间    - 增加缓存机制，降低数据库压力    - 异步化处理耗时操作     2. **架构优化**：    - 服务拆分，避免单点过载    - 引入消息队列，削峰填谷    - 实施熔断机制，防止雪崩效应  3. **监控告警**：    - 设置响应时间告警...（截断）
- **耗时:** 5407ms
- **工具调用:**
  - `querySkills` ✓ 9ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 1. **检查数据库连接数**：    - 使用以下SQL查询当前连接数和状态：      ```sql      SHOW PROCESSLIST;      SHOW STATUS LIKE 'Threads_connected';      SHOW STATUS LIKE 'Max_used_connections';      ```    - 关注指标：`Threads_connected`（当前活跃连接数）、`Max_used_connections`（历史最大连接数）、`max_connections`（最大允许连接数）。  2. **检查连接池配置**：    - 查看应用连...（截断）
- **耗时:** 10670ms
- **工具调用:**
  - `querySkills` ✓ 4ms

---

## ContractConfigToolIT

### ✅ configKeyword_shouldCallQueryContractConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 5999ms
- **工具调用:**
  - `queryContractConfig` ✓ 476ms

### ✅ orderIdWithoutType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6106ms
- **工具调用:**
  - `queryContractConfig` ✓ 123ms

### ✅ orderIdWithConfigType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6105ms
- **工具调用:**
  - `queryContractConfig` ✓ 130ms

---

## ContractInstanceToolIT

### ✅ instanceIdKeyword_shouldCallQueryContractInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1384ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 48ms

### ✅ formIdKeyword_shouldCallQueryContractFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据返回结果，查询合同版式表单数据时出现了服务端异常。建议您稍后重试或联系客服人员获取进一步帮助。如果需要，我可以协助您联系相关技术支持团队。
- **耗时:** 1814ms
- **工具调用:**
  - `queryContractFormId` ✓ 142ms
  - `callPredefinedEndpoint` ✓ 91ms

---

## SubOrderToolIT

### ✅ subOrderWithQuotation_shouldCallQuerySubOrderInfo

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 2316ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 93ms
  - `callPredefinedEndpoint` ✓ 92ms

### ✅ subOrderKeyword_shouldCallQuerySubOrderInfo

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14251231110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S142512311...（截断）
- **耗时:** 20445ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 115ms
  - `callPredefinedEndpoint` ✓ 114ms

---

## ContractQueryToolIT

### ✅ contractCodePrefix_shouldCallQueryContractBasic

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 11559ms
- **工具调用:**
  - `queryContractData` ✓ 55ms

### ✅ pureDigits_shouldCallQueryContractsByOrderId

- **输入:** 825123110000002753的合同详情
- **输出:** {   "合同编号": "C1767150648920281",   "合同类型": 3,   "合同状态": 8,   "合同金额": 413580.67,   "平台实例ID": 0,   "创建时间": "2025-12-31 11:10:48",   "合同节点": [     {"节点类型": 7, "触发时间": "2025-12-31 11:10:42"},     {"节点类型": 8, "触发时间": "2025-12-31 11:10:42"},     {"节点类型": 9, "触发时间": "2025-12-31 11:10:54"},     {"节点类型": 2, ...（截断）
- **耗时:** 11086ms
- **工具调用:**
  - `queryContractSignedObjects` ✓ 48ms
  - `queryContractFields` ✓ 60ms
  - `queryContractNodes` ✓ 51ms
  - `queryContractBasic` ✓ 53ms
  - `queryContractsByOrderId` ✓ 181ms

### ✅ contractCodeWithUserType_shouldCallQueryContractData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 3073ms
- **工具调用:**
  - `queryContractData` ✓ 48ms

### ✅ contractCode_shouldNotCallOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 10520ms
- **工具调用:**
  - `queryContractData` ✓ 52ms

### ✅ contractCodeWithNodeType_shouldCallQueryContractNodes

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","contract_node":[{"node_type":7,"fire_time":"2025-12-31 17:38:01"},{"node_type":8,"fire_time":"2025-12-31 17:38:01"},{"node_type":2,"fire_time":"2025-12-31 17:38:20"},{"node_type":3,"fire_time":"2025-12-31 17:38:24"},{"node_type":9,"fire_time":"2025-12-31 17:38:37...（截断）
- **耗时:** 3391ms
- **工具调用:**
  - `queryContractNodes` ✓ 50ms

### ✅ orderIdKeyword_shouldCallQueryContractsByOrderId

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 18060ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 187ms

---

## HttpEndpointToolIT

### ✅ listEndpointsByCategory_shouldCallListAvailableEndpoints

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息      描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。      参数: `homeOrderNo` (必需), `quotationOrderNo` (可选), `projectChangeNo` (可选)    2. **contract-personal-data** - 查询订单个性化报价数据      描述: 根据项目订单号及单据号查询该订单下对应单据的个性化报价数据。      参数: `projectOrderId` (必需), `subOr...（截断）
- **耗时:** 4955ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 3ms

### ✅ listEndpointsKeyword_shouldCallListAvailableEndpoints

- **输入:** 有哪些可用的预定义接口
- **输出:** 可用的预定义接口分类包括：system（系统）、database（数据库）、monitoring（监控）和contract（合同）。您可以指定具体的分类来查看该分类下的可用接口。
- **耗时:** 1501ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 1ms

---

