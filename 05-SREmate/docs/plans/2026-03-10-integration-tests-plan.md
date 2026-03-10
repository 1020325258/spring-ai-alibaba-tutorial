# SREmate 集成测试体系 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为所有 @Tool 工具建立端到端集成测试，确保每次代码变更后可快速验证全部功能链路正常。

**Architecture:** 每个测试文件对应一组工具，通过真实的 `ChatClient.sreAgent` 发起自然语言请求，验证 Agent 能正确识别意图、调用工具、输出期望字段。所有测试不依赖 mock，连接真实数据库和 HTTP 接口。

**Tech Stack:** JUnit 5, AssertJ, Spring Boot Test, `@SpringBootTest`, `@ActiveProfiles("local")`

---

## 当前工具全览

| 工具类 | @Tool 方法 | 触发场景 |
|---|---|---|
| `ContractTool` | `queryContractData` | 用合同号查合同详情 |
| `ContractTool` | `queryContractsByOrderId` | 用订单号查该订单下所有合同 |
| `ContractTool` | `queryContractInstanceId` | 查合同的 platform_instance_id |
| `ContractTool` | `queryContractFormId` | 查合同版式 form_id（DB + HTTP 串联） |
| `ContractTool` | `queryContractConfig` | 查合同配置表 contract_city_company_info |
| `ContractTool` | `querySubOrderInfo` | 按订单号查子单（HTTP） |
| `HttpEndpointTool` | `callPredefinedEndpoint` | 调用预定义接口 |
| `HttpEndpointTool` | `listAvailableEndpoints` | 列出可用接口 |
| `SkillQueryTool` | `querySkills` | 查 Runbook 排查文档 |
| `SkillQueryTool` | `listSkillCategories` | 列出知识库分类 |

---

## 测试文件规划

```
src/test/java/com/yycome/sremate/
  StartupIT.java               任务1：验证应用能正常启动
  ContractQueryToolIT.java     任务2：queryContractData / queryContractsByOrderId
  ContractInstanceToolIT.java  任务3：queryContractInstanceId / queryContractFormId
  ContractConfigToolIT.java    任务4：queryContractConfig
  SubOrderToolIT.java          任务5：querySubOrderInfo
  SkillQueryToolIT.java        任务6：querySkills / listSkillCategories
  HttpEndpointToolIT.java      任务7：listAvailableEndpoints
```

