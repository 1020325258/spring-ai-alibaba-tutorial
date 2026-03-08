# SREmate RAG 知识库设计方案

## 一、背景与目标

### 1.1 背景

SREmate 目前已具备合同数据查询、HTTP 接口调用等能力，但缺少值班问题知识库的支持。研发人员在值班过程中遇到的问题，无法通过 Agent 快速获取相关经验。

### 1.2 目标

为 SREmate 增加 RAG（检索增强生成）能力，实现：

1. 值班问题知识的存储和检索
2. 支持多轮对话
3. 闭环迭代：反馈收集 → 分析优化 → 知识更新

### 1.3 约束

- 前期要求简单快速，核心功能优先
- 知识库规模：中等（500-5000条）
- 使用现有 Elasticsearch 环境

---

## 二、整体架构

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户提问                                 │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                    ChatClient (Agent)                           │
│                  sre-agent.md 系统提示词                         │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                     @Tool 工具层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ ContractTool │  │ HttpEndpoint │  │ KnowledgeQueryTool   │  │
│  │  (现有)      │  │ Tool (现有)  │  │  (新增 RAG 检索)     │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                    领域服务层 (新增)                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │               KnowledgeService                            │  │
│  │  - searchSimilar() 向量检索                               │  │
│  │  - searchHybrid() 混合检索（向量+关键词）                  │  │
│  │  - recordFeedback() 记录反馈                              │  │
│  │  - getStatistics() 获取统计报表                           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                    基础设施层 (新增)                             │
│  ┌───────────────────┐  ┌───────────────────────────────────┐  │
│  │ VectorStore       │  │ KnowledgeLoader                   │  │
│  │ (Elasticsearch)   │  │ (启动时加载 MD 文件 → 向量化入库)  │  │
│  └───────────────────┘  └───────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────┐  ┌───────────────────────────────────┐  │
│  │ FeedbackRecorder  │  │ StatisticsCollector               │  │
│  │ (反馈记录)        │  │ (统计收集)                        │  │
│  └───────────────────┘  └───────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ EmbeddingModel (通义千问 text-embedding-v3)               │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                    数据存储                                     │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  Elasticsearch Index: sremate_knowledge                   │ │
│  │  - id, content, metadata, embedding(vector)               │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  本地知识库: resources/knowledge/                          │ │
│  │  ├── faq/           # 常见问题                             │ │
│  │  ├── troubleshooting/  # 故障排查                         │ │
│  │  └── operations/    # 运维操作                             │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 全流程闭环

```
┌─────────────────────────────────────────────────────────────────┐
│                    RAG 全流程闭环                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐                                              │
│  │ 知识来源      │  MD 文件（研发编写 + Agent 辅助清洗）          │
│  └──────┬───────┘                                              │
│         ↓                                                       │
│  ┌──────────────┐                                              │
│  │ 知识入库      │  FAQ 格式切分 → 向量化 → ES 存储              │
│  └──────┬───────┘  元数据：标题、分类                           │
│         │              更新策略：启动时全量重建                  │
│         ↓                                                       │
│  ┌──────────────┐                                              │
│  │ 检索增强      │  混合检索（向量 + 关键词）                    │
│  └──────┬───────┘                                              │
│         ↓                                                       │
│  ┌──────────────┐                                              │
│  │ 生成回答      │  Agent 整合知识返回用户                       │
│  └──────┬───────┘                                              │
│         ↓                                                       │
│  ┌──────────────┐                                              │
│  │ 反馈收集      │  检索日志 + 用户点赞/点踩                      │
│  └──────┬───────┘                                              │
│         ↓                                                       │
│  ┌──────────────┐                                              │
│  │ 分析报表      │  命令行查询 / 日志文件                         │
│  └──────┬───────┘  热门问题 / 低质量知识 / 未命中问题            │
│         │                                                       │
│         ↓                                                       │
│  ┌──────────────┐                                              │
│  │ 人工优化      │  更新 MD 文件                                 │
│  └──────┬───────┘                                              │
│         │                                                       │
│         └────────────→ 下次启动生效                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、核心组件设计

### 3.1 依赖配置

**新增 Maven 依赖：**

```xml
<!-- Spring AI Elasticsearch VectorStore -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-elasticsearch-store</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- Elasticsearch Java Client -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.12.0</version>
</dependency>
```

### 3.2 配置文件

**application.yml 新增配置：**

```yaml
spring:
  ai:
    vectorstore:
      elasticsearch:
        index-name: sremate_knowledge
        initialization-mode: lazy
    dashscope:
      embedding:
        model: text-embedding-v3

  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://localhost:9200}
    username: ${ELASTICSEARCH_USERNAME:}
    password: ${ELASTICSEARCH_PASSWORD:}

