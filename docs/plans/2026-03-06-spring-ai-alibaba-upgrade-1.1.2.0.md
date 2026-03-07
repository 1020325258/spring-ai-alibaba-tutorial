# Spring AI Alibaba 升级至 1.1.2.0 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 05-SREmate 模块的 `spring-ai-alibaba` 从 `1.0.0.4` 升级至 `1.1.2.0`，`spring-ai` 从 `1.0.1` 升级至 `1.1.2`，`spring-boot` 从 `3.4.8` 升级至 `3.5.8`，并验证应用正常启动与编译。

**Architecture:** 升级仅涉及版本号变更，不涉及 Spring AI API 代码改动（已通过字节码验证 `ChatClient`、`MethodToolCallbackProvider`、`ToolCallbackProvider`、`@Tool`、`UserMessage`、`AssistantMessage`、`stream().content()` 等核心 API 在 1.1.2 中签名完全兼容）。版本变更在 05-SREmate 子模块 pom.xml 中用 `<properties>` 覆盖父 POM 属性，并在 `<dependencyManagement>` 中重新导入新版 Spring Boot BOM，以隔离对其他模块的影响。

**Tech Stack:** Spring Boot 3.5.8, Spring AI 1.1.2, Spring AI Alibaba 1.1.2.0, Java 21, Maven

---

## 背景与兼容性验证结论

通过字节码检查（`javap`）确认以下 API 在 `spring-ai 1.1.2` 中完全不变：

| 类/方法 | 1.0.1 签名 | 1.1.2 签名 | 结论 |
|---------|-----------|-----------|------|
| `ChatClient.Builder.defaultToolCallbacks(ToolCallbackProvider...)` | ✓ | ✓ | 兼容 |
| `MethodToolCallbackProvider.builder().toolObjects(Object...)` | ✓ | ✓ | 兼容 |
| `@Tool` (`org.springframework.ai.tool.annotation.Tool`) | ✓ | ✓ | 兼容 |
| `UserMessage(String)` | ✓ | ✓ | 兼容 |
| `AssistantMessage` | ✓ | ✓ | 兼容 |
| `ChatClient.stream().content()` → `Flux<String>` | ✓ | ✓ | 兼容 |
| `spring.ai.dashscope.api-key` / `chat.options.model` 配置键 | ✓ | ✓ | 兼容 |

**结论：代码层面零改动，仅需修改 pom.xml。**

---

## 影响范围说明

父 POM (`/pom.xml`) 管理所有模块的版本。直接修改父 POM 会影响：
- `01-spring-ai-alibaba-evaluation`
- `02-spring-ai-alibaba-rag`
- `03-spring-ai-alibaba-mcp`
- `04-AnalysisAgent`

**本计划选择在 05-SREmate 子模块 pom.xml 中用属性覆盖 + 重新导入 BOM 的方式隔离升级**，其他模块保持现有版本不变。

---

### Task 1: 在 05-SREmate/pom.xml 中覆盖版本属性

**Files:**
- Modify: `05-SREmate/pom.xml`

**Step 1: 在 `<properties>` 节中添加版本覆盖**

打开 `05-SREmate/pom.xml`，在已有的 `<properties>` 节中添加三个版本覆盖属性：

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <!-- 覆盖父POM版本，将此模块升级至最新版本 -->
    <spring-ai-alibaba.version>1.1.2.0</spring-ai-alibaba.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-boot.version>3.5.8</spring-boot.version>
</properties>
```

**Step 2: 在 `<dependencyManagement>` 中重新导入 Spring Boot BOM**

由于 Maven 父 POM 的 `<dependencyManagement>` BOM 导入在父层已固化版本，子模块需要重新声明才能覆盖。在 `05-SREmate/pom.xml` 的 `<build>` 标签之前添加：

```xml
<dependencyManagement>
    <dependencies>
        <!-- 覆盖父POM中的Spring Boot BOM，升级至3.5.8 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Step 3: 提交**

```bash
git add 05-SREmate/pom.xml
git commit -m "build: upgrade spring-ai-alibaba to 1.1.2.0, spring-ai to 1.1.2, spring-boot to 3.5.8 in 05-SREmate"
```

---

### Task 2: 验证编译通过

**Files:**
- No code changes expected

**Step 1: 执行编译（仅 05-SREmate 模块）**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
mvn compile -pl 05-SREmate -am
```

Expected: `BUILD SUCCESS`，无编译错误。

**Step 2: 如编译失败，根据错误类型处理**

| 错误类型 | 处理方式 |
|---------|---------|
| `package ... does not exist` | 某个类已移包，更新 import |
| `cannot find symbol` | 某个方法已重命名，按 migration guide 替换 |
| `incompatible types` | 返回值类型变化，添加类型转换或调整赋值 |

---

### Task 3: 验证单元测试通过

**Step 1: 运行单元测试（跳过集成测试）**

```bash
mvn test -pl 05-SREmate -am -Dtest="SkillServiceUnitTest,EndpointTemplateServiceTest,HttpQueryToolTest" -DfailIfNoTests=false
```

Expected: `Tests run: N, Failures: 0, Errors: 0`

**Step 2: 若测试失败，检查失败原因**

- 若是 `ClassNotFoundException` / `NoSuchMethodError`：说明某个 API 在运行时不兼容，需调查具体类
- 若是业务逻辑断言失败：升级不相关，检查测试数据

---

### Task 4: 验证应用启动

**Step 1: 打包应用**

```bash
mvn package -pl 05-SREmate -am -DskipTests
```

Expected: `BUILD SUCCESS`，生成 `target/05-SREmate-*.jar`

**Step 2: 检查依赖树中的版本**

```bash
mvn dependency:tree -pl 05-SREmate -am | grep -E "spring-ai|spring-boot-autoconfigure|dashscope"
```

Expected output 中包含：
```
com.alibaba.cloud.ai:spring-ai-alibaba-starter-dashscope:jar:1.1.2.0
org.springframework.ai:spring-ai-model:jar:1.1.2
org.springframework.boot:spring-boot-autoconfigure:jar:3.5.8
```

**Step 3: 提交最终状态**

```bash
git add .
git commit -m "build: verify 05-SREmate successful upgrade to spring-ai-alibaba 1.1.2.0"
```

---

## 风险与回滚

**回滚方式：** 将 `05-SREmate/pom.xml` 中新增的 `<properties>` 覆盖和 `<dependencyManagement>` 节删除即可恢复到父 POM 的 1.0.x 版本。

**已知非致命警告（升级后仍会存在，不影响运行）：**
- `DEBUG: Cannot load class ClientAuthorizationException` — Spring AI 探测 Spring Security OAuth2，未引入时优雅跳过
- `ERROR: Unable to load netty-resolver-dns-native-macos` — macOS x86_64 的 netty 原生 DNS，回退到系统 DNS，功能不受影响
