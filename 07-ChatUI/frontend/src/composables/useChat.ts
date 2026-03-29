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
      let chunkCount = 0

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          // 兼容 data: 和 data: 两种格式
          if (!line.startsWith('data:')) continue
          const chunk = line.startsWith('data: ')
            ? line.slice(6)
            : line.slice(5)
          if (chunk === '[DONE]') continue
          if (!chunk) continue

          // 替换整个消息对象以触发 Vue 响应式更新
          const msg = messages.value[assistantIdx]
          const newContent = msg.content + chunk + '\n'
          messages.value[assistantIdx] = {
            ...msg,
            content: newContent,
          }
          chunkCount++
          if (chunkCount % 5 === 0) {
            const updatedMsg = messages.value[assistantIdx]
            const nodes = parseMarkdownToStructure(updatedMsg.content, md)
            messages.value[assistantIdx] = {
              ...updatedMsg,
              nodes,
            }
          }
        }
      }

      // Final parse to ensure all content is parsed
      const msg = messages.value[assistantIdx]
      console.log('[sendMessage] 最终content:', JSON.stringify(msg.content).slice(0, 300))
      const finalNodes = parseMarkdownToStructure(msg.content, md)
      // 统计各节点类型
      const typeCount: Record<string, number> = {}
      finalNodes.forEach(n => { typeCount[n.type] = (typeCount[n.type] || 0) + 1 })
      console.log('[sendMessage] 最终nodes类型统计:', typeCount)
      console.log('[sendMessage] 最终nodes:', JSON.stringify(finalNodes).slice(0, 500))
      messages.value[assistantIdx] = {
        ...msg,
        nodes: finalNodes,
      }
    } catch (err) {
      console.error('[sendMessage] 错误:', err)
      const msg = messages.value[assistantIdx]
      messages.value[assistantIdx] = {
        ...msg,
        content: `[错误] ${err instanceof Error ? err.message : '请求失败'}`,
      }
    } finally {
      const msg = messages.value[assistantIdx]
      if (typeof reader !== 'undefined') {
        await reader.cancel().catch(() => {})
      }
      messages.value[assistantIdx] = {
        ...msg,
        streaming: false,
      }
      isStreaming.value = false
    }
  }

  return { messages, isStreaming, sendMessage }
}