# 知识库配置
knowledge:
  loader:
    enabled: true
    paths:
      - classpath:knowledge/faq/
      - classpath:knowledge/troubleshooting/
      - classpath:knowledge/operations/
```

**application-local.yml 新增敏感配置：**

```yaml
spring:
  elasticsearch:
    uris: http://your-es-host:9200
    username: your_username
    password: your_password
```

### 3.3 知识库目录结构

```
src/main/resources/knowledge/
├── faq/                      # 常见问题
│   ├── contract.md           # 合同相关 FAQ
│   └── system.md             # 系统相关 FAQ
├── troubleshooting/          # 故障排查
│   ├── database.md           # 数据库问题
│   ├── redis.md              # Redis 问题
│   └── service.md            # 服务问题
└── operations/               # 运维操作
    ├── deployment.md         # 部署相关
    └── monitoring.md         # 监控相关
```

### 3.4 知识文档格式

**FAQ 格式规范：**

```markdown
# [一级分类：如故障排查]

## [问题标题]

### 问题现象
描述问题的具体表现...

### 排查步骤
1. 第一步...
2. 第二步...

### 解决方案
具体的解决方法...

### 相关命令
```bash
# 相关命令示例
```

---

## [下一个问题标题]

### 问题现象
...
```

**元数据提取规则：**
- 一级标题（#）→ 分类（category）
- 二级标题（##）→ 问题标题（title），作为切分边界
- 每个 FAQ 单元独立入库

---

## 四、核心类设计

### 4.1 知识加载器

**职责**：启动时扫描 MD 文件，解析并入库

```java
package com.yycome.sremate.infrastructure.loader;

@Slf4j
@Component
public class KnowledgeLoader implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;
    private final KnowledgeProperties properties;

    @Override
    public void run(String... args) {
        if (!properties.getLoader().isEnabled()) {
            return;
        }

        // 清空旧数据（全量重建）
        clearIndex();

        // 加载所有知识文档
        List<Document> documents = new ArrayList<>();
        for (String path : properties.getLoader().getPaths()) {
            documents.addAll(loadDocuments(path));
        }

        // 批量入库
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("知识库加载完成，共 {} 条文档", documents.size());
        }
    }

    private List<Document> loadDocuments(String path) {
        // 1. 扫描 MD 文件
        // 2. 按二级标题切分
        // 3. 提取元数据（分类、标题）
        // 4. 封装为 Document 对象
    }
}
```

### 4.2 知识查询服务

**职责**：提供检索和反馈记录能力

```java
package com.yycome.sremate.domain.knowledge.service;

@Slf4j
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final ElasticsearchClient esClient;
    private final FeedbackRepository feedbackRepository;

    /**
     * 混合检索（向量 + 关键词）
     */
    public List<KnowledgeResult> searchHybrid(String query, int topK) {
        // 1. 向量检索
        // 2. 关键词检索 (BM25)
        // 3. RRF 融合排序
        // 4. 记录检索日志
    }

    /**
     * 记录用户反馈
     */
    public void recordFeedback(String query, String docId, FeedbackType type) {
        feedbackRepository.save(query, docId, type);
    }

    /**
     * 获取统计数据
     */
    public KnowledgeStatistics getStatistics() {
        // 返回热门问题、低质量知识、未命中问题
    }
}
```

### 4.3 知识查询工具

**职责**：Agent 调用的 @Tool 方法

```java
package com.yycome.sremate.trigger.agent;

@Slf4j
@Component
public class KnowledgeQueryTool {

    private final KnowledgeService knowledgeService;

    @Tool(description = """
        检索值班问题知识库，查找与用户问题相似的已知问题和解决方案。
        当用户询问运维问题、故障排查、常见问题时使用此工具。

        参数：
        - query: 用户的自然语言问题或关键词
        - topK: 返回结果数量，默认 3

        返回：匹配的知识条目，包含问题和解决方案
        """)
    public String searchKnowledge(String query, @ToolParam(optional = true) int topK) {
        if (topK <= 0) topK = 3;
        List<KnowledgeResult> results = knowledgeService.searchHybrid(query, topK);
        return formatResults(results);
    }

    @Tool(description = """
        对知识库检索结果进行反馈，帮助优化知识库质量。

        参数：
        - query: 原始查询问题
        - docId: 文档ID
        - feedback: 反馈类型 (helpful/unhelpful)
        """)
    public void recordFeedback(String query, String docId, String feedback) {
        knowledgeService.recordFeedback(query, docId, FeedbackType.valueOf(feedback.toUpperCase()));
    }

