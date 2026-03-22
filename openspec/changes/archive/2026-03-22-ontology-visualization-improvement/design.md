## Context

当前本体可视化页面使用 vis-network 库渲染实体关系图。存在以下问题：

**层级硬编码问题**：
- 使用 `entityLevel` 对象手动指定每个实体的层级数字
- 新增实体时需要手动添加层级配置，容易遗漏
- ContractInstance 因未配置层级，被错误放置在第一层

**样式问题**：
- 节点：纯色填充的椭圆形，单层阴影
- 边：灰色曲线，小字号标签
- 缺乏视觉层次和交互反馈

## Goals / Non-Goals

**Goals:**
- 实现层级自适应：根据关系图自动计算每个实体的层级深度
- 提升节点视觉层次感，突出核心实体
- 增强边的方向性和交互反馈
- 提高整体配色对比度和可读性

**Non-Goals:**
- 不改变图谱的布局算法（保持 hierarchical 布局）
- 不增加新的交互功能（如搜索、筛选）
- 不修改后端 API

## Decisions

### D1: 层级自适应算法

**决策**：使用 BFS（广度优先遍历）从根节点出发，自动计算每个实体的层级深度

```javascript
function calculateEntityLevels(entities, relations) {
    const levels = {};
    const adjacencyList = {};

    // 构建邻接表
    relations.forEach(r => {
        if (!adjacencyList[r.from]) adjacencyList[r.from] = [];
        adjacencyList[r.from].push(r.to);
    });

    // BFS 从 Order 出发
    const queue = [{ name: 'Order', level: 0 }];
    const visited = new Set(['Order']);

    while (queue.length > 0) {
        const { name, level } = queue.shift();
        levels[name] = level;

        const neighbors = adjacencyList[name] || [];
        neighbors.forEach(neighbor => {
            if (!visited.has(neighbor)) {
                visited.add(neighbor);
                queue.push({ name: neighbor, level: level + 1 });
            }
        });
    }

    // 未访问到的实体（无入边）放在 Level 0
    entities.forEach(e => {
        if (levels[e.name] === undefined) {
            levels[e.name] = 0;
        }
    });

    return levels;
}
```

**理由**：
- 无需手动维护层级映射，新增实体自动适应
- 保证层级关系符合本体论定义的依赖方向
- ContractInstance 会正确显示在 Level 2（Order → Contract → ContractInstance）

**备选方案**：
- 使用 vis-network 的 `sortMethod: 'directed'` 自动排序 → 经测试不够精确
- 后端计算层级返回给前端 → 增加后端改动，不必要

### D2: 节点形状分层

**决策**：根据计算出的层级使用不同形状

| 层级 | 形状 | 说明 |
|------|------|------|
| Level 0（Order） | `box` 圆角矩形 | 入口实体，视觉权重最高 |
| Level 1 | `ellipse` 椭圆 | 核心实体 |
| Level 2+ | `ellipse` 椭圆，较小尺寸 | 关联实体 |

**理由**：box 形状更醒目，适合入口节点；ellipse 更紧凑，适合数量较多的关联实体。

### D3: 渐变配色方案

**决策**：采用双色配色方案，模拟渐变效果

```javascript
const DOMAIN_COLOR = {
    contract: {
        bg: '#4FC3F7',
        border: '#0288D1',
        glow: 'rgba(79, 195, 247, 0.4)'
    },
    quote: {
        bg: '#FFB74D',
        border: '#EF6C00',
        glow: 'rgba(255, 183, 77, 0.4)'
    },
    default: {
        bg: '#A5D6A7',
        border: '#388E3C',
        glow: 'rgba(165, 214, 167, 0.4)'
    }
};
```

**注意**：vis-network 不支持 CSS 渐变，使用浅色背景 + 深色边框模拟层次感。

### D4: 边样式增强

**决策**：使用 vis-network 内置样式实现边的视觉增强

| 样式 | 配置 |
|------|------|
| 颜色 | 与目标节点领域色一致 |
| 宽度 | 默认 1.5px，悬停 3px |
| 平滑 | `curvedCW` 圆滑曲线 |
| 箭头 | 增大比例 `scaleFactor: 0.9` |
| 悬停 | 整条边变色 + 标签背景高亮 |

### D5: 节点标签优化

**决策**：使用 vis-network 的 `font.multi` 支持多行标签

```javascript
label: e.name + '\n' + (e.displayName || ''),
font: {
    size: 14,
    bold: { size: 16, color: '#1e1e2e' },
    multi: 'html'
}
```

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| BFS 算法在复杂关系图可能不完美 | 当前关系图为树状结构，无环，BFS 足够 |
| 渐变效果受限于 vis-network 能力 | 使用纯色 + 边框模拟层次感 |
| 过多动画可能影响性能 | 仅在悬停时触发动画，保持轻量 |
| 新配色可能在某些显示器上显示不佳 | 使用标准 sRGB 色域颜色 |
