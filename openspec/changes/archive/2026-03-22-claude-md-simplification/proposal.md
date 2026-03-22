# Proposal: 精简 CLAUDE.md 内容

## Problem

CLAUDE.md 文件过长（773 行），存在以下问题：
- 重复内容：同一概念在多处重复说明
- 过度详细：示例代码过多，可以精简
- 结构松散：相关章节分散，缺乏整合

## Solution

通过 Agent Team 协作精简 CLAUDE.md：
1. 删除重复章节（测试清单表格、重复的 @DataQueryTool 说明）
2. 精简过长章节（DirectOutput、新增实体 SOP、HTTP 接口等）
3. 合并相关章节（环境配置 + 敏感配置、QueryScope + 本体论引擎）

## Impact

- 代码量从 773 行减少到 373 行（减少 51.7%）
- 内容更精炼，查阅效率提升
- 保留所有核心规范和关键信息