    @Tool(description = """
        查看知识库统计报表，包括热门问题、低质量知识等。

        参数：
        - type: 报表类型 (hot/low_quality/missed)
        """)
    public String viewKnowledgeStats(String type) {
        KnowledgeStatistics stats = knowledgeService.getStatistics();
        return formatStatistics(stats, type);
    }
}
```

### 4.4 反馈记录

**数据模型：**

```java
package com.yycome.sremate.domain.knowledge.model;

@Data
public class FeedbackRecord {
    private String id;
    private String query;           // 用户查询
    private String docId;           // 检索到的文档ID
    private FeedbackType type;      // HELPFUL / UNHELPFUL
    private LocalDateTime timestamp;
}

public enum FeedbackType {
    HELPFUL,    // 点赞
    UNHELPFUL   // 点踩
}
```

**存储方式**：使用 Elasticsearch 单独的索引 `sremate_feedback`

---

## 五、检索策略

### 5.1 混合检索流程

```
用户问题
    ↓
┌─────────────────────────────────────────────┐
│              并行检索                        │
│  ┌─────────────────┐  ┌─────────────────┐  │
│  │ 向量检索 (knn)  │  │ 关键词检索(BM25)│  │
│  │ Top 10          │  │ Top 10          │  │
│  └────────┬────────┘  └────────┬────────┘  │
│           │                    │            │
│           └────────┬───────────┘            │
│                    ↓                        │
│           ┌─────────────────┐               │
│           │  RRF 融合排序   │               │
│           │  取 Top K       │               │
│           └─────────────────┘               │
└─────────────────────────────────────────────┘
    ↓
返回结果
```

### 5.2 RRF (Reciprocal Rank Fusion)

RRF 公式：
```
RRF_score(d) = Σ 1/(k + rank_i(d))
```
其中 k 通常取 60

---

## 六、反馈与分析

### 6.1 反馈收集

**触发时机：**
- Agent 返回答案后，提示用户可反馈
- 用户输入 "有帮助" / "没帮助" 或 "👍" / "👎"

**记录内容：**
- 查询问题
- 返回的文档ID
- 反馈类型
- 时间戳

### 6.2 统计报表

**报表类型：**

| 报表 | 数据来源 | 用途 |
|------|---------|------|
| 热门问题榜 | 检索日志聚合 | 了解用户关注点 |
| 低质量知识榜 | 点踩率统计 | 提示优化内容 |
| 未命中问题榜 | 无点击的查询 | 识别知识缺口 |

**查看方式：**
1. 命令行：`/stats knowledge hot`
2. 日志文件：每日定时输出到日志

---

## 七、项目结构变化

```
src/main/java/com/yycome/sremate/
├── domain/
│   └── knowledge/                      # 新增
│       ├── model/
│       │   ├── KnowledgeResult.java
│       │   ├── FeedbackRecord.java
│       │   └── KnowledgeStatistics.java
│       └── service/
│           └── KnowledgeService.java
├── infrastructure/
│   ├── loader/                         # 新增
│   │   └── KnowledgeLoader.java
│   ├── config/                         # 新增
│   │   └── VectorStoreConfig.java
│   └── properties/                     # 新增
│       └── KnowledgeProperties.java
└── trigger/agent/
    └── KnowledgeQueryTool.java         # 新增

src/main/resources/
├── knowledge/                          # 新增目录
│   ├── faq/
│   ├── troubleshooting/
│   └── operations/
└── application.yml                     # 新增配置
```

---

## 八、实施计划

### 阶段一：基础能力（P0）

1. 添加依赖和配置
2. 实现 KnowledgeLoader（MD 解析 + 入库）
3. 实现 KnowledgeService（混合检索）
4. 实现 KnowledgeQueryTool

### 阶段二：反馈闭环（P1）

1. 实现反馈记录
2. 实现统计报表
3. 更新 Agent 提示词

### 阶段三：优化迭代（P2）

1. 优化检索效果
2. 增量更新支持（可选）
3. 知识库管理界面（可选）

---

## 九、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| ES 连接失败 | 知识库不可用 | 降级：返回提示"知识库暂时不可用" |
| 向量化 API 限流 | 启动时间长 | 批量处理 + 重试机制 |
| 知识质量参差不齐 | 检索效果差 | 反馈机制驱动优化 |

---

## 十、验收标准

1. **功能验收**
   - 启动时能正确加载 MD 文件到 ES
   - 用户问题能检索到相关知识
   - 反馈能正确记录

2. **性能验收**
   - 检索响应时间 < 1s
   - 启动时间增量 < 30s（500条知识）

3. **体验验收**
   - Agent 能正确识别知识查询意图
   - 返回结果与问题相关
