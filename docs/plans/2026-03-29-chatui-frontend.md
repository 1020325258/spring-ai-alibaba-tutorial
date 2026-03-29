# ChatUI Frontend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a minimal Vue 3 + markstream-vue chat UI that streams responses from a backend SSE endpoint (`POST /api/chat/stream`).

**Architecture:** Single-page Vue 3 app with Vite. A `useChat` composable handles the fetch+ReadableStream SSE parsing and accumulates content into a reactive ref. `<MarkdownRender>` from markstream-vue renders the streaming Markdown in real time. Vite proxy forwards `/api` to the backend during development.

**Tech Stack:** Vue 3, Vite 6, TypeScript, markstream-vue, Vitest, @vue/test-utils

---

## Project Location

All files go under:
```
/Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test/07-ChatUI/frontend/
```

## SSE Contract (frontend assumes this backend format)

```
POST /api/chat/stream
Content-Type: application/json
Body: { "message": "用户输入" }

Response: text/event-stream
data: 你好
data: ，有什么
data: 可以帮你？
```

Each `data:` line is a raw text chunk (no JSON wrapper). Stream ends when connection closes.

---

### Task 1: Scaffold the Vite + Vue 3 project

**Files:**
- Create: `07-ChatUI/frontend/package.json`
- Create: `07-ChatUI/frontend/index.html`
- Create: `07-ChatUI/frontend/vite.config.ts`
- Create: `07-ChatUI/frontend/tsconfig.json`
- Create: `07-ChatUI/frontend/tsconfig.node.json`

**Step 1: Create `package.json`**

```json
{
  "name": "chatui-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview",
    "test": "vitest"
  },
  "dependencies": {
    "vue": "^3.5.13",
    "markstream-vue": "^0.0.10-beta.4"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^26.1.0",
    "typescript": "^5.7.3",
    "vite": "^6.2.4",
    "vitest": "^3.1.1",
    "vue-tsc": "^2.2.8"
  }
}
```

**Step 2: Create `vite.config.ts`**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
```

**Step 3: Create `index.html`**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>ChatUI</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

**Step 4: Create `tsconfig.json`**

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.node.json" },
    { "path": "./tsconfig.app.json" }
  ]
}
```

**Step 5: Create `tsconfig.app.json`**

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedSideEffectImports": true
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"]
}
```

**Step 6: Create `tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedSideEffectImports": true
  },
  "include": ["vite.config.ts"]
}
```

**Step 7: Install dependencies**

```bash
cd /Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test/07-ChatUI/frontend
npm install
```

Expected: `node_modules/` created, no errors.

**Step 8: Commit**

```bash
git add 07-ChatUI/frontend/
git commit -m "feat(07-chatui): scaffold Vue 3 + Vite frontend project"
```

---

### Task 2: Implement `useChat` composable with tests

**Files:**
- Create: `07-ChatUI/frontend/src/composables/useChat.ts`
- Create: `07-ChatUI/frontend/src/composables/__tests__/useChat.test.ts`
- Create: `07-ChatUI/frontend/vitest.config.ts`

**Step 1: Create `vitest.config.ts`**

```typescript
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
```

**Step 2: Write the failing tests**

Create `src/composables/__tests__/useChat.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useChat } from '../useChat'

// Helper: create a mock ReadableStream from an array of text chunks
function mockStream(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(`data: ${chunk}\n\n`))
      }
      controller.close()
    },
  })
}

