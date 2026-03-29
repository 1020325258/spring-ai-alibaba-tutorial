# ChatUI Frontend

## UI 框架源码

当前使用的 UI 框架源码位于：`/Users/zqy/work/AI-Project/chat-ui/markstream-vue`

如需了解组件实现细节或进行定制，可参考该源码。

## UI 设计参考

参考 playground demo 实现：`/Users/zqy/work/AI-Project/chat-ui/markstream-vue/playground`

主要参考页面：
- `playground/src/pages/index.vue` - 流式 Markdown 聊天 Demo

设计要点：
- 聊天窗口铺满屏幕（max-width: 90vw, height: calc(100vh - 2rem)）
- 消息气泡宽度 max-width: 90%
- 浅色主题背景 + 白色卡片容器
- Header 带渐变图标和副标题
- 输入区域使用边框包裹样式

## 本次开发经验

### 页面布局
- 左侧固定侧边栏（260px），右侧聊天区域
- 聊天区域分为两部分：消息列表（可滚动）+ 输入框（固定在底部）
- 消息内容区最大宽度 64rem 居中

### 消息气泡样式
- 用户消息：蓝色背景 (#3b82f6)，靠右，宽度自适应内容（max-width: 85%）
- Agent 消息：浅灰背景 (#f9fafb)，铺满宽度，带边框
- 气泡圆角 1rem，底部小圆角 0.25rem

### 输入框设计
- 胶囊形状（border-radius: 1.5rem）
- 白色背景带边框
- 聚焦时蓝色边框 + 阴影
- 圆形发送按钮