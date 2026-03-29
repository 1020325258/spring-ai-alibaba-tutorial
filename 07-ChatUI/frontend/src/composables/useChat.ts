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
        const lines = buffer.split(/\r?\n/)
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
      // Cancel the reader to free resources (may not exist if fetch failed early)
      if (typeof reader !== 'undefined') {
        await reader.cancel().catch(() => {}) // ignore cancel errors
      }
      msg.streaming = false
      isStreaming.value = false
    }
  }

  return { messages, isStreaming, sendMessage }
}