# SkillQueryTool 升级实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Spring AI Alibaba 升级到 1.1.2.0 版本，使用官方 Skill 功能替换自实现的 SkillQueryTool，完成数据迁移和代码重构。

**Architecture:** 升级框架版本后，调研官方 Skill API，将现有 Markdown 文档迁移到官方格式，重构 SkillQueryTool 和 SkillService 使用官方 API，通过 TDD 方式确保功能正确性。

**Tech Stack:** Spring Boot 3.x, Spring AI Alibaba 1.1.2.0, Java 21, Maven, JUnit 5

---

## Task 1: 调研 Spring AI Alibaba 1.1.2.0 官方 Skill 功能

**Files:**
- Create: `docs/research/spring-ai-alibaba-skill-research.md`

**Step 1: 访问官方文档**

访问 Spring AI Alibaba GitHub 仓库和官方文档，了解 1.1.2.0 版本的 Skill 功能。

**Step 2: 记录 Skill API 接口**

在调研文档中记录：
- Skill 文档的格式要求
- Skill 的 API 接口（加载、查询、管理等）
- Skill 的配置方式
- Skill 的依赖项

**Step 3: 记录与现有实现的差异**

对比现有 SkillQueryTool 实现与官方 Skill 的差异：
- 文档格式差异
- API 调用方式差异
- 功能差异

**Step 4: 提交调研文档**

```bash
git add docs/research/spring-ai-alibaba-skill-research.md
git commit -m "docs: add Spring AI Alibaba 1.1.2.0 Skill research"
```

---

## Task 2: 升级 Spring AI Alibaba 版本到 1.1.2.0

**Files:**
- Modify: `pom.xml`

**Step 1: 查看当前版本**

```bash
grep -A 5 "spring-ai-alibaba" pom.xml
```

记录当前的 Spring AI Alibaba 版本号。

**Step 2: 更新 pom.xml 中的版本**

在 `pom.xml` 中更新 Spring AI Alibaba 版本：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>1.1.2.0</version>
</dependency>
```

**Step 3: 检查依赖冲突**

```bash
mvn dependency:tree > dependency-tree.txt
```

查看输出，确认无版本冲突。

**Step 4: 编译项目**

```bash
mvn clean compile
```

Expected: BUILD SUCCESS

如果有编译错误，记录错误信息并解决。

**Step 5: 运行现有测试**

```bash
mvn test
```

Expected: All tests pass

如果有测试失败，记录失败原因。

**Step 6: 提交版本升级**

```bash
git add pom.xml
git commit -m "feat: upgrade Spring AI Alibaba to 1.1.2.0"
```

---

## Task 3: 分析现有 Skill 文档结构

**Files:**
- Create: `docs/analysis/existing-skill-documents-analysis.md`

**Step 1: 列出现有 Skill 文档**

```bash
find src/main/resources/skills -name "*.md" -type f
```

**Step 2: 分析文档结构**

读取每个文档，分析其结构：
- 文档标题
- 章节划分
- 内容格式

**Step 3: 记录文档元数据**

记录每个文档的关键信息：
- 文档路径
- 文档分类（diagnosis/operations/knowledge）
- 文档内容概要

**Step 4: 提交分析文档**

```bash
git add docs/analysis/existing-skill-documents-analysis.md
git commit -m "docs: add existing Skill documents analysis"
```

---

## Task 4: 确定官方 Skill 文档格式要求

**Files:**
- Create: `docs/specs/official-skill-format-spec.md`

**Step 1: 阅读官方 Skill 文档格式要求**

根据 Task 1 的调研结果，明确官方 Skill 的文档格式要求。

**Step 2: 定义文档格式规范**

创建格式规范文档，包括：
- 文件命名规范
- 文件路径规范
- 文档头部元数据格式
- 文档内容格式

**Step 3: 提供示例**

提供官方 Skill 文档的示例：

```markdown
---
name: database-connection-timeout
description: 数据库连接超时问题排查
category: diagnosis
tags: [database, connection, timeout]
version: 1.0
---

# 数据库连接超时问题排查

## 问题特征
...

## 排查步骤
...

## 解决方案
...
```

**Step 4: 提交格式规范**

```bash
git add docs/specs/official-skill-format-spec.md
git commit -m "docs: add official Skill format specification"
```

---

## Task 5: 创建数据迁移脚本

**Files:**
- Create: `scripts/migrate-skills.sh`

**Step 1: 编写迁移脚本**

创建 Shell 脚本将现有 Markdown 文档转换为官方格式：

```bash
#!/bin/bash

