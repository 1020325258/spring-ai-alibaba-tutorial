# Proposal: 移除未使用的知识库模块

## Problem

知识库模块（基于 Elasticsearch 的 RAG 检索）在项目中已禁用：
- `spring.ai.vectorstore.elasticsearch.enabled: false`
- `knowledge.loader.enabled: false`

该功能未实际使用，且无测试覆盖，增加了代码维护成本。

## Solution

清理知识库相关代码，包括：
- 领域模型（6个文件）
- 领域服务（1个文件）
- 基础设施配置（3个文件）
- 触发层组件（2个文件）
- pom.xml 依赖清理
- application.yml 配置清理

## Impact

- 代码量减少约 1,000 行
- Java 文件减少 12 个
- 移除 4 个未使用的 Maven 依赖
- 无功能影响（模块已禁用）

## Non-goals

- 不涉及本体论查询功能
- 不涉及其他已启用的功能模块
