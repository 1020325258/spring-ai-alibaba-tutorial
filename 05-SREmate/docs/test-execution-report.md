# SREmate 集成测试执行报告

> 最后更新: 2026-03-15 12:16:16

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## ContractOntologyIT

### ✅ contractSignedObjects_shouldCallOntologyQuery

- **输入:** C1767173898135504的签约单据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 3408ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ contractNodes_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2684ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ contractBasic_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同基本信息
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2949ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ budgetBill_shouldCallOntologyQuery

- **输入:** 826031111000001859的报价单
- **输出:** {"error":"未找到BudgetBill: 826031111000001859"}
- **耗时:** 16574ms
- **工具调用:**
  - `ontologyQuery` ✓ 1005ms
  - `ontologyQuery` ✓ 1114ms

### ✅ orderContract_contractSignedObjAndNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同签约单据和节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2796ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ orderContract_signedObjects_shouldCallOntologyQuery

- **输入:** 825123110000002753合同的签约单据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2537ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ orderContract_allData_shouldCallOntologyQuery

- **输入:** 825123110000002753下的合同数据
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2595ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ orderContract_contractNodes_shouldCallOntologyQuery

- **输入:** 825123110000002753合同节点
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2144ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ contractConfig_shouldCallOntologyQuery

- **输入:** C1767173898135504的配置表
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2146ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ contractFields_shouldCallOntologyQuery

- **输入:** C1767173898135504的合同字段
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 2044ms
- **工具调用:**
  - `ontologyQuery` ✓ 1114ms

### ✅ contractForm_shouldCallOntologyQuery

- **输入:** C1767173898135504的版式
- **输出:** {"error":"Failed to obtain JDBC Connection"}
- **耗时:** 1979ms
- **工具调用:**
  - `ontologyQuery` ✓ 1005ms
  - `ontologyQuery` ✓ 1114ms

---

