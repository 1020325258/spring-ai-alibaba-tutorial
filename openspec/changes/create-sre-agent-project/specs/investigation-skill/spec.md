# Investigation Skill Framework

## Overview

支持将问题排查 SOP 封装为可复用的 Skill，Agent 可根据问题类型自主加载和执行。

## Components

### SkillRegistry
- 扫描并加载 skills/ 目录下的 SKILL.md 文件
- 提供 Skill 元数据查询

### read_skill Tool
- LLM 可主动调用读取完整 Skill 内容
- 参数：skillName

### Skill 文件结构

```
skills/<skill-name>/
├── SKILL.md                    # YAML frontmatter + Markdown 指令
└── references/                 # 详细文档（可选）
```

## SKILL.md Format

```yaml
---
name: skill-name
description: 一句话描述用途（用于 LLM 匹配）
---

# Skill 标题

## 触发条件
用户说什么时触发此 Skill

## 排查步骤
1. 步骤描述
2. 工具调用
3. ...

## 输出格式
结论的结构化格式
```

---

## Acceptance Criteria (严格验收标准)

### 1. Skill 加载验收

- [ ] SkillRegistry 能扫描 skills/ 目录
- [ ] 能解析 SKILL.md 的 YAML frontmatter（name、description）
- [ ] 能读取 Markdown 指令内容
- [ ] `registry.get(skillName)` 能返回正确的 SkillMetadata
- [ ] `registry.size()` 能返回正确的 Skill 数量

### 2. Skill 注册验收

- [ ] FileSystemSkillRegistry 能正确配置扫描路径
- [ ] ClasspathSkillRegistry 能正确配置扫描路径
- [ ] 重复注册同一 Skill 不会报错（覆盖机制）
- [ ] 注册不存在的 Skill 目录不会报错（忽略）

### 3. read_skill 工具验收

- [ ] read_skill(skillName) 能返回完整 SKILL.md 内容（不含 frontmatter）
- [ ] 读取不存在的 Skill 返回错误信息
- [ ] skillName 为空时返回错误信息

### 4. LLM 匹配验收

- [ ] System Prompt 注入后，Available Skills 列表包含所有已注册 Skill
- [ ] 每个 Skill 的 name 和 description 正确展示
- [ ] LLM 能根据 description 匹配到正确的 Skill

### 5. Skill 执行验收

- [ ] LLM 能解析 Skill 中的排查步骤
- [ ] LLM 能调用对应的工具执行步骤
- [ ] 排查结论包含：断点位置
- [ ] 排查结论包含：可能原因
- [ ] 排查结论包含：建议操作

### 6. 示例 Skill 验收 (missing-personal-quote-diagnosis)

- [ ] SKILL.md 文件存在且格式正确
- [ ] name = "missing-personal-quote-diagnosis"
- [ ] description 包含"个性化报价"关键词
- [ ] 排查步骤完整：订单→合同→签约单据→个性化报价
- [ ] LLM 能正确加载并执行此 Skill

---

## Test Cases

### Unit Tests

| 测试类 | 验证内容 |
|--------|---------|
| `SkillRegistryTest` | 扫描、解析、注册 |
| `FileSystemSkillRegistryTest` | 文件系统扫描 |
| `ReadSkillToolTest` | 工具调用、参数校验 |
| `SkillMatchingTest` | description 匹配 |

### Integration Tests

| 测试类 | 验证内容 |
|--------|---------|
| `SkillLoadingE2ETest` | 完整加载流程 |
| `MissingPersonalQuoteSkillE2ETest` | 示例 Skill 执行 |
