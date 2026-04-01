<template>
  <div class="app-container">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="sidebar-logo">AI</div>
        <span class="sidebar-title">Chat</span>
      </div>
      <nav class="sidebar-nav">
        <button class="nav-item active">
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
          <span>New Chat</span>
        </button>
      </nav>
    </aside>

    <!-- 聊天区域 -->
    <div ref="listRef" class="chat-area">
      <div class="chat-content">
        <!-- Agent 消息气泡直接显示在页面 -->
        <template v-for="(msg, i) in messages" :key="i">
          <div v-if="msg.role === 'assistant'" class="message-row assistant">
            <div class="message-bubble">
              <!-- Thinking blocks: collapsible step cards -->
              <div v-if="msg.thinkingBlocks?.length" class="thinking-steps">
                <details v-for="block in msg.thinkingBlocks" :key="block.nodeName + block.stepTitle" class="thinking-step">
                  <summary class="thinking-step-header">
                    <span class="step-badge" :class="{ 'badge-router': block.nodeName === 'router' }">
                      {{ block.displayTitle || '步骤' }}
                    </span>
                    <span class="step-title">{{ block.stepTitle }}</span>
                    <span v-if="block.duration > 0" class="step-meta">{{ block.duration }}ms</span>
                    <span v-if="block.toolName" :class="['step-status', block.success ? 'success' : 'failure']">
                      {{ block.success ? '✓' : '✗' }}
                    </span>
                  </summary>
                  <div class="thinking-step-body">
                    <div v-if="block.toolName" class="step-tool">工具: <code>{{ block.toolName }}</code></div>
                    <div v-if="Object.keys(block.params).length" class="step-params">
                      <div>参数:</div>
                      <ul>
                        <li v-for="(v, k) in block.params" :key="k"><code>{{ k }}</code>: {{ v }}</li>
                      </ul>
                    </div>
                    <div v-if="block.resultSummary" class="step-result">结果: {{ block.resultSummary }}</div>
                    <div v-if="block.resultData" class="step-data">
                      <div>数据:</div>
                      <MarkdownRender
                        :content="'```json\n' + JSON.stringify(block.resultData, null, 2) + '\n```'"
                        :is-dark="false"
                        :final="true"
                        :max-live-nodes="320"
                        :themes="['vitesse-dark', 'vitesse-light']"
                        custom-id="chatui-thinking"
                        class="step-data-md"
                      />
                    </div>
                    <div v-if="block.errorMessage" class="step-error">错误: {{ block.errorMessage }}</div>
                  </div>
                </details>
              </div>

              <!-- Conclusion (JSON event) or regular streaming markdown -->
              <MarkdownRender
                v-if="msg.conclusion"
                :content="msg.conclusion"
                :final="!msg.streaming"
                :max-live-nodes="msg.streaming ? MAX_LIVE_NODES_STREAMING : MAX_LIVE_NODES_DONE"
                :is-dark="false"
                :code-block-props="{ showCopyButton: true }"
                :themes="['vitesse-dark', 'vitesse-light']"
                custom-id="chatui"
              />
              <MarkdownRender
                v-else
                :nodes="msg.nodes"
                :final="!msg.streaming"
                :max-live-nodes="msg.streaming ? MAX_LIVE_NODES_STREAMING : MAX_LIVE_NODES_DONE"
                :is-dark="false"
                :code-block-props="{ showCopyButton: true }"
                :themes="['vitesse-dark', 'vitesse-light']"
                custom-id="chatui"
                :custom-html-tags="['thinking']"
              />
              <span v-if="msg.streaming" class="cursor-blink">▌</span>
            </div>
          </div>
          <div v-else class="message-row user">
            <div class="message-bubble">{{ msg.content }}</div>
          </div>
        </template>

        <!-- 空状态提示 -->
        <div v-if="messages.length === 0" class="chat-empty">
          Send a message to start the conversation
        </div>
      </div>

      <!-- 输入区域 - 固定在底部 -->
      <div class="chat-input-area">
        <div class="input-wrapper">
          <textarea
            ref="inputRef"
            v-model="inputText"
            class="chat-input"
            placeholder="Type a message... (Enter to send, Shift+Enter for new line)"
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
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import MarkdownRender from 'markstream-vue'
import { useChat } from './composables/useChat'

const MAX_LIVE_NODES_STREAMING = 0
const MAX_LIVE_NODES_DONE = 320

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
watch(() => messages.value.length, async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
})

// Scroll listener for chat content
watch(() => messages.value.length, async () => {
  await nextTick()
  const chatContent = listRef.value?.querySelector('.chat-content')
  if (chatContent) {
    (chatContent as HTMLElement).scrollTop = (chatContent as HTMLElement).scrollHeight
  }
})
</script>

<style scoped>
/* 主容器 - 左右布局 */
.app-container {
  display: flex;
  min-height: 100vh;
  background: #f3f4f6;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  background: #1f2937;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #374151;
}