# 源目录和目标目录
SOURCE_DIR="src/main/resources/skills"
TARGET_DIR="src/main/resources/official-skills"

# 创建目标目录
mkdir -p "$TARGET_DIR"

# 迁移文档
for file in $(find "$SOURCE_DIR" -name "*.md" -type f); do
    # 提取文件名和分类
    filename=$(basename "$file" .md)
    category=$(basename $(dirname "$file"))

    # 生成目标文件路径
    target_file="$TARGET_DIR/${category}-${filename}.md"

    # 添加元数据头部
    echo "---" > "$target_file"
    echo "name: ${category}-${filename}" >> "$target_file"
    echo "description: $(head -n 1 "$file" | sed 's/^# //')" >> "$target_file"
    echo "category: ${category}" >> "$target_file"
    echo "version: 1.0" >> "$target_file"
    echo "---" >> "$target_file"
    echo "" >> "$target_file"

    # 添加原始内容
    cat "$file" >> "$target_file"

    echo "Migrated: $file -> $target_file"
done

echo "Migration completed!"
```

**Step 2: 测试迁移脚本**

```bash
chmod +x scripts/migrate-skills.sh
./scripts/migrate-skills.sh
```

Expected: 脚本成功运行，生成官方格式的文档

**Step 3: 验证迁移结果**

检查生成的文档：
- 文档数量是否一致
- 文档格式是否正确
- 文档内容是否完整

**Step 4: 提交迁移脚本**

```bash
git add scripts/migrate-skills.sh
git commit -m "feat: add Skill document migration script"
```

---

## Task 6: 执行数据迁移

**Files:**
- Create: `src/main/resources/official-skills/` (目录)
- Create: `docs/logs/migration-log.md`

**Step 1: 备份现有文档**

```bash
cp -r src/main/resources/skills src/main/resources/skills-backup
```

**Step 2: 执行迁移脚本**

```bash
./scripts/migrate-skills.sh
```

**Step 3: 记录迁移日志**

创建迁移日志文档，记录：
- 迁移时间
- 迁移的文档数量
- 迁移过程中的问题
- 迁移结果验证

**Step 4: 验证迁移结果**

```bash
# 检查文档数量
echo "Original documents: $(find src/main/resources/skills -name '*.md' | wc -l)"
echo "Migrated documents: $(find src/main/resources/official-skills -name '*.md' | wc -l)"
```

Expected: 数量一致

**Step 5: 提交迁移结果**

```bash
git add src/main/resources/official-skills/
git add docs/logs/migration-log.md
git commit -m "feat: migrate Skill documents to official format"
```

---

## Task 7: 创建 SkillService 测试

**Files:**
- Create: `src/test/java/com/yycome/sremate/service/SkillServiceTest.java`

**Step 1: 编写 SkillService 测试**

```java
package com.yycome.sremate.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkillServiceTest {

    @Autowired
    private SkillService skillService;

    @Test
    void shouldQuerySkillByCategory() {
        String result = skillService.querySkills("diagnosis", null);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    void shouldQuerySkillByKeywords() {
        String result = skillService.querySkills("diagnosis", "数据库 连接超时");
        assertNotNull(result);
        assertTrue(result.contains("数据库") || result.contains("连接"));
    }

    @Test
    void shouldListSkillCategories() {
        var categories = skillService.listSkillCategories();
        assertNotNull(categories);
        assertTrue(categories.size() > 0);
        assertTrue(categories.contains("diagnosis"));
    }
}
```

**Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=SkillServiceTest
```

Expected: Tests pass (因为 SkillService 还未重构，使用现有实现)

**Step 3: 提交测试**

```bash
git add src/test/java/com/yycome/sremate/service/SkillServiceTest.java
git commit -m "test: add SkillService tests"
```

---

## Task 8: 重构 SkillService 使用官方 Skill API

**Files:**
- Modify: `src/main/java/com/yycome/sremate/service/SkillService.java`

**Step 1: 读取现有 SkillService 代码**

```bash
cat src/main/java/com/yycome/sremate/service/SkillService.java
```

**Step 2: 编写使用官方 Skill API 的新实现**

根据 Task 1 的调研结果，重构 SkillService：

```java
package com.yycome.sremate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillService {

    @Autowired(required = false)
    private VectorStore vectorStore;

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 查询 Skills 文档
     */
    public String querySkills(String queryType, String keywords) {
        log.info("查询 Skills - 类型: {}, 关键词: {}", queryType, keywords);

        try {
            // 加载官方格式的 Skill 文档
            List<Document> documents = loadSkillDocuments(queryType);

            // 如果有关键词，进行过滤
            if (keywords != null && !keywords.isEmpty()) {
                String[] keywordArray = keywords.split("\\s+");
                documents = documents.stream()
                    .filter(doc -> containsAnyKeyword(doc.getContent(), keywordArray))
                    .collect(Collectors.toList());
            }

            // 格式化输出
            return formatDocuments(documents);

        } catch (Exception e) {
            log.error("查询 Skills 失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 列出所有 Skills 分类
     */
    public List<String> listSkillCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("diagnosis");
        categories.add("operations");
        categories.add("knowledge");
        return categories;
    }

    /**
     * 加载 Skill 文档
     */
    private List<Document> loadSkillDocuments(String queryType) throws IOException {
        List<Document> documents = new ArrayList<>();

        String locationPattern = "classpath:official-skills/";
        if (queryType != null && !queryType.isEmpty()) {
            locationPattern += queryType + "-*.md";
        } else {
            locationPattern += "*.md";
        }

        Resource[] resources = resolver.getResources(locationPattern);

        for (Resource resource : resources) {
            TextReader reader = new TextReader(resource);
            List<Document> docs = reader.get();
            documents.addAll(docs);
        }

        return documents;
    }

    /**
     * 检查内容是否包含关键词
     */
    private boolean containsAnyKeyword(String content, String[] keywords) {
        String lowerContent = content.toLowerCase();
        for (String keyword : keywords) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 格式化文档输出
     */
    private String formatDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return "未找到相关的 Skills 文档";
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("找到 %d 个相关的 Skills 文档:\n\n", documents.size()));

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            result.append(String.format("### 文档 %d\n", i + 1));
            result.append(doc.getContent());
            result.append("\n\n");
        }

        return result.toString();
    }
}
```

注意：此代码是基于假设的官方 API，实际实现需要根据 Task 1 的调研结果调整。

**Step 3: 运行测试验证通过**

```bash
mvn test -Dtest=SkillServiceTest
```

Expected: Tests pass

**Step 4: 提交重构**

```bash
git add src/main/java/com/yycome/sremate/service/SkillService.java
git commit -m "refactor: use official Skill API in SkillService"
```

---

## Task 9: 创建 SkillQueryTool 测试

**Files:**
- Create: `src/test/java/com/yycome/sremate/tools/SkillQueryToolTest.java`

**Step 1: 编写 SkillQueryTool 测试**

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.service.SkillService;
import com.yycome.sremate.tools.core.SRETool;
import com.yycome.sremate.tools.core.ToolMetadata;
import com.yycome.sremate.tools.core.ToolRequest;
import com.yycome.sremate.tools.core.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class SkillQueryToolTest {

    @Autowired
    private SkillQueryTool skillQueryTool;

    @MockBean
    private SkillService skillService;

    @Test
    void shouldImplementSREToolInterface() {
        assertTrue(skillQueryTool instanceof SRETool);
    }

    @Test
    void shouldHaveValidMetadata() {
        ToolMetadata metadata = skillQueryTool.getMetadata();
        assertNotNull(metadata);
        assertEquals("querySkills", metadata.getName());
        assertNotNull(metadata.getDescription());
        assertNotNull(metadata.getParameters());
        assertEquals("knowledge", metadata.getCategory());
    }

    @Test
    void shouldExecuteQuerySuccessfully() {
        // Mock
        when(skillService.querySkills("diagnosis", "数据库"))
            .thenReturn("相关的 Skills 文档内容");

        // Execute
        ToolRequest request = new ToolRequest(Map.of(
            "queryType", "diagnosis",
            "keywords", "数据库"
        ));
        ToolResult result = skillQueryTool.execute(request);

        // Verify
        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());
        assertTrue(result.getDuration() > 0);

        verify(skillService, times(1)).querySkills("diagnosis", "数据库");
    }

    @Test
    void shouldHandleQueryFailure() {
        // Mock
        when(skillService.querySkills(anyString(), anyString()))
            .thenThrow(new RuntimeException("查询失败"));

        // Execute
        ToolRequest request = new ToolRequest(Map.of(
            "queryType", "diagnosis",
            "keywords", "测试"
        ));
        ToolResult result = skillQueryTool.execute(request);

        // Verify
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }
}
```

**Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=SkillQueryToolTest
```

