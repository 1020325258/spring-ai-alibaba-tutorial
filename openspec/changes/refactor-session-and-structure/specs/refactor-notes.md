# Refactoring Notes

本变更不涉及能力需求变更，仅进行代码结构重构和命名规范化。

## 变更类型

- **目录结构重构**: `infrastructure/config/` → `config/infra/`
- **文件重命名**: `prompts/sre-agent.md` → `prompts/query-agent.md`
- **文件名规范化**: `SPEC.md` → `spec.md`

## 验证要点

1. 所有 import 路径正确更新
2. 所有文档引用正确更新
3. 测试通过
