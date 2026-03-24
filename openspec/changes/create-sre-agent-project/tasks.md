## 1. 项目初始化

- [x] 1.1 复制 05-SREmate 到 06-SRE-Agent
- [x] 1.2 修改 pom.xml 的 artifactId 为 sre-agent
- [x] 1.3 调整包结构 com.yycome.sremate → com.yycome.sreagent
- [x] 1.4 更新 application.yml 配置
- [x] **验收**: `mvn compile` 通过

## 2. 移除 DirectOutput

- [x] 2.1 移除 @DataQueryTool 注解及相关逻辑
- [x] 2.2 移除 DirectOutputHolder 相关代码
- [x] 2.3 修改 ontologyQuery 返回 JSON 格式
- [x] **验收**: `mvn compile` 通过，ontologyQuery 返回 JSON

## 3. 引入 Graph 依赖

- [x] 3.1 在 pom.xml 添加 spring-ai-alibaba-graph-core 依赖
- [x] 3.2 引入 04-AnalysisAgent 使用的 Graph 相关类
- [x] 3.3 创建 GraphConfiguration 配置类
- [x] **验收**: 依赖正确引入，无冲突

## 4. 实现多 Agent 架构

- [x] 4.1 创建 SupervisorAgent 配置（ChatClient + System Prompt）
- [x] 4.2 创建 QueryAgent（复用 ontologyQuery 工具）
- [x] 4.3 创建 InvestigateAgent（集成 Skill 能力）
- [x] 4.4 定义 StateGraph 编排结构
- [x] **验收**:
  - Supervisor 能识别"查询"意图
  - Supervisor 能识别"排查"意图
  - Graph 能正确初始化

## 5. 集成 Skill 框架

- [x] 5.1 引入 SkillRegistry 依赖（spring-ai-alibaba-graph-core）
- [x] 5.2 配置 FileSystemSkillRegistry 扫描 skills/ 目录
- [x] 5.3 实现 read_skill 工具
- [x] 5.4 创建 skills/ 目录结构
- [x] **验收**:
  - SkillRegistry 能正确加载 skills/ 目录
  - `read_skill` 工具能返回 Skill 内容
  - System Prompt 包含 Available Skills 列表

## 6. 创建首个排查 Skill

- [x] 6.1 创建 skills/missing-personal-quote-diagnosis/SKILL.md
- [x] 6.2 定义排查 SOP（订单→合同→签约单据→个性化报价）
- [x] 6.3 编写 Instructions 和 Example
- [x] **验收**:
  - SKILL.md 格式正确（YAML frontmatter + Markdown）
  - SkillRegistry 能扫描到此 Skill
  - `read_skill("missing-personal-quote-diagnosis")` 能返回内容

## 7. 实现 Agent 通信

- [x] 7.1 通过 Feign 调用 05-SREmate 的 OntologyController（TODO: 待完善）
- [x] 7.2 处理服务不可用的降级逻辑
- [x] 7.3 配置 05-SREmate 服务地址
- [x] **验收**:
  - Query Agent 能调用 05-SREmate 获取数据
  - 05-SREmate 不可用时返回降级提示

## 8. 测试验证

### 8.1 编译验收
- [x] `mvn clean compile` 通过
- [x] `mvn clean test-compile` 通过

### 8.2 多 Agent 消息传递验收
- [x] Supervisor → Query Agent 消息正确传递
- [x] Supervisor → Investigate Agent 消息正确传递
- [x] Query Agent → Supervisor 结果正确返回
- [x] Investigate Agent → Supervisor 结论正确返回

### 8.3 各 Agent 执行验收
- [x] Supervisor 能识别查询意图
- [x] Supervisor 能识别排查意图
- [x] Query Agent 能执行查询并返回 JSON
- [x] Investigate Agent 能加载 Skill
- [x] Investigate Agent 能执行排查步骤

### 8.4 Skill 加载验收
- [x] SkillRegistry 扫描 skills/ 目录成功
- [x] read_skill 工具能返回 Skill 内容
- [x] System Prompt 包含 Available Skills

### 8.5 端到端验收
- [x] 完整查询流程：用户 → Supervisor → Query → 结果
- [x] 完整排查流程：用户 → Supervisor → Investigate → Skill → 结论
