# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 18:23:20

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询任何与运维相关的问题，我会根据你的描述提供相应的解决方案。
- **耗时:** 1923ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 1ms
- **工具调用:** 无

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** SRE运维知识库包含以下分类：  - **diagnosis**：问题诊断类，用于排查和解决技术问题（如超时、报错、异常） - **operations**：运维操作类，提供具体的运维操作指导 - **knowledge**：知识库类，包含常见问题和解决方案的文档  需要查询具体分类内容时，可以告诉我您感兴趣的分类名称。
- **耗时:** 2224ms
- **工具调用:**
  - `listSkillCategories` ✓ 0ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** 针对服务超时问题，以下是详细的排查和解决方案：  ### 短期解决 1. **扩容实例**：增加服务实例数以分担负载。 2. **调整超时时间**：临时增加客户端超时时间，避免因短暂延迟导致的超时。 3. **限流降级**：对非核心功能进行降级，确保核心功能的可用性。  ### 长期优化 1. **性能优化**：    - 优化慢接口，减少响应时间。    - 增加缓存机制，减少重复计算或数据库查询。    - 异步化处理耗时操作，提高并发能力。  2. **架构优化**：    - 服务拆分，将高负载的服务独立出来，避免相互影响。    - 引入消息队列，解耦系统组件，提高系统的可扩展性和稳...（截断）
- **耗时:** 5876ms
- **工具调用:**
  - `querySkills` ✓ 13ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** ### 短期解决 1. **增加连接池大小**：临时调大连接池maximum-pool-size 2. **释放空闲连接**：重启应用释放长期占用的连接 3. **优化慢查询**：识别并优化执行时间长的SQL  ### 长期优化 1. **优化连接池配置**：    - maximum-pool-size: 根据业务并发量设置（公式：(core_count * 2) + effective_spindle_count）    - minimum-idle: 设置为与maximum-pool-size相同，避免连接创建开销    - connection-timeout: 30000ms（30秒...（截断）
- **耗时:** 5764ms
- **工具调用:**
  - `querySkills` ✓ 3ms

---

## ContractConfigToolIT

### ✅ queryContractConfig_byOrderId_withoutType_shouldAskForType

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 5944ms
- **工具调用:**
  - `queryContractConfig` ✓ 271ms

### ✅ queryContractConfig_byOrderId_withContractType_shouldReturnConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6311ms
- **工具调用:**
  - `queryContractConfig` ✓ 44ms

### ✅ queryContractConfig_byContractCode_shouldReturnConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 6116ms
- **工具调用:**
  - `queryContractConfig` ✓ 43ms

---

## ContractInstanceToolIT

### ✅ queryContractInstanceId_shouldReturnInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1519ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 18ms

### ✅ queryContractFormId_shouldReturnFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据返回结果，查询合同版式表单数据时发生了服务端异常。建议您稍后重试或联系客服处理。如果需要进一步帮助，请提供更多信息。
- **耗时:** 1485ms
- **工具调用:**
  - `queryContractFormId` ✓ 160ms
  - `callPredefinedEndpoint` ✓ 143ms

---

## SubOrderToolIT

### ✅ querySubOrderInfo_byOrderAndQuotation_shouldReturnSubOrderData

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {   "code": 2000,   "message": "操作成功",   "data": [],   "success": true }
- **耗时:** 2318ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 419ms
  - `callPredefinedEndpoint` ✓ 418ms

### ✅ querySubOrderInfo_byOrderOnly_shouldReturnSubOrderData

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** 订单825123110000002753的子单信息如下：  ```json {   "code": 2000,   "message": "操作成功",   "data": [     {       "orderNo": "S14251231110001838",       "status": 1000,       "scmFullNo": "",       "projectChangeNo": "",       "retailSaleProjectNo": "825123110000002459",       "mdmCode": "V201800236",       "ord...（截断）
- **耗时:** 29945ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 108ms
  - `callPredefinedEndpoint` ✓ 107ms

---

## ContractQueryToolIT

### ✅ queryContractsByOrderId_withOrderId_shouldReturnContractList

- **输入:** 825123110000002753的合同详情
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 20965ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 62ms

### ✅ queryContractData_withContractCode_shouldReturnContractData

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 9333ms
- **工具调用:**
  - `queryContractData` ✓ 26ms

### ✅ queryContractData_contractUserType_shouldReturnUserData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 3702ms
- **工具调用:**
  - `queryContractData` ✓ 20ms

### ✅ queryContractData_withCPrefix_shouldNotUseOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 9881ms
- **工具调用:**
  - `queryContractData` ✓ 21ms

### ✅ queryContractData_contractNodeType_shouldReturnNodeData

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 7578ms
- **工具调用:**
  - `queryContractData` ✓ 22ms

### ✅ queryContractsByOrderId_pureDigits_shouldNotUseContractTool

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** 订单825123110000002753下有以下合同：  1. 合同编号：C1767150648920281    - 类型：正签合同(3)    - 状态：已完结(8)    - 金额：413580.67    - 创建时间：2025-12-31 11:10:48    - 节点记录：[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54"},{"nodeType"...（截断）
- **耗时:** 18897ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 70ms

---

## HttpEndpointToolIT

### ✅ listAvailableEndpoints_shouldReturnEndpointList

- **输入:** 有哪些可用的预定义接口
- **输出:** 目前可用的预定义接口分类有： - system（系统相关） - database（数据库相关） - monitoring（监控相关） - contract（签约相关）  您需要查看具体某个分类下的接口吗？
- **耗时:** 2008ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 2ms

### ✅ listAvailableEndpoints_byCategory_shouldFilterCorrectly

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息    - 描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。    - 使用场景：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"    - 参数:      - homeOrderNo（必需）      - quotationOrderNo（...（截断）
- **耗时:** 7973ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 1ms

---

