# SREmate 告警根因分析功能设计

- **日期**：2026-03-10
- **作者**：与 Claude 协作设计
- **状态**：已批准，待实现

---

## 背景

SREmate 当前已具备合同数据查询、HTTP 端点调用、Runbook 检索等能力。本次增强目标是让 SREmate 能够接收 ELK 告警数据（Java 异常堆栈 / 错误日志），自动关联业务数据和本地源码，生成根因假设与排查建议，提升研发值班效率。

**本期范围**：被动查询模式（CLI 输入触发分析）。主动监听（watch 模式）留到后续迭代。

---

## 设计目标

1. 接收 ES 告警 ID 或原始日志内容，输出结构化的根因分析结果
2. 自动从堆栈中提取业务 ID，关联合同/订单数据
3. 自动定位本地源码出错位置，提供代码上下文
4. 代码职责清晰，严格遵循 DDD 分层，不破坏现有架构
5. 通过抽象 `AlertRepository` 接口屏蔽 ES 实现细节，支持 Mock 模式本地测试

---

## 架构分层

遵循项目现有 DDD 分层约定，新增内容按层放置：

```
domain/alert/
  model/          纯领域模型，无框架依赖
  gateway/        端口接口，由 domain 层定义
  service/        领域服务，编排分析链

infrastructure/
  gateway/        适配器，实现 domain 端口

trigger/
  agent/          @Tool 方法，暴露给 LLM
```

---

## 新增组件清单

### Domain 层

| 类 | 职责 |
|---|---|
| `Alert` | 告警领域模型，字段通用，兼容不同 ES schema |
| `ParsedAlert` | 解析后的结构化告警（值对象），含异常类名、栈帧、业务 ID 等 |
| `AlertAnalysisResult` | 分析结果，含根因摘要、排查建议、关联合同、命中 Runbook |
| `AlertRepository` | 端口接口：`findById` / `findRecent` / `findByKeyword` |
| `AlertParser` | 解析原始日志/堆栈，输出 ParsedAlert（只解析，不判断） |
| `BusinessIdExtractor` | 正则扫描文本，提取合同号、订单号（规则可扩展） |
| `StackTraceCodeLocator` | 栈帧转本地文件路径，读取出错行 ±30 行代码片段 |
| `AlertAnalysisService` | 编排分析链，协调上述所有 domain 服务 |

### Infrastructure 层

| 类 | 职责 |
|---|---|
| `ElasticsearchAlertRepository` | 适配 ES，实现 `AlertRepository` |
| `MockAlertRepository` | 硬编码测试数据，无需真实 ES，用于本地验证分析能力 |

### Trigger 层

| 类 | 职责 |
|---|---|
| `AlertQueryTool` | `@Tool`：按 ID 或关键词查询告警列表 |
| `AlertAnalysisTool` | `@Tool`：对指定告警触发完整根因分析 |

---

## 分析链流程

```
用户输入告警 ID 或日志内容
        ↓
AlertQueryTool / AlertAnalysisTool（@Tool，trigger 层）
        ↓
AlertAnalysisService.analyze()（domain 层）
        ↓
  AlertParser → ParsedAlert
    ├─ 异常类名、message
    ├─ 过滤后的业务包栈帧（去掉 JDK/Spring 噪音）
    ├─ 服务名、时间
    └─ BusinessIdExtractor → [合同号列表、订单号列表]

        ↓ 根据是否有业务 ID 分支（两条路径）
  ┌─────────────────────────────────┐
  │  有业务 ID？                      │
  YES                               NO
  │                                  │
  ContractQueryService              SkillQueryTool
  查合同状态、节点、操作记录           查匹配的 Runbook
  └─────────────────────────────────┘
        ↓
  StackTraceCodeLocator
  → 按配置过滤业务包帧
  → 类名转文件路径（确定性映射，无需搜索）
  → 读本地源码 ±30 行
        ↓
  组装分析上下文
  [原始告警 + 业务数据 + 代码片段 + Runbook]
        ↓
  返回 AlertAnalysisResult 给 @Tool 层
        ↓
  LLM 基于完整上下文生成根因假设 + 排查建议
```

---

## 领域模型

