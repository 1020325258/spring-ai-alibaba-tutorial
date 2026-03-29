## ADDED Requirements

### Requirement: 层级自适应计算
系统 SHALL 根据关系图自动计算每个实体的层级深度，而非使用硬编码映射。

#### Scenario: BFS 层级推导
- **WHEN** 加载本体数据
- **THEN** 系统从根节点（Order）出发，通过 BFS 遍历关系图，计算每个实体的层级深度

#### Scenario: ContractInstance 正确层级
- **WHEN** 关系路径为 Order → Contract → ContractInstance
- **THEN** ContractInstance 层级为 2，显示在最后一层

#### Scenario: 新增实体自动适应
- **WHEN** 在 YAML 中新增实体并定义关系
- **THEN** 页面刷新后新实体自动计算层级，无需手动配置

---

### Requirement: 节点视觉层次区分
系统 SHALL 根据实体在层级中的位置应用不同的视觉样式，入口实体（Level 0）使用圆角矩形，其他实体使用椭圆。

#### Scenario: 入口节点形状
- **WHEN** 实体为 Order（Level 0）
- **THEN** 节点形状为 `box`（圆角矩形），视觉权重最高

#### Scenario: 核心节点形状
- **WHEN** 实体为 Contract 或 BudgetBill（Level 1）
- **THEN** 节点形状为 `ellipse`，尺寸适中

#### Scenario: 关联节点形状
- **WHEN** 实体为 Level 2 或更深层级
- **THEN** 节点形状为 `ellipse`，尺寸略小

---

### Requirement: 节点渐变配色
系统 SHALL 为节点应用双色配色方案，背景色为浅色调，边框为同色系的深色调，形成视觉层次。

#### Scenario: 签约领域配色
- **WHEN** 实体属于 contract 领域
- **THEN** 节点使用蓝色系（背景 #4FC3F7，边框 #0288D1）

#### Scenario: 报价领域配色
- **WHEN** 实体属于 quote 领域
- **THEN** 节点使用橙色系（背景 #FFB74D，边框 #EF6C00）

#### Scenario: 默认配色
- **WHEN** 实体未指定领域
- **THEN** 节点使用绿色系（背景 #A5D6A7，边框 #388E3C）

---

### Requirement: 节点多层阴影
系统 SHALL 为节点应用多层阴影效果，增加立体感。

#### Scenario: 默认阴影
- **WHEN** 节点处于默认状态
- **THEN** 显示模糊阴影（blur: 12px，颜色为节点背景色的 55% 透明度）

#### Scenario: 悬停阴影
- **WHEN** 鼠标悬停在节点上
- **THEN** 阴影增强（模糊度增大，颜色更明显）

---

### Requirement: 节点标签格式
系统 SHALL 以多行格式显示节点标签，第一行为实体名（加粗），第二行为中文显示名（较小字号）。

#### Scenario: 双行标签
- **WHEN** 实体有 displayName
- **THEN** 标签显示为 "Contract\n合同"，第一行加粗

#### Scenario: 单行标签
- **WHEN** 实体无 displayName
- **THEN** 标签仅显示实体名

---

### Requirement: 节点悬停效果
系统 SHALL 在鼠标悬停节点时提供明显的视觉反馈。

#### Scenario: 悬停高亮
- **WHEN** 鼠标悬停在节点上
- **THEN** 节点边框加粗至 4px，阴影增强

---

### Requirement: 边渐变着色
系统 SHALL 根据目标节点的领域色为边着色，从视觉上关联源节点和目标节点。

#### Scenario: 边颜色匹配
- **WHEN** 边指向 contract 领域的实体
- **THEN** 边使用蓝色系

#### Scenario: 边颜色匹配
- **WHEN** 边指向 quote 领域的实体
- **THEN** 边使用橙色系

---

### Requirement: 边悬停效果
系统 SHALL 在鼠标悬停边时提供视觉反馈。

#### Scenario: 悬停高亮
- **WHEN** 鼠标悬停在边上
- **THEN** 边宽度增加至 3px，颜色变为领域色，标签背景高亮

---

### Requirement: 边标签样式
系统 SHALL 为边标签应用圆角气泡背景，提高可读性。

#### Scenario: 标签背景
- **WHEN** 边显示标签（via.source_field）
- **THEN** 标签有圆角背景，颜色与深色主题协调

---

### Requirement: 箭头增强
系统 SHALL 使用较大比例的箭头指示关系方向。

#### Scenario: 箭头尺寸
- **WHEN** 渲染边的箭头
- **THEN** 箭头比例为 `scaleFactor: 0.9`，比默认值略大