.sidebar-logo {
  width: 2rem;
  height: 2rem;
  background: linear-gradient(to bottom right, #3b82f6, #a855f7);
  border-radius: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 0.75rem;
}

.sidebar-title {
  color: #f3f4f6;
  font-weight: 600;
  font-size: 1rem;
}

.sidebar-nav {
  padding: 0.75rem;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.75rem 1rem;
  background: transparent;
  border: none;
  border-radius: 0.5rem;
  color: #9ca3af;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.nav-item:hover {
  background: #374151;
  color: #f3f4f6;
}

.nav-item.active {
  background: #374151;
  color: #f3f4f6;
}

/* 聊天区域 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 2rem 1rem;
  max-width: 64rem;
  margin: 0 auto;
  width: 100%;
}

/* 消息行 */
.message-row {
  display: flex;
  padding: 0.5rem 0;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

/* 消息气泡 */
.message-bubble {
  padding: 0.875rem 1.25rem;
  border-radius: 1rem;
  font-size: 0.9375rem;
  line-height: 1.6;
  position: relative;
  max-width: 85%;
}

/* 用户消息 - 蓝色填充，靠右 */
.message-row.user .message-bubble {
  background: #3b82f6;
  color: #fff;
  border-bottom-right-radius: 0.25rem;
}

/* AI消息 - 铺满宽度 */
.message-row.assistant .message-bubble {
  width: 100%;
  max-width: 100%;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-bottom-left-radius: 0.25rem;
}

/* 空状态提示 */
.chat-empty {
  text-align: center;
  color: #9ca3af;
  margin-top: 2.5rem;
  font-size: 0.875rem;
}

/* 光标闪烁 */
.cursor-blink {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: #3b82f6;
  margin-left: 2px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 输入区域 - 固定在底部 */
.chat-input-area {
  padding: 1rem 2rem;
  background: #f3f4f6;
  flex-shrink: 0;
  max-width: 64rem;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

/* 输入框包装 */
.input-wrapper {
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 1.5rem;
  padding: 0.75rem 1rem;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-wrapper:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* 输入框 */
.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  padding: 0.5rem;
  color: #1f2937;
  font-size: 0.9375rem;
  font-family: inherit;
  resize: none;
  outline: none;
  min-height: 1.5rem;
  max-height: 10rem;
  line-height: 1.5;
}

.chat-input::placeholder {
  color: #9ca3af;
}

.chat-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 发送按钮 */
.chat-send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.2s, transform 0.1s;
  flex-shrink: 0;
}

.chat-send-btn:hover:not(:disabled) {
  background: #2563eb;
}

.chat-send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.chat-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 思考步骤 */
.thinking-steps {
  margin-bottom: 0.75rem;
}

.thinking-step {
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  margin-bottom: 0.25rem;
  background: #fff;
  overflow: hidden;
}

.thinking-step-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  font-size: 0.875rem;
  color: #374151;
  list-style: none;
  user-select: none;
}

.thinking-step-header::-webkit-details-marker {
  display: none;
}

.thinking-step-header::before {
  content: '▶';
  font-size: 0.625rem;
  color: #9ca3af;
  transition: transform 0.2s;
  flex-shrink: 0;
}

details[open] .thinking-step-header::before {
  transform: rotate(90deg);
}

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.125rem 0.375rem;
  background: #3b82f6;
  color: #fff;
  border-radius: 0.25rem;
  font-size: 0.6875rem;
  font-weight: 600;
  flex-shrink: 0;
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

.badge-router {
  background: #6b7280;
}

.step-title {
  flex: 1;
  font-weight: 500;
}

.step-meta {
  color: #9ca3af;
  font-size: 0.8125rem;
}

.step-status {
  font-size: 0.875rem;
  font-weight: 600;
}

.step-status.success {
  color: #10b981;
}

.step-status.failure {
  color: #ef4444;
}

.thinking-step-body {
  padding: 0.5rem 0.75rem 0.75rem 2.25rem;
  font-size: 0.8125rem;
  color: #6b7280;
  border-top: 1px solid #f3f4f6;
}

.step-tool,
.step-params,
.step-result,
.step-data {
  margin-bottom: 0.25rem;
}

.step-data-content {
  background: #f3f4f6;
  padding: 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  overflow-x: auto;
  max-height: 200px;
  margin-top: 0.25rem;
}

.step-data-md {
  margin-top: 0.25rem;
}

.step-data-md :deep(.code-block-container) {
  max-height: 200px;
  overflow: auto;
}

.step-params ul {
  margin: 0.25rem 0 0 1rem;
  padding: 0;
}

.step-params li {
  list-style: disc;
  margin-bottom: 0.125rem;
}

/* 滚动条样式 */
.chat-area::-webkit-scrollbar {
  width: 8px;
}

.chat-area::-webkit-scrollbar-track {
  background: transparent;
}

.chat-area::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 4px;
}

.chat-area::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
</style>