# SREmate Web 模式 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 SREmate 增加 Web 模式，支持通过浏览器流式访问 Agent，实现团队共享部署。

**Architecture:** 新增 `ChatController` 提供 SSE 流式接口，新增 `index.html` 前端页面，新增 `application-web.yml` 配置 profile。复用现有 ChatClient 和 Agent Tools，零改动。

**Tech Stack:** Spring WebFlux（SSE）、Spring AI ChatClient、原生 HTML/CSS/JS

---

### Task 1: 修改 application.yml 支持可配置端口

**Files:**
- Modify: `05-SREmate/src/main/resources/application.yml:1-2`

**Step 1: 修改端口配置**

将：
```yaml
server:
  port: 0
```

改为：
```yaml
server:
  port: ${SERVER_PORT:0}
```

**Step 2: Commit**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/src/main/resources/application.yml
git commit -m "feat(sremate): make server port configurable via env var

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 2: 创建 application-web.yml 配置 profile

**Files:**
- Create: `05-SREmate/src/main/resources/application-web.yml`

**Step 1: 创建配置文件**

```yaml
server:
  port: ${SERVER_PORT:8080}

sre:
  console:
    enabled: false
```

**Step 2: Commit**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/src/main/resources/application-web.yml
git commit -m "feat(sremate): add web profile for team deployment

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 3: 创建 ChatController SSE 流式接口

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/http/ChatController.java`

**Step 1: 查看现有 ChatClient 用法**

读取 `IntentAwareChatService.java` 了解 ChatClient 的调用方式，确保复用正确。

```bash
cat 05-SREmate/src/main/java/com/yycome/sremate/domain/intent/service/IntentAwareChatService.java
```

**Step 2: 创建 ChatController**

```java
package com.yycome.sremate.trigger.http;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 聊天接口
 * 提供 SSE 流式响应，支持团队共享部署
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final IntentAwareChatService chatService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L); // 60s 超时

        // 异步处理，避免阻塞 Servlet 线程
        new Thread(() -> {
            try {
                StringBuilder fullResponse = new StringBuilder();
                Flux<String> stream = chatService.chatStream(request.sessionId(), request.message());

                stream.subscribe(
                        chunk -> {
                            try {
                                fullResponse.append(chunk);
                                emitter.send(SseEmitter.event()
                                        .data("{\"content\":\"" + escapeJson(chunk) + "\"}"));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record ChatRequest(String message, String sessionId) {}
}
```

**Step 3: Commit**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/http/ChatController.java
git commit -m "feat(sremate): add ChatController with SSE streaming endpoint

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 4: 为 IntentAwareChatService 添加 chatStream 方法

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/domain/intent/service/IntentAwareChatService.java`

**Step 1: 读取现有代码**

```bash
cat 05-SREmate/src/main/java/com/yycome/sremate/domain/intent/service/IntentAwareChatService.java
```

**Step 2: 添加 chatStream 方法**

在现有 `chat` 方法附近添加：

```java
/**
 * 流式聊天（用于 Web SSE）
 *
 * @param sessionId 会话 ID，用于维护对话历史
 * @param message   用户消息
 * @return 流式响应
 */
public Flux<String> chatStream(String sessionId, String message) {
    return chatClient.prompt()
            .user(message)
            .advisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, 10))
            .stream()
            .content();
}
```

需要引入：
```java
import reactor.core.publisher.Flux;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
```

**Step 3: 确保注入 chatMemory**

如果类中还没有 `chatMemory` 字段，需要添加：

```java
private final ChatMemory chatMemory;

// 在构造函数或 @Autowired 中注入
public IntentAwareChatService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
    this.chatMemory = new InMemoryChatMemory();
}
```

**Step 4: Commit**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/intent/service/IntentAwareChatService.java
git commit -m "feat(sremate): add chatStream method for SSE streaming

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 5: 创建前端聊天页面

**Files:**
- Create: `05-SREmate/src/main/resources/static/index.html`

**Step 1: 创建 static 目录（如果不存在）**

```bash
mkdir -p 05-SREmate/src/main/resources/static
```

**Step 2: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SREmate Agent</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; height: 100vh; display: flex; flex-direction: column; }
        .header { background: #1a1a2e; color: white; padding: 12px 20px; font-size: 18px; font-weight: 600; }
        .chat-container { flex: 1; overflow-y: auto; padding: 20px; }
        .message { margin-bottom: 16px; display: flex; flex-direction: column; }
        .message.user { align-items: flex-end; }
        .message.assistant { align-items: flex-start; }
        .message-label { font-size: 12px; color: #888; margin-bottom: 4px; }
        .message-content { max-width: 80%; padding: 12px 16px; border-radius: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
        .message.user .message-content { background: #1a1a2e; color: white; }
        .message.assistant .message-content { background: white; border: 1px solid #e0e0e0; }
        .message-content pre { background: #f8f8f8; padding: 8px; border-radius: 4px; overflow-x: auto; margin: 8px 0; }
        .input-container { padding: 16px 20px; background: white; border-top: 1px solid #e0e0e0; display: flex; gap: 12px; }
        #message-input { flex: 1; padding: 12px 16px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; resize: none; min-height: 44px; max-height: 120px; }
        #message-input:focus { outline: none; border-color: #1a1a2e; }
        #send-btn { padding: 12px 24px; background: #1a1a2e; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; }
        #send-btn:disabled { background: #ccc; cursor: not-allowed; }
        .typing { opacity: 0.6; }
    </style>
</head>
<body>
    <div class="header">SREmate Agent</div>
    <div class="chat-container" id="chat-container"></div>
    <div class="input-container">
        <textarea id="message-input" placeholder="输入消息... (Ctrl+Enter 发送)" rows="1"></textarea>
        <button id="send-btn">发送</button>
    </div>

    <script>
        const chatContainer = document.getElementById('chat-container');
        const messageInput = document.getElementById('message-input');
        const sendBtn = document.getElementById('send-btn');

        // 生成或获取 sessionId
        let sessionId = sessionStorage.getItem('sremate_session');
        if (!sessionId) {
            sessionId = crypto.randomUUID();
            sessionStorage.setItem('sremate_session', sessionId);
        }

        function addMessage(role, content) {
            const msg = document.createElement('div');
            msg.className = `message ${role}`;
            msg.innerHTML = `
                <div class="message-label">${role === 'user' ? '你' : '助手'}</div>
                <div class="message-content">${formatContent(content)}</div>
            `;
            chatContainer.appendChild(msg);
            chatContainer.scrollTop = chatContainer.scrollHeight;
            return msg;
        }

        function formatContent(content) {
            // 尝试格式化 JSON
            try {
                const json = JSON.parse(content);
                return `<pre>${JSON.stringify(json, null, 2)}</pre>`;
            } catch {
                return content;
            }
        }

        async function sendMessage() {
            const message = messageInput.value.trim();
            if (!message) return;

            sendBtn.disabled = true;
            messageInput.value = '';
            addMessage('user', message);

            const assistantMsg = addMessage('assistant', '');
            const contentDiv = assistantMsg.querySelector('.message-content');
            let fullContent = '';

            try {
                const response = await fetch('/api/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message, sessionId })
                });

                const reader = response.body.getReader();
                const decoder = new TextDecoder();

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    const chunk = decoder.decode(value);
                    const lines = chunk.split('\n');

                    for (const line of lines) {
                        if (line.startsWith('data: ')) {
                            const data = line.slice(6);
                            if (data === '[DONE]') break;
                            try {
                                const json = JSON.parse(data);
                                fullContent += json.content;
                                contentDiv.innerHTML = formatContent(fullContent);
                                chatContainer.scrollTop = chatContainer.scrollHeight;
                            } catch {}
                        }
                    }
                }
            } catch (err) {
                contentDiv.textContent = '请求失败: ' + err.message;
            } finally {
                sendBtn.disabled = false;
                messageInput.focus();
            }
        }

        sendBtn.onclick = sendMessage;
        messageInput.onkeydown = (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                e.preventDefault();
                sendMessage();
            }
        };

        // 自动调整输入框高度
        messageInput.oninput = () => {
            messageInput.style.height = 'auto';
            messageInput.style.height = Math.min(messageInput.scrollHeight, 120) + 'px';
        };

        messageInput.focus();
    </script>
</body>
</html>
```

**Step 3: Commit**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/src/main/resources/static/index.html
git commit -m "feat(sremate): add web chat UI with SSE streaming

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 6: 确保控制台模式可关闭

**Files:**
- Check: `05-SREmate/src/main/java/com/yycome/sremate/trigger/console/SREConsole.java`

**Step 1: 检查现有控制台实现**

```bash
cat 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/SREConsole.java
```

确认是否已有条件启动逻辑（如 `@ConditionalOnProperty` 或 `if (!enabled) return`）。

**Step 2: 如无条件启动，添加条件**

如果 `SREConsole` 没有条件注解，在类上添加：

```java
@ConditionalOnProperty(name = "sre.console.enabled", havingValue = "true", matchIfMissing = true)
```

**Step 3: Commit（如有修改）**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/console/SREConsole.java
git commit -m "feat(sremate): make console mode conditional on config

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 7: 编译验证

**Step 1: 编译项目**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn compile -pl 05-SREmate
```

预期：BUILD SUCCESS

**Step 2: 打包**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn package -pl 05-SREmate -DskipTests
```

预期：生成 `target/SREmate-1.0-SNAPSHOT.jar`

---

### Task 8: 更新 CLAUDE.md 文档

**Files:**
- Modify: `05-SREmate/CLAUDE.md`

**Step 1: 在文档末尾添加 Web 模式部署说明**

```markdown
## Web 模式部署

SREmate 支持两种运行模式：

### 控制台模式（默认）

```bash
java -jar sremate.jar --spring.profiles.active=local
```

### Web 模式（团队共享）

1. 创建配置文件 `application-local.yml`，填写数据库连接和 DashScope API Key
2. 启动：

```bash
java -jar sremate.jar --spring.profiles.active=local,web
```

3. 浏览器访问 `http://localhost:8080`

**配置项：**
- `SERVER_PORT`：Web 服务端口，默认 8080
- `sre.console.enabled`：是否启用控制台模式，Web 模式下设为 false
```

**Step 2: Commit**

```bash
cd /Users/zqy/work/AI-Project/spring-ai-alibaba-tutorial
git add 05-SREmate/CLAUDE.md
git commit -m "docs(sremate): add web mode deployment instructions

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```
