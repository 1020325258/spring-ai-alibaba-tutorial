# 通宵任务执行指南

## 问题根因
Context 溢出触发 compact → Ralph loop 中断

## 解决方案：接力式执行

### 方案 1：任务队列文件（推荐）

创建一个任务队列文件，每完成一个任务就标记：

```markdown
# 任务队列
- [ ] 任务1: 删除死代码
- [ ] 任务2: 重构代码
- [ ] 任务3: 添加测试
```

Prompt 设计：
```
读取 .claude/task-queue.md，找到第一个未完成的任务（[ ]），执行它，完成后：
1. git add -A && git commit -m "完成: 任务名"
2. 更新 task-queue.md 把 [ ] 改成 [x]
3. 输出 <promise>NEXT_TASK</promise>
```

### 方案 2：Git 驱动（更简单）

让 Claude 自己从 git 状态判断进度：

```
检查 .claude/task-queue.md：
- 如果不存在，创建任务列表
- 找到第一个未完成的任务执行
- 完成后 git commit + 更新任务列表
- 输出 <promise>NEXT</promise>

如果所有任务都完成了，输出 <promise>ALL_DONE</promise>
```

## 具体操作步骤

### 步骤 1：创建任务队列
```bash
# 在项目根目录创建
cat > .claude/task-queue.md << 'EOF'
# 任务队列 - 通宵执行

## 规则
- 每个 [ ] 是待完成任务
- 每个任务完成后：git commit + 改成 [x]
- 完成后输出 <promise>NEXT</promise>
- 全部完成输出 <promise>ALL_DONE</promise>

## 任务列表
- [ ] 任务1描述
- [ ] 任务2描述
- [ ] 任务3描述
...
EOF
```

### 步骤 2：启动 Ralph Loop
```bash
/ralph-loop 读取 .claude/task-queue.md，找到第一个未完成的任务并执行。完成后 git commit 并更新文件，输出 <promise>NEXT</promise>。如果全部完成输出 <promise>ALL_DONE</promise> --max-iterations 100 --completion-promise ALL_DONE
```

### 步骤 3：第二天检查
```bash
# 查看完成了多少任务
grep '\[x\]' .claude/task-queue.md | wc -l

# 查看 git 日志
git log --oneline -20
```

## 关键设计原则

1. **每个任务独立** - 不依赖上下文，从 git 状态恢复
2. **任务粒度小** - 单个任务在 context 限制内完成
3. **显式 checkpoint** - git commit + 文件标记
4. **幂等性** - 重新执行不会破坏已完成的工作