Expected: Tests pass (因为 SkillQueryTool 还未重构，使用现有实现)

**Step 3: 提交测试**

```bash
git add src/test/java/com/yycome/sremate/tools/SkillQueryToolTest.java
git commit -m "test: add SkillQueryTool tests"
```

---

## Task 10: 重构 SkillQueryTool 使用官方 Skill API

**Files:**
- Modify: `src/main/java/com/yycome/sremate/tools/SkillQueryTool.java`

**Step 1: 读取现有 SkillQueryTool 代码**

```bash
cat src/main/java/com/yycome/sremate/tools/SkillQueryTool.java
```

**Step 2: 重构 SkillQueryTool**

更新 SkillQueryTool 使用重构后的 SkillService：

```java
package com.yycome.sremate.tools;

import com.yycome.sremate.service.SkillService;
import com.yycome.sremate.tools.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillQueryTool implements SRETool {

    private final SkillService skillService;

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
            .name("querySkills")
            .description("查询SRE运维知识库，获取问题排查经验和解决方案")
            .parameters(Arrays.asList(
                new ParameterSpec("queryType", "查询类型（diagnosis/operations/knowledge）", false, "diagnosis"),
                new ParameterSpec("keywords", "关键词，用于匹配相关文档", false, null)
            ))
            .category("knowledge")
            .build();
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> params = request.getParams();
            String queryType = (String) params.getOrDefault("queryType", "diagnosis");
            String keywords = (String) params.get("keywords");

            log.info("调用 SkillQueryTool - 类型: {}, 关键词: {}", queryType, keywords);
            String result = skillService.querySkills(queryType, keywords);

            return ToolResult.success(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("SkillQueryTool 执行失败", e);
            return ToolResult.failure(e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // 保留原有方法以保持兼容性
    @Tool(description = "查询SRE运维知识库，获取问题排查经验和解决方案。" +
            "queryType可选值：diagnosis（问题诊断）、operations（运维咨询）、knowledge（通用知识）。" +
            "keywords用于匹配相关的文档，多个关键词用空格分隔。")
    public String querySkills(String queryType, String keywords) {
        log.info("调用 SkillQueryTool - 类型: {}, 关键词: {}", queryType, keywords);
        return skillService.querySkills(queryType, keywords);
    }

    @Tool(description = "列出SRE运维知识库的所有分类")
    public String listSkillCategories() {
        log.info("调用 listSkillCategories");
        return "可用的 Skills 分类：" + String.join(", ", skillService.listSkillCategories());
    }
}
```