```java
// 告警原始数据
Alert {
    String alertId
    String title
    String level            // CRITICAL / WARNING / INFO
    String serviceName
    String message          // 完整日志/堆栈内容
    Instant triggeredAt
    Map<String, Object> rawFields   // 保留扩展性
}

// 解析后的结构化告警（值对象，不可变）
ParsedAlert {
    Alert source
    String exceptionClassName       // e.g. NullPointerException
    String exceptionMessage
    List<StackFrame> businessFrames // 已过滤，只含业务包帧
    List<String> contractCodes      // 提取到的合同号
    List<String> orderIds           // 提取到的订单号
    String serviceName
    Instant triggeredAt
}

// 栈帧
StackFrame {
    String className
    String methodName
    String fileName
    int lineNumber
}

// 分析结果
AlertAnalysisResult {
    Alert alert
    List<String> relatedContracts
    String rootCauseSummary
    List<String> suggestions
    List<String> relatedRunbooks
    List<CodeSnippet> codeSnippets
    Instant analyzedAt
}

// 代码片段
CodeSnippet {
    String filePath
    int errorLine
    String content      // 出错行 ±30 行的源码
}
```

---

## AlertRepository 接口

```java
public interface AlertRepository {
    Optional<Alert> findById(String alertId);
    List<Alert> findRecent(Duration window, int limit);
    List<Alert> findByKeyword(String keyword, Duration window);
}
```

---

## 代码检索设计

### 原理

Java 堆栈帧包含完整类名和行号，类名可确定性地映射到文件路径：

```
com.yycome.contract.service.SignService
  → {sourceRoot}/com/yycome/contract/service/SignService.java
```

不需要搜索引擎，直接文件系统定位。

### 边界情况处理

| 情况 | 处理方式 |
|---|---|
| 文件不在 sourceRoots 里 | 跳过该帧，不报错，继续处理其他帧 |
| 行号超出文件范围 | 读文件头部 50 行 |
| 文件超过 500 行且无行号 | 只读头部 50 行 |
| 非业务包的帧 | filterFrames 直接过滤 |

---

## 配置

```yaml
sremate:
  alert:
    repository: mock              # mock | elasticsearch
    es-index: app-alerts          # ES 索引名（elasticsearch 模式下生效）
    es-timestamp-field: "@timestamp"
  code-search:
    source-roots:
      - /path/to/project/src/main/java
    business-packages:            # 只检索这些包下的栈帧
      - com.yycome
      - com.ke.utopia
```

---

## 验收标准

### AC-1：AlertParser 解析能力
- 给定一段含 `NullPointerException` 的 Java 堆栈，能正确提取异常类名、message、业务包栈帧
- 给定一段含 `contractCode=C12345` 的日志，`BusinessIdExtractor` 能提取出 `C12345`
- 给定混合内容（堆栈 + 业务日志），两者均能正确提取

### AC-2：代码定位能力
- 给定栈帧 `com.yycome.contract.service.SignService.sign(SignService.java:142)`，能正确定位到本地文件并读取第 112~172 行
- 给定不存在于 sourceRoots 的类名，跳过该帧不抛异常
- 给定非业务包帧（如 `java.util.ArrayList`），被过滤不出现在结果中

### AC-3：分析链 Path A（有业务 ID）
- 输入含合同号的告警，分析结果中包含该合同的业务数据
- `AlertAnalysisResult.relatedContracts` 非空

### AC-4：分析链 Path B（无业务 ID）
- 输入不含业务 ID 的纯技术堆栈，分析结果中包含命中的 Runbook
- `AlertAnalysisResult.relatedRunbooks` 非空（前提：有匹配的 Runbook）

### AC-5：Mock 模式可用
- 配置 `repository: mock` 时，无需 ES 连接即可完成完整分析链
- 所有单元测试使用 Mock 数据，不依赖外部服务

### AC-6：职责边界
- `AlertParser` 不调用任何外部服务
- `AlertAnalysisService` 不直接依赖 `AlertRepository` 实现类（只依赖接口）
- `AlertAnalysisService` 不直接依赖任何 `@Tool` 类，通过 domain service 调用

---

## 测试要求

每个 domain service 必须有对应单元测试，测试通过后任务方可结束：

| 测试类 | 覆盖内容 |
|---|---|
| `AlertParserTest` | 正常堆栈解析、空 message、多异常嵌套 |
| `BusinessIdExtractorTest` | 各种合同号格式、无 ID 场景、多 ID 混合 |
| `StackTraceCodeLocatorTest` | 正常定位、文件不存在、行号越界、非业务包过滤 |
| `AlertAnalysisServiceTest` | Path A 流程、Path B 流程，均使用 Mock 依赖 |
| `MockAlertRepositoryTest` | findById / findRecent / findByKeyword 基本功能 |

测试位置（遵循项目约定）：
`src/test/java/com/yycome/sremate/domain/alert/`

---

## 不在本期范围内

- Watch 主动监听模式（定时轮询 ES）
- 横向扩展到其他业务域
- 告警聚合/去重
- 分析结果持久化
- 远程 Git 仓库代码检索