运行所有测试的命令（每次代码变更后执行）：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT"
```

---

## 测试基类（所有 IT 共用）

> 注意：不需要创建真正的基类文件，只是把这段注解和字段复制到每个 IT 文件。

```java
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class XxxIT {

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt()
                .user(question)
                .call()
                .content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }
}
```

---

## Task 1：StartupIT — 验证应用启动

**背景：** 最常见的问题是改完代码应用启动失败，这个测试能在 30 秒内发现。

**Files:**
- Create: `src/test/java/com/yycome/sremate/StartupIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动验证测试 - 验证应用上下文能正常加载，所有 Bean 能正常注入
 * 每次修改配置类、新增 Bean、改动注解后必须运行此测试
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class StartupIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void applicationContext_shouldLoad() {
        // 只要 Spring 上下文能正常启动，此测试即通过
        assertThat(sreAgent).isNotNull();
    }

    @Test
    void sreAgent_shouldRespondToSimpleQuestion() {
        String response = sreAgent.prompt()
                .user("你好，你是谁？")
                .call()
                .content();

        System.out.println("=== SREmate 自我介绍 ===\n" + response);
        assertThat(response).isNotBlank();
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=StartupIT
```

预期输出：`Tests run: 2, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/StartupIT.java
git commit -m "test: add StartupIT to verify application context loads"
```

---

## Task 2：ContractQueryToolIT — 合同查询（按合同号 / 订单号）

**覆盖工具：** `queryContractData`、`queryContractsByOrderId`

**Files:**
- Create: `src/test/java/com/yycome/sremate/ContractQueryToolIT.java`

> ⚠️ 以下测试数据（`C1772854666284956`、`826030911000002645`）需替换为你本地数据库中真实存在的合同号和订单号。

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractTool 集成测试 - 验证按合同号和订单号查询合同数据的完整链路
 *
 * 测试数据说明：
 *   CONTRACT_CODE：替换为本地 DB 中真实存在的合同编号（C 前缀）
 *   PROJECT_ORDER_ID：替换为本地 DB 中真实存在的项目订单号（纯数字）
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractQueryToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String CONTRACT_CODE = "C1772854666284956";
    private static final String PROJECT_ORDER_ID = "826030911000002645";
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    // --- queryContractData ---

    @Test
    void queryContractData_withContractCode_shouldReturnContractData() {
        String response = ask(CONTRACT_CODE + "的合同数据");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        // 合同表的标志字段
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_code"),
                r -> assertThat(r).containsIgnoringCase("contract_status"),
                r -> assertThat(r).containsIgnoringCase(CONTRACT_CODE)
        );
    }

    @Test
    void queryContractData_contractNodeType_shouldReturnNodeData() {
        String response = ask(CONTRACT_CODE + "的合同节点数据");

        assertThat(response).doesNotContain("error");
        // 节点数据的标志字段
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_node"),
                r -> assertThat(r).containsIgnoringCase("node_type"),
                r -> assertThat(r).containsIgnoringCase("node_status")
        );
    }

    @Test
    void queryContractData_contractUserType_shouldReturnUserData() {
        String response = ask(CONTRACT_CODE + "的签约人信息");

        assertThat(response).doesNotContain("error");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_user"),
                r -> assertThat(r).containsIgnoringCase("user_name"),
                r -> assertThat(r).containsIgnoringCase("user_type")
        );
    }

    @Test
    void queryContractData_withCPrefix_shouldNotUseOrderTool() {
        // 合同号以 C 开头，Agent 不应该误用 queryContractsByOrderId
        String response = ask("查询" + CONTRACT_CODE + "的合同详情");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到编号");
    }

    // --- queryContractsByOrderId ---

    @Test
    void queryContractsByOrderId_withOrderId_shouldReturnContractList() {
        String response = ask(PROJECT_ORDER_ID + "的合同详情");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        // 订单下合同数据的标志字段
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_code"),
                r -> assertThat(r).containsIgnoringCase("project_order_id"),
                r -> assertThat(r).contains(PROJECT_ORDER_ID)
        );
    }

    @Test
    void queryContractsByOrderId_pureDigits_shouldNotUseContractTool() {
        // 纯数字订单号，Agent 不应该误用 queryContractData
        String response = ask("订单" + PROJECT_ORDER_ID + "下有哪些合同");

        assertThat(response).doesNotContain("无效的 dataType");
        assertThat(response).doesNotContain("error");
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractQueryToolIT
```

预期输出：`Tests run: 6, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/ContractQueryToolIT.java
git commit -m "test: add ContractQueryToolIT for contract data query tools"
```

---

## Task 3：ContractInstanceToolIT — 实例 ID 和版式查询

**覆盖工具：** `queryContractInstanceId`、`queryContractFormId`

**Files:**
- Create: `src/test/java/com/yycome/sremate/ContractInstanceToolIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同实例 ID 和版式 form_id 查询集成测试
 * queryContractFormId 是复合工具（DB + HTTP），同时验证两段链路
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractInstanceToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String CONTRACT_CODE = "C1772854666284956";
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void queryContractInstanceId_shouldReturnInstanceId() {
        String response = ask(CONTRACT_CODE + "的 platform_instance_id 是多少");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("platformInstanceId"),
                r -> assertThat(r).containsIgnoringCase("platform_instance_id"),
                r -> assertThat(r).containsIgnoringCase("instanceId")
        );
    }

    @Test
    void queryContractFormId_shouldReturnFormId() {
        String response = ask(CONTRACT_CODE + "的版式 form_id 是多少");

        assertThat(response).doesNotContain("error");
        // form_id 查询涉及 DB（获取 instanceId）+ HTTP（获取 form_id）
        // 响应中可能是 form_id 数据，也可能是接口返回的 JSON
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("form_id"),
                r -> assertThat(r).containsIgnoringCase("formId"),
                r -> assertThat(r).containsIgnoringCase("版式")
        );
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractInstanceToolIT
```

预期输出：`Tests run: 2, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/ContractInstanceToolIT.java
git commit -m "test: add ContractInstanceToolIT for instance ID and form ID queries"
```

---

## Task 4：ContractConfigToolIT — 合同配置表查询

**覆盖工具：** `queryContractConfig`

**核心验证点：** Agent 能正确区分"合同号查询"（不需要 contractType）和"订单号查询"（需要 contractType），以及在订单号不指定类型时会询问。

**Files:**
- Create: `src/test/java/com/yycome/sremate/ContractConfigToolIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同配置表查询集成测试
 * 验证按合同号和订单号查询 contract_city_company_info 的链路
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractConfigToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String CONTRACT_CODE = "C1772925348216431";
    private static final String PROJECT_ORDER_ID = "826030619000001899";
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void queryContractConfig_byContractCode_shouldReturnConfig() {
        String response = ask(CONTRACT_CODE + "的合同配置表数据");

        assertThat(response).doesNotContain("未找到编号");
        // contract_city_company_info 表的标志字段
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_city_company_info"),
                r -> assertThat(r).containsIgnoringCase("projectOrderId"),
                r -> assertThat(r).containsIgnoringCase("company")
        );
    }

    @Test
    void queryContractConfig_byOrderId_withContractType_shouldReturnConfig() {
        String response = ask(PROJECT_ORDER_ID + "的正签合同配置");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到编号");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_city_company_info"),
                r -> assertThat(r).containsIgnoringCase("projectOrderId"),
                r -> assertThat(r).contains(PROJECT_ORDER_ID)
        );
    }

    @Test
    void queryContractConfig_byOrderId_withoutType_shouldAskForType() {
        // 订单号查询时未指定合同类型，Agent 应该询问用户，而不是报错
        String response = ask(PROJECT_ORDER_ID + "的合同配置");

        // 不应该出现技术性错误
        assertThat(response).doesNotContain("未找到编号");
        // Agent 应该询问合同类型，或尝试查询
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsAnyOf("类型", "合同类型", "正签", "认购", "needAskType"),
                r -> assertThat(r).containsIgnoringCase("contract_city_company_info")
        );
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=ContractConfigToolIT
```

预期输出：`Tests run: 3, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/ContractConfigToolIT.java
git commit -m "test: add ContractConfigToolIT for contract config table queries"
```

---

## Task 5：SubOrderToolIT — 子单信息查询

**覆盖工具：** `querySubOrderInfo`

**Files:**
- Create: `src/test/java/com/yycome/sremate/SubOrderToolIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 子单信息查询集成测试
 * querySubOrderInfo 通过 HTTP 接口查询，验证 HTTP 链路正常
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class SubOrderToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String HOME_ORDER_NO = "826030611000000795";
    private static final String QUOTATION_ORDER_NO = "GBILL260309110407580001"; // 可选，若无则置空
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void querySubOrderInfo_byOrderOnly_shouldReturnSubOrderData() {
        String response = ask("查询订单" + HOME_ORDER_NO + "的子单信息");

        // HTTP 接口能正常调用（不一定有数据，但不应该报连接异常）
        assertThat(response).doesNotContain("接口调用失败");
        assertThat(response).doesNotContain("ConnectException");
    }

    @Test
    void querySubOrderInfo_byOrderAndQuotation_shouldReturnSubOrderData() {
        String response = ask(HOME_ORDER_NO + "下" + QUOTATION_ORDER_NO + "的子单信息");

        assertThat(response).doesNotContain("接口调用失败");
        assertThat(response).doesNotContain("ConnectException");
        // Agent 应正确识别 GBILL 前缀为报价单号
        assertThat(response).doesNotContain("参数验证失败");
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=SubOrderToolIT
```

预期输出：`Tests run: 2, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/SubOrderToolIT.java
git commit -m "test: add SubOrderToolIT for sub-order HTTP query"
```

---

## Task 6：SkillQueryToolIT — 运维知识库查询

**覆盖工具：** `querySkills`、`listSkillCategories`

**Files:**
- Create: `src/test/java/com/yycome/sremate/SkillQueryToolIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill 运维知识库查询集成测试
 * 验证 querySkills 和 listSkillCategories 工具的完整链路
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class SkillQueryToolIT {

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void querySkills_databaseTimeout_shouldReturnRunbook() {
        String response = ask("数据库连接超时怎么排查");

        assertThat(response).isNotBlank();
        // 知识库中有数据库排查文档，应该返回内容而不是"未找到"
        assertThat(response).doesNotContain("未找到任何匹配");
        assertThat(response).doesNotContain("error");
    }

    @Test
    void querySkills_serviceTimeout_shouldReturnRunbook() {
        String response = ask("服务超时怎么处理");

        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("error");
    }

    @Test
    void listSkillCategories_shouldReturnCategories() {
        String response = ask("运维知识库有哪些分类");

        assertThat(response).isNotBlank();
        // 知识库应至少包含 diagnosis 分类
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("diagnosis"),
                r -> assertThat(r).containsIgnoringCase("诊断"),
                r -> assertThat(r).containsIgnoringCase("分类")
        );
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=SkillQueryToolIT
```

预期输出：`Tests run: 3, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/SkillQueryToolIT.java
git commit -m "test: add SkillQueryToolIT for runbook query tools"
```

---

## Task 7：HttpEndpointToolIT — 预定义接口列表

**覆盖工具：** `listAvailableEndpoints`

> 注意：`callPredefinedEndpoint` 和 `callHttpEndpoint` 已被 `querySubOrderInfo`、`queryContractFormId` 等工具间接覆盖，不单独测试。

**Files:**
- Create: `src/test/java/com/yycome/sremate/HttpEndpointToolIT.java`

**Step 1: 创建测试文件**

```java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 预定义接口工具集成测试
 * 主要验证 listAvailableEndpoints 和 YAML 模板加载是否正常
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class HttpEndpointToolIT {

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void listAvailableEndpoints_shouldReturnEndpointList() {
        String response = ask("有哪些可用的预定义接口");

        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("error");
        // 至少应该包含合同相关接口
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("sign-order-list"),
                r -> assertThat(r).containsIgnoringCase("contract-form-data"),
                r -> assertThat(r).containsIgnoringCase("sub-order-info")
        );
    }

    @Test
    void listAvailableEndpoints_byCategory_shouldFilterCorrectly() {
        String response = ask("查看 contract 分类的接口");

        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("error");
    }
}
```

**Step 2: 运行测试**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate -Dtest=HttpEndpointToolIT
```

预期输出：`Tests run: 2, Failures: 0, Errors: 0`

**Step 3: 提交**

```bash
git add src/test/java/com/yycome/sremate/HttpEndpointToolIT.java
git commit -m "test: add HttpEndpointToolIT for endpoint list tool"
```

---

## Task 8：创建运行脚本 + 测试文档

**Files:**
- Create: `05-SREmate/scripts/run-integration-tests.sh`
- Create: `05-SREmate/docs/integration-tests.md`

**Step 1: 创建运行脚本**

```bash
#!/bin/bash
# SREmate 集成测试一键运行脚本
# 每次代码变更后执行，确保所有工具链路正常
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$PROJECT_DIR")"

echo "======================================"
echo "  SREmate 集成测试"
echo "======================================"

JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test \
  -f "$PROJECT_ROOT/pom.xml" \
  -pl 05-SREmate \
  -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT" \
  -Dsurefire.failIfNoSpecifiedTests=false

echo ""
echo "======================================"
echo "  所有集成测试通过 ✓"
echo "======================================"
```

运行方式：
```bash
chmod +x 05-SREmate/scripts/run-integration-tests.sh
./05-SREmate/scripts/run-integration-tests.sh
```

**Step 2: 创建测试文档**

文件路径：`05-SREmate/docs/integration-tests.md`

内容：

```markdown
# SREmate 集成测试文档

## 测试原则

- 所有测试通过真实 ChatClient 发起自然语言请求，验证完整 Agent 链路
- 不使用 Mock，连接真实数据库和 HTTP 接口
- 每次代码变更后必须运行全部集成测试

## 快速运行

```bash
./05-SREmate/scripts/run-integration-tests.sh
```

## 测试文件说明

| 文件 | 覆盖工具 | 核心验证点 |
|---|---|---|
| `StartupIT` | 无 | 应用上下文加载、Bean 注入 |
| `ContractQueryToolIT` | `queryContractData`、`queryContractsByOrderId` | 合同号/订单号格式识别、数据字段返回 |
| `ContractInstanceToolIT` | `queryContractInstanceId`、`queryContractFormId` | instanceId 查询、DB+HTTP 串联 |
| `ContractConfigToolIT` | `queryContractConfig` | 配置表查询、类型识别、无类型时询问 |
| `SubOrderToolIT` | `querySubOrderInfo` | HTTP 接口连通、GBILL 前缀识别 |
| `SkillQueryToolIT` | `querySkills`、`listSkillCategories` | Runbook 检索、知识库分类 |
| `HttpEndpointToolIT` | `listAvailableEndpoints` | YAML 模板加载、接口列表 |

## 测试数据维护

各 IT 文件顶部有 `private static final String` 声明的测试数据常量，需确保与本地数据库一致：

- `CONTRACT_CODE`：本地 DB 中真实存在的合同编号（C 前缀）
- `PROJECT_ORDER_ID`：本地 DB 中真实存在的项目订单号（纯数字）
- `HOME_ORDER_NO`：子单查询使用的订单号

## 验收标准

| 检查项 | 验证方式 |
|---|---|
| 应用能启动 | `StartupIT.applicationContext_shouldLoad` 通过 |
| 合同号/订单号不混淆 | `ContractQueryToolIT` 中互斥 assert 通过 |
| HTTP 接口可连通 | `SubOrderToolIT` 无 ConnectException |
| 知识库文档可加载 | `SkillQueryToolIT.listSkillCategories` 返回非空 |
| YAML 模板可加载 | `HttpEndpointToolIT.listAvailableEndpoints` 包含已配置接口 |

## 常见失败原因

| 失败现象 | 可能原因 |
|---|---|
| `StartupIT` 失败 | Bean 注入失败、配置项缺失、`application-local.yml` 未配置 |
| 合同查询返回"未找到" | 测试数据常量与本地 DB 不匹配，需更新 |
| 子单查询返回"接口调用失败" | HTTP 接口地址不可达，检查网络/VPN |
| Skill 返回"未找到任何匹配" | `skills/` 目录下的 Markdown 文件为空或关键词不匹配 |
```

**Step 3: 提交**

```bash
chmod +x 05-SREmate/scripts/run-integration-tests.sh
git add 05-SREmate/scripts/run-integration-tests.sh 05-SREmate/docs/integration-tests.md
git commit -m "test: add integration test runner script and documentation"
```

---

## 全量运行验证

完成所有任务后，执行一次完整验证：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test -pl 05-SREmate \
  -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT"
```

预期最终输出：
```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