**Step 3: 运行测试验证通过**

```bash
mvn test -Dtest=SkillQueryToolTest
```

Expected: Tests pass

**Step 4: 提交重构**

```bash
git add src/main/java/com/yycome/sremate/tools/SkillQueryTool.java
git commit -m "refactor: use official Skill API in SkillQueryTool"
```

---

## Task 11: 更新配置文件

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: 添加官方 Skill 配置**

根据官方 Skill 的要求，更新配置文件：

```yaml
spring:
  application:
    name: sremate

  # AI 配置
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7

  # Skill 配置（根据官方要求调整）
  ai:
    skill:
      enabled: true
      location: classpath:official-skills/
      # 其他官方 Skill 配置项

# 其他配置保持不变
```

注意：具体配置项需要根据 Task 1 的调研结果调整。

**Step 2: 验证配置**

```bash
mvn spring-boot:run
```

Expected: 应用启动成功，无配置错误

**Step 3: 提交配置**

```bash
git add src/main/resources/application.yml
git commit -m "config: update configuration for official Skill"
```

---

## Task 12: 运行完整测试套件

**Files:**
- 无文件修改

**Step 1: 运行所有测试**

```bash
mvn clean test
```

Expected: All tests pass

**Step 2: 检查测试覆盖率**

```bash
mvn jacoco:report
```

检查 `target/site/jacoco/index.html`，确保测试覆盖率 ≥ 80%。

**Step 3: 记录测试结果**

如果测试失败，记录失败原因并修复。

---

## Task 13: 进行集成测试

**Files:**
- Create: `src/test/java/com/yycome/sremate/integration/SkillIntegrationTest.java`

**Step 1: 编写集成测试**

```java
package com.yycome.sremate.integration;

import com.yycome.sremate.tools.SkillQueryTool;
import com.yycome.sremate.tools.core.ToolRequest;
import com.yycome.sremate.tools.core.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkillIntegrationTest {

    @Autowired
    private SkillQueryTool skillQueryTool;

    @Test
    void shouldQueryDiagnosisSkills() {
        ToolRequest request = new ToolRequest(Map.of(
            "queryType", "diagnosis",
            "keywords", "数据库"
        ));

        ToolResult result = skillQueryTool.execute(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());
        assertTrue(result.getResult().length() > 0);
    }

    @Test
    void shouldQueryAllSkillCategories() {
        String categories = skillQueryTool.listSkillCategories();

        assertNotNull(categories);
        assertTrue(categories.contains("diagnosis"));
        assertTrue(categories.contains("operations"));
        assertTrue(categories.contains("knowledge"));
    }
}
```

