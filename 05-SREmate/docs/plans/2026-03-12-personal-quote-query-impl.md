# 个性化报价查询接口 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 SREmate Agent 新增 `queryContractPersonalData` 工具，支持根据订单号+单据号（S单/报价单/变更单）查询个性化报价数据。

**Architecture:** 新建 `PersonalQuoteTool` 工具类，向 `contract-endpoints.yml` 追加 endpoint 模板，同步更新 `sre-agent.md` 系统提示词，最后补充集成测试。

**Tech Stack:** Spring AI `@Tool`、`@DataQueryTool`、`HttpEndpointTool`、`ToolExecutionTemplate`、JUnit 5 集成测试

---

### Task 1: 追加 endpoint 模板

**Files:**
- Modify: `05-SREmate/src/main/resources/endpoints/contract-endpoints.yml`

**Step 1: 在文件末尾追加以下内容**

```yaml
  - id: contract-personal-data
    name: 查询订单个性化报价数据
    description: |
      根据项目订单号及单据号查询该订单下对应单据的个性化报价数据。
      当用户询问以下问题时使用此接口：
      - "查询某订单的个性化报价"
      - "某S单的个性化报价数据"
      - "某报价单的个性化报价"
      - "某变更单的个性化报价"
      参数说明：
      - projectOrderId：订单号（必填），纯数字
      - subOrderNoList：S单号列表（可选），逗号分隔，如 S15260312120004471
      - billCodeList：报价单号列表（可选），逗号分隔，如 GBILL260312104241050001
      - changeOrderId：变更单号（可选），与订单号格式类似
    category: contract
    urlTemplate: "http://utopia-nrs-sales-project.${env}.ttb.test.ke.com/api/contract/tool/getContractPersonalData?projectOrderId=${projectOrderId}&subOrderNoList=${subOrderNoList}&billCodeList=${billCodeList}&changeOrderId=${changeOrderId}"
    method: GET
    parameters:
      - name: projectOrderId
        type: string
        description: 项目订单号，纯数字格式
        required: true
        example: "826031210000003581"
      - name: subOrderNoList
        type: string
        description: S单号列表，逗号分隔，可为空
        required: false
        example: "S15260312120004471,S15260312120000427"
      - name: billCodeList
        type: string
        description: 报价单号列表，逗号分隔，可为空
        required: false
        example: "GBILL260312104241050001"
      - name: changeOrderId
        type: string
        description: 变更单号，可为空
        required: false
        example: ""
    headers:
      X-NRS-User-Id: "1000000000000000"
      Content-Type: "application/json"
    timeout: 15
    examples:
      - "826031210000003581的个性化报价数据"
      - "826031210000003581下S15260312120004471的个性化报价"
      - "826031210000003581下GBILL260312104241050001的个性化报价"
```

**Step 2: Commit**

```bash
git add 05-SREmate/src/main/resources/endpoints/contract-endpoints.yml
git commit -m "feat(sremate): add contract-personal-data endpoint template"
```

---

### Task 2: 编写集成测试（先写失败的测试）

**Files:**
- Create: `05-SREmate/src/test/java/com/yycome/sremate/PersonalQuoteToolIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 个性化报价查询工具集成测试
 */
class PersonalQuoteToolIT extends BaseSREIT {

    private static final String ORDER_ID = "826031210000003581";
    private static final String SUB_ORDER_NO = "S15260312120004471";
    private static final String BILL_CODE = "GBILL260312104241050001";

    @Test
    void personalQuoteKeyword_withSubOrder_shouldCallQueryContractPersonalData() {
        ask(ORDER_ID + "下" + SUB_ORDER_NO + "的个性化报价");

        assertToolCalled("queryContractPersonalData");
        assertAllToolsSuccess();
    }

    @Test
    void personalQuoteKeyword_withBillCode_shouldCallQueryContractPersonalData() {
        ask(ORDER_ID + "下" + BILL_CODE + "的个性化报价");

        assertToolCalled("queryContractPersonalData");
        assertAllToolsSuccess();
    }

    @Test
    void personalQuoteKeyword_shouldNotCallBudgetBillTool() {
        ask(ORDER_ID + "下" + SUB_ORDER_NO + "的个性化报价");

        assertToolNotCalled("queryBudgetBillList");
    }
}
```

**Step 2: 运行测试，确认失败（工具不存在，assertToolCalled 失败）**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -f pom.xml -pl 05-SREmate \
  -Dtest="PersonalQuoteToolIT" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

预期：**FAIL** —— `期望调用工具: queryContractPersonalData, 实际调用: 无`

**Step 3: Commit 测试文件**

```bash
git add 05-SREmate/src/test/java/com/yycome/sremate/PersonalQuoteToolIT.java
git commit -m "test(sremate): add failing tests for PersonalQuoteTool"
```