describe('useChat', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('starts with empty state', () => {
    const { messages, isStreaming } = useChat()
    expect(messages.value).toEqual([])
    expect(isStreaming.value).toBe(false)
  })

  it('sends user message and adds it to messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: mockStream(['hello']),
    }))

    const { messages, sendMessage } = useChat()
    await sendMessage('你好')

    expect(messages.value[0]).toMatchObject({ role: 'user', content: '你好' })
  })

  it('accumulates SSE chunks into assistant message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: mockStream(['你', '好，', '有什么可以帮你？']),
    }))

    const { messages, sendMessage } = useChat()
    await sendMessage('hi')

    const assistant = messages.value.find(m => m.role === 'assistant')
    expect(assistant?.content).toBe('你好，有什么可以帮你？')
  })

  it('sets isStreaming to false after stream ends', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: mockStream(['done']),
    }))

    const { isStreaming, sendMessage } = useChat()
    await sendMessage('test')
    expect(isStreaming.value).toBe(false)
  })
})
```

**Step 3: Run tests to verify they fail**

```bash
cd /Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test/07-ChatUI/frontend
npm test -- --run
```

Expected: FAIL — `useChat` not found.

**Step 4: Implement `useChat.ts`**

Create `src/composables/useChat.ts`:

```typescript
import { ref } from 'vue'
import { getMarkdown, parseMarkdownToStructure } from 'markstream-vue'
import type { BaseNode } from 'markstream-vue'

export interface Message {
  role: 'user' | 'assistant'
  content: string
  nodes: BaseNode[]
  streaming: boolean
}

export function useChat() {
  const messages = ref<Message[]>([])
  const isStreaming = ref(false)
  const md = getMarkdown()

  async function sendMessage(text: string) {
    messages.value.push({ role: 'user', content: text, nodes: [], streaming: false })
    isStreaming.value = true

    // Add empty assistant message placeholder
    const assistantMsg: Message = { role: 'assistant', content: '', nodes: [], streaming: true }
    messages.value.push(assistantMsg)
    const assistantIdx = messages.value.length - 1

    try {
      const res = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text }),
      })

      if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          if (!line.startsWith('data: ')) continue
          const chunk = line.slice(6)
          if (chunk === '[DONE]') continue

          const msg = messages.value[assistantIdx]
          msg.content += chunk
          msg.nodes = parseMarkdownToStructure(msg.content, md)
        }
      }
    } catch (err) {
      const msg = messages.value[assistantIdx]
      msg.content = `[错误] ${err instanceof Error ? err.message : '请求失败'}`
    } finally {
      const msg = messages.value[assistantIdx]
      msg.streaming = false
      isStreaming.value = false
    }
  }

  return { messages, isStreaming, sendMessage }
}
```

**Step 5: Run tests to verify they pass**

```bash
npm test -- --run
```

Expected: All 4 tests PASS.

**Step 6: Commit**

```bash
git add 07-ChatUI/frontend/src/composables/ 07-ChatUI/frontend/vitest.config.ts
git commit -m "feat(07-chatui): add useChat composable with SSE stream parsing"
```

---

### Task 3: Build the chat UI components

**Files:**
- Create: `07-ChatUI/frontend/src/main.ts`
- Create: `07-ChatUI/frontend/src/App.vue`
- Create: `07-ChatUI/frontend/src/style.css`

**Step 1: Create `src/main.ts`**

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

createApp(App).mount('#app')
```

**Step 2: Create `src/style.css`** (dark theme, minimal)

```css
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --bg: #1a1a1a;
  --surface: #2a2a2a;
  --border: #3a3a3a;
  --text: #e0e0e0;
  --text-muted: #888;
  --accent: #4a9eff;
  --user-bubble: #1e3a5f;
  --radius: 12px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

html, body, #app { height: 100%; }

body {
  background: var(--bg);
  color: var(--text);
  line-height: 1.6;
}

/* scrollbar */
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
```

**Step 3: Create `src/App.vue`**