**Step 2: 运行集成测试**

```bash
mvn test -Dtest=SkillIntegrationTest
```

Expected: Tests pass

**Step 3: 提交集成测试**

```bash
git add src/test/java/com/yycome/sremate/integration/SkillIntegrationTest.java
git commit -m "test: add Skill integration tests"
```

---

## Task 14: 进行功能对比测试

**Files:**
- Create: `docs/tests/function-comparison-test-report.md`

**Step 1: 准备测试数据集**

准备一组测试查询：
- 查询 diagnosis 类型的文档
- 查询 operations 类型的文档
- 查询 knowledge 类型的文档
- 使用关键词查询

**Step 2: 执行对比测试**

对每个测试查询，记录：
- 迁移前的查询结果
- 迁移后的查询结果
- 结果一致性

**Step 3: 分析差异**

如果结果有差异，分析原因：
- 文档格式转换导致的内容变化
- 检索算法的差异
- 其他因素

**Step 4: 记录测试报告**

创建测试报告文档，记录：
- 测试数据集
- 测试结果
- 差异分析
- 结论

**Step 5: 提交测试报告**

```bash
git add docs/tests/function-comparison-test-report.md
git commit -m "test: add function comparison test report"
```

---

## Task 15: 更新项目文档

**Files:**
- Modify: `README.md`
- Create: `docs/migration/skill-migration-guide.md`

**Step 1: 更新 README.md**

更新 README.md 中的相关内容：
- 更新 Spring AI Alibaba 版本信息
- 更新 Skill 功能的说明
- 更新使用示例

**Step 2: 创建迁移指南**

创建详细的迁移指南文档，包括：
- 迁移背景和目标
- 迁移步骤
- 配置变更
- API 变更
- 注意事项

**Step 3: 提交文档**

```bash
git add README.md docs/migration/skill-migration-guide.md
git commit -m "docs: update project documentation for Skill migration"
```

---

## Task 16: 最终验收

**Files:**
- Create: `docs/acceptance/acceptance-report.md`

**Step 1: 检查所有验收标准**

根据需求规格说明书，逐项检查验收标准：

框架升级：
- [ ] pom.xml 中的 Spring AI Alibaba 版本为 1.1.2.0
- [ ] 项目能够正常编译
- [ ] 基本的 Agent 功能正常工作

数据迁移：
- [ ] 所有现有 Markdown 文档成功转换
- [ ] 转换后的文档格式正确
- [ ] 文档内容完整无丢失

代码重构：
- [ ] SkillQueryTool 使用官方 Skill API
- [ ] SkillService 使用官方 Skill API
- [ ] 所有单元测试通过
- [ ] 测试覆盖率 ≥ 80%

**Step 2: 进行手动测试**

启动应用，进行手动测试：
- 测试 SkillQueryTool 的查询功能
- 测试 Agent 调用 SkillQueryTool
- 测试各种查询场景

**Step 3: 记录验收报告**

创建验收报告文档，记录：
- 验收标准检查结果
- 手动测试结果
- 遗留问题（如有）
- 验收结论

**Step 4: 最终提交**

```bash
git add docs/acceptance/acceptance-report.md
git commit -m "docs: add final acceptance report"
```

**Step 5: 创建最终标签**

```bash
git tag -a v1.1.0 -m "Release v1.1.0: Upgrade to Spring AI Alibaba 1.1.2.0 with official Skill support"
git push origin v1.1.0
```

---

## 总结

本实施计划包含 16 个主要任务，涵盖：

1. **调研准备** - 调研官方 Skill 功能，分析现有文档
2. **框架升级** - 升级 Spring AI Alibaba 到 1.1.2.0
3. **数据迁移** - 转换文档格式，迁移数据
4. **代码重构** - 重构 SkillService 和 SkillQueryTool
5. **测试验证** - 单元测试、集成测试、功能对比测试
6. **文档更新** - 更新项目文档，提供迁移指南
7. **最终验收** - 检查验收标准，进行手动测试

每个任务都遵循 TDD 原则，包含详细的步骤、代码示例和验收标准。通过频繁提交确保代码质量。
