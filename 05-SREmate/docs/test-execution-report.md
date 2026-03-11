# SREmate 集成测试执行报告

> 最后更新: 2026-03-11 21:57:16

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## StartupIT

### ✅ sreAgent_shouldRespondToSimpleQuestion

- **输入:** 你好，你是谁？
- **输出:** 你好，我是SRE值班客服Agent，专门帮助研发人员快速排查和解决运维问题。你可以向我咨询任何与运维相关的问题，比如系统故障、配置查询、合同数据、报价单信息等。请告诉我你需要什么帮助？
- **耗时:** 1471ms
- **工具调用:** 无

### ✅ applicationContext_shouldLoad

- **耗时:** 0ms
- **工具调用:** 无

---

## SkillQueryToolIT

### ✅ listSkillCategories_shouldReturnCategories

- **输入:** 运维知识库有哪些分类
- **输出:** 运维知识库的分类包括：diagnosis（诊断）、operations（运维）、knowledge（知识）。
- **耗时:** 1082ms
- **工具调用:**
  - `listSkillCategories` ✓ 0ms

### ✅ querySkills_serviceTimeout_shouldReturnRunbook

- **输入:** 服务超时怎么处理
- **输出:** 服务超时的处理方案如下：  ### 短期解决 1. **扩容实例**：增加服务实例数，缓解负载压力 2. **调整超时时间**：临时增加客户端超时时间（如将超时时间从3000ms调整为5000ms） 3. **限流降级**：对非核心功能进行降级，保证核心功能可用  ### 长期优化 1. **性能优化**：    - 优化慢接口，减少响应时间    - 增加缓存，减少数据库访问    - 异步化处理，降低同步等待时间  2. **架构优化**：    - 服务拆分，避免单点瓶颈    - 引入消息队列，解耦系统组件    - 实施熔断机制，防止雪崩效应  3. **监控告警**：    - 设...（截断）
- **耗时:** 5425ms
- **工具调用:**
  - `querySkills` ✓ 11ms

### ✅ querySkills_databaseTimeout_shouldReturnRunbook

- **输入:** 数据库连接超时怎么排查
- **输出:** 1. 检查数据库连接数    - 使用MySQL查询工具查看当前连接数：      ```sql      SHOW PROCESSLIST;      SHOW STATUS LIKE 'Threads_connected';      SHOW STATUS LIKE 'Max_used_connections';      ```    - 关键指标：      - Threads_connected：当前活跃连接数      - Max_used_connections：历史最大连接数      - max_connections：最大允许连接数（通过 SHOW VARIABLES L...（截断）
- **耗时:** 5014ms
- **工具调用:**
  - `querySkills` ✓ 7ms

---

## ContractConfigToolIT

### ✅ configKeyword_shouldCallQueryContractConfig

