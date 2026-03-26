# Tasks: 精简 CLAUDE.md 内容

## Analysis

- [x] 分析 CLAUDE.md 内容结构
- [x] 识别冗余/重复内容
- [x] 识别可精简的章节
- [x] 输出精简建议报告

## Implementation

- [x] 删除测试清单章节表格（与集成测试章节重复）
- [x] 删除基础设施组件中重复的 @DataQueryTool 说明
- [x] 精简 DirectOutput 机制章节（89行→46行）
- [x] 精简新增实体 SOP 章节（129行→63行）
- [x] 精简新增 HTTP 接口章节（45行→17行）
- [x] 精简集成测试章节（39行→17行）
- [x] 合并环境配置 + 敏感配置管理
- [x] 合并 QueryScope 枚举到本体论驱动查询引擎

## Verification

- [x] 验证精简后行数：373 行
- [x] 验证减少比例：51.7%
- [x] 验证 Markdown 格式正确
