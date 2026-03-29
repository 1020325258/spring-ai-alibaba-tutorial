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
