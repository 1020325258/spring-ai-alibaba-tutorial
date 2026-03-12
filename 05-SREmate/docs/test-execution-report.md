# SREmate 集成测试执行报告

> 最后更新: 2026-03-12 09:34:16

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询任何与运维相关的问题，比如系统故障、配置查询、合同数据、报价单信息等。请告诉我你需要什么帮助？
- **耗时:** 2064ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 0ms
- **工具调用:** 无

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** SRE运维知识库包含以下分类：  1. **diagnosis（问题诊断）**：用于查询技术问题的排查经验和解决方案，例如超时、报错、异常等。 2. **operations（运维操作）**：提供运维操作指南和步骤，例如服务重启、配置调整等。 3. **knowledge（知识库）**：包含通用的运维知识和概念，例如系统架构、最佳实践等。  如果您有具体的问题或需要了解某个分类的详细内容，请告诉我！
- **耗时:** 2648ms
- **工具调用:**
  - `listSkillCategories` ✓ 2ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** 服务超时问题需要根据具体场景进行排查和处理。以下是针对服务超时的详细分析和解决方案：  ### 1. **检查服务健康状态**    - 调用服务健康检查接口，确认服务是否正常运行。    - 示例：`GET /actuator/health`  ### 2. **检查服务指标**    - 查看关键性能指标：      - 响应时间（p95、p99）      - 错误率      - QPS（每秒查询数）  ### 3. **检查日志**    - 查看服务日志中的异常和慢请求记录，定位具体问题。  ### 4. **检查资源使用**    - 监控以下资源使用情况：      - CPU...（截断）
- **耗时:** 7422ms
- **工具调用:**
  - `querySkills` ✓ 39ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 根据数据库连接超时的排查经验，以下是具体的排查建议：  ### 短期解决 1. **增加连接池大小**：临时调大连接池maximum-pool-size。 2. **释放空闲连接**：重启应用以释放长期占用的连接。 3. **优化慢查询**：识别并优化执行时间长的SQL。  ### 长期优化 1. **优化连接池配置**：    - `maximum-pool-size`：根据业务并发量设置（公式：`(core_count * 2) + effective_spindle_count`）。    - `minimum-idle`：设置为与`maximum-pool-size`相同，避免连接创建...（截断）
- **耗时:** 3883ms
- **工具调用:**
  - `querySkills` ✓ 7ms

---

## ContractConfigToolIT

### ✅ configKeyword_shouldCallQueryContractConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 5765ms
- **工具调用:**
  - `queryContractConfig` ✓ 319ms

### ✅ orderIdWithoutType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 7008ms
- **工具调用:**
  - `queryContractConfig` ✓ 55ms

### ✅ orderIdWithConfigType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 5613ms
- **工具调用:**
  - `queryContractConfig` ✓ 48ms

---

## ContractInstanceToolIT

### ✅ instanceIdKeyword_shouldCallQueryContractInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1713ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 19ms

### ✅ formIdKeyword_shouldCallQueryContractFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据接口返回结果，查询合同版式表单数据时出现了服务端异常。建议您稍后重试或联系客服处理。
- **耗时:** 1725ms
- **工具调用:**
  - `queryContractFormId` ✓ 267ms
  - `callPredefinedEndpoint` ✓ 247ms

---

## SubOrderToolIT

### ✅ subOrderWithQuotation_shouldCallQuerySubOrderInfo

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1922ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 151ms
  - `callPredefinedEndpoint` ✓ 149ms

### ✅ subOrderKeyword_shouldCallQuerySubOrderInfo

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {   "code": 2000,   "message": "操作成功",   "data": [     {       "orderNo": "S14251231110001838",       "status": 1000,       "scmFullNo": "",       "projectChangeNo": "",       "retailSaleProjectNo": "825123110000002459",       "mdmCode": "V201800236",       "orderCreateTime": 1767150675000,       "d...（截断）
- **耗时:** 28599ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 135ms
  - `callPredefinedEndpoint` ✓ 135ms

---

## ContractQueryToolIT

### ✅ pureDigits_shouldCallQueryContractsByOrderId

- **输入:** 825123110000002753的合同详情
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 17477ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 84ms

### ✅ contractCodePrefix_shouldCallQueryContractData

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 10847ms
- **工具调用:**
  - `queryContractData` ✓ 25ms

### ✅ contractCodeWithUserType_shouldCallQueryContractData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 2731ms
- **工具调用:**
  - `queryContractData` ✓ 20ms

### ✅ contractCode_shouldNotCallOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 10280ms
- **工具调用:**
  - `queryContractData` ✓ 20ms

### ✅ orderIdKeyword_shouldCallQueryContractsByOrderId

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 17419ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 66ms

### ✅ contractCodeWithNodeType_shouldCallQueryContractData

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 7160ms
- **工具调用:**
  - `queryContractData` ✓ 35ms

---

## HttpEndpointToolIT

### ✅ listEndpointsByCategory_shouldCallListAvailableEndpoints

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息      描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。      当用户询问以下问题时使用此接口：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"      参数说明：      - homeOrderNo：订单号（必填），纯数字格式  ...（截断）
- **耗时:** 8755ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 2ms

### ✅ listEndpointsKeyword_shouldCallListAvailableEndpoints

- **输入:** 有哪些可用的预定义接口
- **输出:** 以下是可用的预定义接口列表：  1. **sub-order-info** - 查询子单基本信息    - 描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。    - 当用户询问以下问题时使用此接口：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"    - 参数说明：      - homeOrderNo：订单号（必填），纯数字格式      - quo...（截断）
- **耗时:** 8356ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 1ms

---

