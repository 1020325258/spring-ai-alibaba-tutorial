# SREmate 集成测试执行报告

> 最后更新: 2026-03-22 11:41:47

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractInstance_shouldUseContractEntityAndContractInstanceScope

- **输入:** C1773303150687211的版式
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 32874ms
- **工具调用:**
  - `ontologyQuery` ✓ 31161ms

### ✅ budgetBill_shouldUseOrderEntityAndBudgetBillScope

- **输入:** 826031111000001859的报价单
- **输出:** {"queryEntity":"Order","queryValue":"826031111000001859","records":[{"projectOrderId":"826031111000001859","budgetBills":[]}]}
- **耗时:** 8533ms
- **工具调用:**
  - `ontologyQuery` ✓ 7095ms

### ✅ contractInstance_directQuery_shouldUseContractInstanceEntity

- **输入:** 101835395的实例信息
- **输出:** {"error":"未找到ContractInstance: 101835395"}
- **耗时:** 15244ms
- **工具调用:**
  - `ontologyQuery` ✓ 14093ms

### ✅ contractConfig_shouldUseContractEntityAndConfigScope

- **输入:** C1767173898135504的配置表
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 32300ms
- **工具调用:**
  - `ontologyQuery` ✓ 31016ms

### ✅ contractSignedObjects_shouldUseContractEntityAndQuotationRelationScope

- **输入:** C1767173898135504的签约单据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 32365ms
- **工具调用:**
  - `ontologyQuery` ✓ 31011ms

### ✅ subOrder_shouldUseOrderEntityAndSubOrderScope

- **输入:** 826031111000001859的S单
- **输出:** {"queryEntity":"Order","queryValue":"826031111000001859","records":[{"projectOrderId":"826031111000001859","budgetBills":[]}]}
- **耗时:** 8712ms
- **工具调用:**
  - `ontologyQuery` ✓ 7075ms

### ✅ orderContract_shouldUseOrderEntityAndContractScope

- **输入:** 825123110000002753下的合同
- **输出:** {"error":"org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection"}
- **耗时:** 32633ms
- **工具调用:**
  - `ontologyQuery` ✓ 31019ms

### ✅ contractFields_shouldUseContractEntityAndFieldScope

- **输入:** C1767173898135504的合同字段
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 32550ms
- **工具调用:**
  - `ontologyQuery` ✓ 31009ms

### ✅ orderContract_signedObjectsAndNodes_shouldUseOrderEntityAndMultipleScopes

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"error":"org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection"}
- **耗时:** 32388ms
- **工具调用:**
  - `ontologyQuery` ✓ 31015ms

### ✅ contractBasic_shouldUseContractEntity

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 32170ms
- **工具调用:**
  - `ontologyQuery` ✓ 31021ms

### ✅ contractNodes_shouldUseContractEntityAndContractNodeScope

- **输入:** C1767173898135504的合同节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 32492ms
- **工具调用:**
  - `ontologyQuery` ✓ 31009ms

---

