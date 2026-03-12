# SREmate Web 模式设计文档

**日期：** 2026-03-12
**状态：** 已批准

---

## 背景

SREmate 当前为控制台交互应用，适合个人使用。为支持团队共享部署，需要增加 Web 模式，让团队成员通过浏览器访问，零安装成本。

---

## 方案决策

选择 **内网共享 Web 服务** 方案，理由：
- 一次部署，全员可用
- API Key 集中管理，无需每人申请
- 新人直接使用，无门槛
- 无需 Docker，JAR + 配置文件即可部署

---

## 架构设计

```
浏览器
  ↕  SSE 流式
ChatController（新增）
  ↕
ChatClient（复用现有）
  ↕
Agent Tools（完全不动）
```

**关键发现：**
- 测试配置中已有 `sre.console.enabled=false`，控制台模式可关闭
- `server.port: 0` 改为 `${SERVER_PORT:8080}` 即可对外服务
- 现有 Agent 逻辑完全复用，无需改动

---

## 组件设计

### 1. Backend API

**新增文件：** `src/main/java/com/yycome/sremate/trigger/http/ChatController.java`

```
POST /api/chat
Content-Type: application/json
{"message": "826031111000001859的报价单", "sessionId": "uuid-xxx"}

→ text/event-stream
data: {"content": "{\"decorateBudget"}
data: {"content": "List\":[...]}"}
data: [DONE]
```

**关键点：**
- 使用 Spring AI 的 `ChatClient.stream()` 实现流式输出
- `sessionId` 由前端生成，后端用 `InMemoryChatMemory` 维护对话历史
- Session 无需持久化，重启后历史清空
- 错误返回 `data: {"error": "xxx"}`

### 2. Frontend UI

**新增文件：** `src/main/resources/static/index.html`

单个 HTML 文件，纯 HTML/CSS/JS，无依赖。

**布局：**
```
┌─────────────────────────────────┐
│         SREmate Agent           │
├─────────────────────────────────┤
│  [用户消息]               你    │
│                                 │
│  助手: {"decorateBudgetList"... │
│  ← 逐字流式展示                 │
├─────────────────────────────────┤
│  [输入框]          [发送]       │
└─────────────────────────────────┘
```

**技术实现：**
- `fetch` + `ReadableStream` 读取 SSE
- `sessionId` 用 `sessionStorage` 存储
- JSON 响应用 `<pre>` 格式化展示
- `Ctrl+Enter` 或点击发送

### 3. 配置 Profile

**新增文件：** `src/main/resources/application-web.yml`

```yaml
server:
  port: ${SERVER_PORT:8080}

sre:
  console:
    enabled: false
```

---

## 部署流程

```bash
# 1. 打包
mvn package -pl 05-SREmate -DskipTests

# 2. 上传 JAR 到内网服务器，创建配置文件
cat > application-local.yml <<EOF
spring:
  datasource:
    sre:
      jdbc-url: jdbc:mysql://your-db/...
      username: xxx
      password: xxx
  ai:
    dashscope:
      api-key: sk-xxx
EOF

# 3. 启动
java -jar sremate.jar --spring.profiles.active=local,web
```

浏览器访问 `http://服务器IP:8080` 即可使用。

---

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `trigger/http/ChatController.java` | 新增 | SSE 流式聊天接口 |
| `resources/static/index.html` | 新增 | 前端聊天页面 |
| `resources/application-web.yml` | 新增 | Web 部署配置 |
| `resources/application.yml` | 修改 | `server.port: 0` 改为 `${SERVER_PORT:0}` |

---

## 非目标

- 用户认证（内网工具，暂不需要）
- 对话历史持久化（重启清空，可接受）
- 多模型切换（当前只用 DashScope）