- **输入:** C1767173898135504的合同配置表数据
- **输出:** {"input":"C1767173898135504","projectOrderId":"825123117000001474","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V2018002...（截断）
- **耗时:** 6370ms
- **工具调用:**
  - `queryContractConfig` ✓ 410ms

### ✅ orderIdWithoutType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 5339ms
- **工具调用:**
  - `queryContractConfig` ✓ 51ms

### ✅ orderIdWithConfigType_shouldCallQueryContractConfig

- **输入:** 825123110000002753的销售合同配置
- **输出:** {"input":"825123110000002753","projectOrderId":"825123110000002753","contractConfigId":"2_2_2","version":2,"businessType":"1","gbCode":"310000","companyCode":"V201800236","type":"8","signChannelType":1,"contract_city_company_info":[{"id":422,"business_type":1,"gb_code":310000,"company_code":"V201800...（截断）
- **耗时:** 4827ms
- **工具调用:**
  - `queryContractConfig` ✓ 43ms

---

## ContractInstanceToolIT

### ✅ instanceIdKeyword_shouldCallQueryContractInstanceId

- **输入:** C1767173898135504的 platform_instance_id 是多少
- **输出:** {"contractCode":"C1767173898135504","platformInstanceId":0}
- **耗时:** 1287ms
- **工具调用:**
  - `queryContractInstanceId` ✓ 22ms

### ✅ formIdKeyword_shouldCallQueryContractFormId

- **输入:** C1767173898135504的版式 form_id 是多少
- **输出:** 根据返回结果，查询合同版式表单数据时出现了服务端异常。建议您稍后重试或联系客服处理。
- **耗时:** 1872ms
- **工具调用:**
  - `queryContractFormId` ✓ 442ms
  - `callPredefinedEndpoint` ✓ 424ms

---

## SubOrderToolIT

### ✅ subOrderWithQuotation_shouldCallQuerySubOrderInfo

- **输入:** 825123110000002753下GBILL260309110407580001的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[],"success":true}
- **耗时:** 1633ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 81ms
  - `callPredefinedEndpoint` ✓ 81ms

### ✅ subOrderKeyword_shouldCallQuerySubOrderInfo

- **输入:** 查询订单825123110000002753的子单信息
- **输出:** {"code":2000,"message":"操作成功","data":[{"orderNo":"S14251231110001838","status":1000,"scmFullNo":"","projectChangeNo":"","retailSaleProjectNo":"825123110000002459","mdmCode":"V201800236","orderCreateTime":1767150675000,"dueAmount":10.00,"compositOrderNo":"CT202512310000285301"},{"orderNo":"S142512311...（截断）
- **耗时:** 17123ms
- **工具调用:**
  - `querySubOrderInfo` ✓ 173ms
  - `callPredefinedEndpoint` ✓ 172ms

---

## ContractQueryToolIT

### ✅ pureDigits_shouldCallQueryContractsByOrderId

- **输入:** 825123110000002753的合同详情
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 15097ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 66ms

### ✅ contractCodePrefix_shouldCallQueryContractData

- **输入:** C1767173898135504的合同数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 9357ms
- **工具调用:**
  - `queryContractData` ✓ 23ms

### ✅ contractCodeWithUserType_shouldCallQueryContractData

- **输入:** C1767173898135504的签约人信息
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_user":[{"role_type":1,"name":"张云","phone":"B002ɧLƑ5974","is_sign":1,"is_auth":0}]}
- **耗时:** 2298ms
- **工具调用:**
  - `queryContractData` ✓ 22ms

### ✅ contractCode_shouldNotCallOrderTool

- **输入:** 查询C1767173898135504的合同详情
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 7790ms
- **工具调用:**
  - `queryContractData` ✓ 26ms

### ✅ orderIdKeyword_shouldCallQueryContractsByOrderId

- **输入:** 订单825123110000002753下有哪些合同
- **输出:** {"contracts":[{"contractCode":"C1767150648920281","type":3,"status":8,"amount":413580.67,"platformInstanceId":0,"ctime":"2025-12-31 11:10:48","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 11:10:42"},{"nodeType":8,"fireTime":"2025-12-31 11:10:42"},{"nodeType":9,"fireTime":"2025-12-31 11:10:54...（截断）
- **耗时:** 13342ms
- **工具调用:**
  - `queryContractsByOrderId` ✓ 65ms

### ✅ contractCodeWithNodeType_shouldCallQueryContractData

- **输入:** C1767173898135504的合同节点数据
- **输出:** {"contractCode":"C1767173898135504","type":8,"status":8,"amount":200.00,"platformInstanceId":0,"projectOrderId":"825123117000001474","ctime":"2025-12-31 17:38:20","contract_node":[{"nodeType":7,"fireTime":"2025-12-31 17:38:01"},{"nodeType":8,"fireTime":"2025-12-31 17:38:01"},{"nodeType":2,"fireTime"...（截断）
- **耗时:** 7369ms
- **工具调用:**
  - `queryContractData` ✓ 23ms

---

## HttpEndpointToolIT

### ✅ listEndpointsByCategory_shouldCallListAvailableEndpoints

- **输入:** 查看 contract 分类的接口
- **输出:** 根据 contract 分类的接口列表如下：  1. **sub-order-info** - 查询子单基本信息    - 描述: 根据订单号查询对应的子单信息，支持按报价单号和变更单号筛选。    - 使用场景:      - "查询某订单下某报价单的子单信息"      - "某订单下某报价单对应的子单是什么"      - "查询订单xxx下报价单xxx的子单"      - "826030611000000795下GBILL260309110407580001的子单信息"    - 参数:      - homeOrderNo (必需), quotationOrderNo (可选), ...（截断）
- **耗时:** 7700ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 2ms

### ✅ listEndpointsKeyword_shouldCallListAvailableEndpoints

- **输入:** 有哪些可用的预定义接口
- **输出:** 可用的预定义接口分类有：  - system（系统相关） - database（数据库相关） - monitoring（监控相关） - contract（签约相关）  您可以指定具体分类来查看对应的接口。
- **耗时:** 1742ms
- **工具调用:**
  - `listAvailableEndpoints` ✓ 0ms

---

