# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 12:13:32

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractSignedObjects_shouldCallOntologyQuery

- **输入:** C1767173898135504的签约单据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 3130ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ contractNodes_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2325ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ contractBasic_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2612ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ budgetBill_shouldCallOntologyQuery

- **输入:** 826031111000001859的报价单
- **输出:** {"error":"未找到BudgetBill: 826031111000001859"}
- **耗时:** 17053ms
- **工具调用:**
  - `ontologyQuery` ✓ 1009ms
  - `ontologyQuery` ✓ 1104ms

### ✅ orderContract_contractSignedObjAndNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2608ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ orderContract_signedObjects_shouldCallOntologyQuery

- **输入:** 825123110000002753合同的签约单据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2679ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ orderContract_allData_shouldCallOntologyQuery

- **输入:** 825123110000002753下的合同数据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2264ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ orderContract_contractNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2975ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ contractConfig_shouldCallOntologyQuery

- **输入:** C1767173898135504的配置表
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2452ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ contractFields_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同字段
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2020ms
- **工具调用:**
  - `ontologyQuery` ✓ 1104ms

### ✅ contractForm_shouldCallOntologyQuery

- **输入:** C1767173898135504的版式
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2017ms
- **工具调用:**
  - `ontologyQuery` ✓ 1009ms
  - `ontologyQuery` ✓ 1104ms

---

