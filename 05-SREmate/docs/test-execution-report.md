# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 19:58:01

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询技术问题或寻求运维支持。
- **耗时:** 1363ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 0ms
- **工具调用:** 无

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** SRE运维知识库包含以下分类：  1. **diagnosis**（问题诊断）：用于查询故障排查经验、解决方案和常见问题处理方法。 2. **operations**（运维操作）：提供运维操作指南、配置说明和系统管理相关知识。 3. **knowledge**（知识库）：包含通用技术知识、系统原理和最佳实践等。  如果需要进一步了解某个分类的内容，可以告诉我具体分类名称。
- **耗时:** 2035ms
- **工具调用:**
  - `listSkillCategories` ✓ 1ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** 根据知识库中的信息，服务超时问题可以按照以下步骤进行处理：  ### 短期解决 1. **扩容实例**：增加服务实例数以分担负载。 2. **调整超时时间**：临时增加客户端超时时间，避免因短暂延迟导致的超时。 3. **限流降级**：对非核心功能进行降级，确保核心功能的可用性。  ### 长期优化 1. **性能优化**：    - 优化慢接口，减少响应时间。    - 增加缓存机制，减少重复计算和数据库查询。    - 异步化处理耗时操作，提高整体效率。  2. **架构优化**：    - 服务拆分，将复杂的服务分解为更小、更易管理的微服务。    - 引入消息队列，解耦服务间的依赖，提...（截断）
- **耗时:** 4888ms
- **工具调用:**
  - `querySkills` ✓ 11ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 根据数据库连接超时的问题排查经验，以下是详细的排查建议：  ### 短期解决 1. **增加连接池大小**：临时调大连接池maximum-pool-size 2. **释放空闲连接**：重启应用释放长期占用的连接 3. **优化慢查询**：识别并优化执行时间长的SQL  ### 长期优化 1. **优化连接池配置**：    - maximum-pool-size: 根据业务并发量设置（公式：(core_count * 2) + effective_spindle_count）    - minimum-idle: 设置为与maximum-pool-size相同，避免连接创建开销    - c...（截断）
- **耗时:** 5447ms
- **工具调用:**
  - `querySkills` ✓ 4ms

---

## ContractConfigToolIT

### ✅ queryContractConfig_byOrderId_withoutType_shouldAskForType

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6543ms
- **工具调用:**
  - `queryContractConfig` ✓ 384ms

### ✅ queryContractConfig_byOrderId_withContractType_shouldReturnConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 6253ms
- **工具调用:**
  - `queryContractConfig` ✓ 55ms

### ✅ queryContractConfig_byContractCode_shouldReturnConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 4924ms
- **工具调用:**
  - `queryContractConfig` ✓ 43ms

---

## ContractInstanceToolIT

### ✅ queryContractInstanceId_shouldReturnInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1527ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 22ms

### ✅ queryContractFormId_shouldReturnFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据返回结果，查询版式 form_id 时出现了服务端异常。建议您稍后重试或联系客服处理。  如果需要进一步帮助，请提供更多信息。
- **耗时:** 2231ms
- **工具调用:**
  - `queryContractFormId` ✓ 512ms
  - `callPredefinedEndpoint` ✓ 494ms

---

## SubOrderToolIT

### ✅ querySubOrderInfo_byOrderAndQuotation_shouldReturnSubOrderData

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1676ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 133ms
  - `callPredefinedEndpoint` ✓ 131ms

### ✅ querySubOrderInfo_byOrderOnly_shouldReturnSubOrderData

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14251231110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S142512311...（截断）
- **耗时:** 21124ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 126ms
  - `callPredefinedEndpoint` ✓ 124ms

---

## ContractQueryToolIT

### ✅ queryContractsByOrderId_withOrderId_shouldReturnContractList

- **输入:** 825123110000002753的合同详情
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 17709ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 101ms

### ✅ queryContractData_withContractCode_shouldReturnContractData

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 10122ms
- **工具调用:**
  - `queryContractData` ✓ 21ms

### ✅ queryContractData_contractUserType_shouldReturnUserData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 2261ms
- **工具调用:**
  - `queryContractData` ✓ 22ms

### ✅ queryContractData_withCPrefix_shouldNotUseOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 8972ms
- **工具调用:**
  - `queryContractData` ✓ 22ms

### ✅ queryContractData_contractNodeType_shouldReturnNodeData

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 7058ms
- **工具调用:**
  - `queryContractData` ✓ 21ms

### ✅ queryContractsByOrderId_pureDigits_shouldNotUseContractTool

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 14339ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 214ms

---

## HttpEndpointToolIT

### ✅ listAvailableEndpoints_shouldReturnEndpointList

- **输入:** 有哪些可用的预定义接口
- **输出:** 当前可用的预定义接口分类有：system（系统相关）、database（数据库相关）、monitoring（监控相关）、contract（签约相关）。您可以指定具体分类来查看对应的接口。
- **耗时:** 1558ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 6ms

### ✅ listAvailableEndpoints_byCategory_shouldFilterCorrectly

- **输入:** 查看 contract 分类的接口
- **输出:** 以下是 contract 分类的可用接口列表：  1. **sub-order-info** - 查询子单基本信息      描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。      使用场景：      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"      参数说明：      - `homeOrderNo`：订单号（必填），纯数字格式      - `qu...（截断）
- **耗时:** 8446ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 3ms

---