---

### Task 3: 实现 PersonalQuoteTool

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/PersonalQuoteTool.java`

**Step 1: 创建工具类**

```java
package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 个性化报价查询工具
 * 负责：根据订单号和单据号查询个性化报价数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalQuoteTool {

    private final HttpEndpointTool httpEndpointTool;

    @Tool(description = """
            【个性化报价查询】用户提到"个性化报价"时使用。

            触发条件：包含关键词"个性化报价"

            参数：
            - projectOrderId：纯数字订单号（必填）
            - subOrderNoList：S单号列表，逗号分隔（可选，如 S15260312120004471）
            - billCodeList：报价单号列表，逗号分隔（可选，如 GBILL260312104241050001）
            - changeOrderId：变更单号（可选，格式与订单号类似）

            约束：subOrderNoList、billCodeList、changeOrderId 至少填一个

            示例：
            - "826031210000003581下S15260312120004471的个性化报价"
              → projectOrderId=826031210000003581, subOrderNoList=S15260312120004471
            - "826031210000003581的GBILL260312104241050001个性化报价"
              → projectOrderId=826031210000003581, billCodeList=GBILL260312104241050001""")
    @DataQueryTool
    public String queryContractPersonalData(String projectOrderId,
                                            String subOrderNoList,
                                            String changeOrderId,
                                            String billCodeList) {
        return ToolExecutionTemplate.execute("queryContractPersonalData", () -> {
            boolean allEmpty = isBlank(subOrderNoList)
                    && isBlank(changeOrderId)
                    && isBlank(billCodeList);
            if (allEmpty) {
                return "请提供至少一种单据号：S单号（如 S15260312120004471）、"
                        + "报价单号（如 GBILL260312104241050001）或变更单号，以便查询个性化报价数据。";
            }
            Map<String, String> params = new HashMap<>();
            params.put("projectOrderId", projectOrderId);
            params.put("subOrderNoList",  subOrderNoList  != null ? subOrderNoList  : "");
            params.put("billCodeList",    billCodeList    != null ? billCodeList    : "");
            params.put("changeOrderId",   changeOrderId   != null ? changeOrderId   : "");
            return httpEndpointTool.callPredefinedEndpoint("contract-personal-data", params);
        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

**Step 2: 运行测试，确认通过**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -f pom.xml -pl 05-SREmate \
  -Dtest="PersonalQuoteToolIT" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

预期：**PASS**（3 tests passed）

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/PersonalQuoteTool.java
git commit -m "feat(sremate): add PersonalQuoteTool for personal quote query"
```

---

### Task 4: 更新系统提示词

**Files:**
- Modify: `05-SREmate/src/main/resources/prompts/sre-agent.md`

**Step 1: 在关键词映射表新增一行**

在 `| **子单/S单/签约单** | ...` 行后追加：

```markdown
| **个性化报价** | 个性化报价查询 | `queryContractPersonalData` | 需订单号+至少一种单据号 |
```

**Step 2: 在"订单号（纯数字）+关键词"快速决策表新增一行**

在 `| {订单号}配置表 | ...` 行后追加：

```markdown
| `{订单号}个性化报价` | `queryContractPersonalData` |
```

**Step 3: 在可用工具列表 `2e. querySubOrderInfo` 后追加**

```markdown
### 2f. queryContractPersonalData
根据项目订单号及单据号查询对应单据的个性化报价数据。
- 参数：
  - projectOrderId：纯数字订单号（必填）
  - subOrderNoList：S单号列表，逗号分隔（可选，S前缀）
  - billCodeList：报价单号列表，逗号分隔（可选，GBILL前缀）
  - changeOrderId：变更单号（可选，格式与订单号类似）
- 约束：后三个参数至少填一个，否则询问用户
- 使用场景：用户询问"xxx的个性化报价"时使用
```

**Step 4: Commit**

```bash
git add 05-SREmate/src/main/resources/prompts/sre-agent.md
git commit -m "feat(sremate): update sre-agent prompt for personal quote tool"
```

---

### Task 5: 将新测试加入集成测试脚本

**Files:**
- Modify: `05-SREmate/scripts/run-integration-tests.sh`

**Step 1: 在 `-Dtest=` 参数中追加 `PersonalQuoteToolIT`**

找到这一行：
```
  -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT" \
```

改为：
```
  -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT,PersonalQuoteToolIT" \
```

**Step 2: 运行完整集成测试，确认全部通过**

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

预期：**所有集成测试通过 ✓**

**Step 3: Commit**

```bash
git add 05-SREmate/scripts/run-integration-tests.sh
git commit -m "test(sremate): add PersonalQuoteToolIT to integration test suite"
```
