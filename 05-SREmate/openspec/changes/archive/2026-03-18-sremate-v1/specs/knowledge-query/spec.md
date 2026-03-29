## ADDED Requirements

### Requirement: 知识库向量检索
系统 SHALL 提供 `KnowledgeQueryTool`，通过向量相似度检索 `skills/` 目录下的 Markdown 知识文档，回答 SRE 相关的操作手册类问题。

#### Scenario: 检索到相关文档
- **WHEN** 用户提问与某 Markdown 文档内容语义相近
- **THEN** 返回相关文档片段作为回答依据

#### Scenario: 无相关文档
- **WHEN** 用户提问在知识库中无匹配内容
- **THEN** 工具返回空结果，LLM 可据此回答"知识库中暂无相关信息"

---

### Requirement: 知识库加载与向量化
系统 SHALL 在应用启动时将 `resources/skills/` 目录下的 Markdown 文件向量化并存入向量数据库（SimpleVectorStore）。

#### Scenario: 启动时自动加载
- **WHEN** 应用启动
- **THEN** `KnowledgeLoader` 遍历所有 `.md` 文件，完成向量化入库，无需手动触发