```vue
<template>
  <div class="chat-layout">
    <!-- Header -->
    <header class="chat-header">
      <span class="chat-title">ChatUI</span>
      <span class="chat-status" :class="{ active: isStreaming }">
        {{ isStreaming ? '生成中...' : '就绪' }}
      </span>
    </header>

    <!-- Message list -->
    <main ref="listRef" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        发送消息开始对话
      </div>

      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="message-row"
        :class="msg.role"
      >
        <div class="message-bubble">
          <!-- User message: plain text -->
          <template v-if="msg.role === 'user'">{{ msg.content }}</template>

          <!-- Assistant message: markstream-vue rendering -->
          <template v-else>
            <MarkdownRender
              :nodes="msg.nodes"
              :final="!msg.streaming"
              :max-live-nodes="msg.streaming ? 0 : 320"
              :is-dark="true"
            />
            <span v-if="msg.streaming" class="cursor-blink">▌</span>
          </template>
        </div>
      </div>
    </main>

    <!-- Input area -->
    <footer class="chat-input-area">
      <textarea
        ref="inputRef"
        v-model="inputText"
        class="chat-input"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        rows="1"
        :disabled="isStreaming"
        @keydown.enter.exact.prevent="handleSend"
        @input="autoResize"
      />
      <button
        class="chat-send-btn"
        :disabled="isStreaming || !inputText.trim()"
        @click="handleSend"
      >
        发送
      </button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import MarkdownRender from 'markstream-vue'
import { useChat } from './composables/useChat'

const { messages, isStreaming, sendMessage } = useChat()
const inputText = ref('')
const listRef = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return
  inputText.value = ''
  resetInputHeight()
  await sendMessage(text)
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function resetInputHeight() {
  if (inputRef.value) inputRef.value.style.height = 'auto'
}

// Auto-scroll to bottom when messages change
watch(messages, async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}, { deep: true })
</script>

<style scoped>
.chat-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}

.chat-title { font-weight: 600; font-size: 16px; }

.chat-status {
  font-size: 12px;
  color: var(--text-muted);
  transition: color 0.2s;
}
.chat-status.active { color: var(--accent); }

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-empty {
  text-align: center;
  color: var(--text-muted);
  margin-top: 40px;
  font-size: 14px;
}

.message-row { display: flex; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }

.message-bubble {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: var(--radius);
  font-size: 14px;
  line-height: 1.7;
  position: relative;
}

.message-row.user .message-bubble {
  background: var(--user-bubble);
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.assistant .message-bubble {
  background: var(--surface);
  border: 1px solid var(--border);
  min-width: 60px;
}

.cursor-blink {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: var(--accent);
  margin-left: 2px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.chat-input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  background: var(--surface);
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 10px 14px;
  color: var(--text);
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  min-height: 42px;
  max-height: 160px;
  transition: border-color 0.2s;
}
.chat-input:focus { border-color: var(--accent); }
.chat-input:disabled { opacity: 0.5; cursor: not-allowed; }

.chat-send-btn {
  padding: 10px 20px;
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: var(--radius);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.2s;
  height: 42px;
}
.chat-send-btn:hover:not(:disabled) { opacity: 0.85; }
.chat-send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
```

**Step 4: Verify dev server starts**

```bash
cd /Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test/07-ChatUI/frontend
npm run dev
```

Expected: Server starts at `http://localhost:5173`, chat UI visible in browser (empty state with "发送消息开始对话").

**Step 5: Commit**

```bash
git add 07-ChatUI/frontend/src/
git commit -m "feat(07-chatui): add App.vue chat UI with MarkdownRender integration"
```

---

### Task 4: Final wiring and README

**Files:**
- Create: `07-ChatUI/frontend/README.md`

**Step 1: Create `README.md`**

```markdown
# ChatUI Frontend

Vue 3 + Vite frontend for the ChatUI demo. Uses [markstream-vue](https://github.com/...) for streaming Markdown rendering.

## Dev

```bash
npm install
npm run dev       # http://localhost:5173  (proxies /api → localhost:8090)
```

## Build

```bash
npm run build     # outputs to ../src/main/resources/static/
```

## Backend contract

```
POST /api/chat/stream
Content-Type: application/json
{ "message": "用户输入" }

Response: text/event-stream
data: chunk1
data: chunk2
data: [DONE]
```

## Test

```bash
npm test
```
```

**Step 2: Run full test suite one more time**

```bash
npm test -- --run
```

Expected: All tests PASS.

**Step 3: Final commit**

```bash
git add 07-ChatUI/frontend/README.md
git commit -m "docs(07-chatui): add frontend README with backend contract"
```
