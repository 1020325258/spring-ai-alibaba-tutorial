import { ref } from 'vue'
import { getMarkdown, parseMarkdownToStructure } from 'markstream-vue'
import type { BaseNode } from 'markstream-vue'

export interface Message {
  role: 'user' | 'assistant'
  content: string
  nodes: BaseNode[]
  streaming: boolean
  thinkingBlocks?: ThinkingBlock[]
  conclusion?: string
}

export interface ThinkingBlock {
  stepNumber: number
  title: string
  toolName: string
  params: Record<string, string>
  summary: string
  duration: number
  success: boolean
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
      let pendingContent = ''

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

          // 尝试解析 JSON 格式事件
          try {
            const jsonData = JSON.parse(chunk)
            if (jsonData.type === 'thinking') {
              // 处理 thinking 类型事件
              const msg = messages.value[assistantIdx]
              const thinkingBlocks = msg.thinkingBlocks || []
              thinkingBlocks.push(parseThinkingContent(jsonData.content))
              messages.value[assistantIdx] = {
                ...msg,
                thinkingBlocks,
              }
              continue
            } else if (jsonData.type === 'conclusion') {
              // 处理 conclusion 类型事件
              const msg = messages.value[assistantIdx]
              messages.value[assistantIdx] = {
                ...msg,
                conclusion: (msg.conclusion || '') + jsonData.content,
              }
              continue
            }
          } catch {
            // 不是 JSON 格式，作为普通文本处理
          }

          // 累积内容：每个 SSE data: 行对应原始内容的一行，必须加 \n 还原换行
          // 空 chunk（来自原始内容中的空行）也要保留为 \n
          pendingContent += chunk + '\n'

          // 每累积 50 个字符或收到完整段落标记时解析一次
          if (pendingContent.length >= 50 || pendingContent.endsWith('\n\n') || pendingContent.endsWith('```\n')) {
            const msg = messages.value[assistantIdx]
            messages.value[assistantIdx] = {
              ...msg,
              content: msg.content + pendingContent,
            }
            pendingContent = ''

            // 减少解析频率，每 10 次累积再解析一次 nodes
            chunkCount++
            if (chunkCount % 10 === 0) {
              const updatedMsg = messages.value[assistantIdx]
              const nodes = parseMarkdownToStructure(updatedMsg.content, md)
              messages.value[assistantIdx] = {
                ...updatedMsg,
                nodes,
              }
            }
          }
        }
      }

      // 处理剩余的 pending content
      if (pendingContent) {
        const msg = messages.value[assistantIdx]
        messages.value[assistantIdx] = {
          ...msg,
          content: msg.content + pendingContent,
        }
      }

      // Final parse to ensure all content is parsed
      const msg = messages.value[assistantIdx]
      const finalContent = prettyPrintJsonBlocks(msg.content)
      if (finalContent !== msg.content) {
        messages.value[assistantIdx] = { ...msg, content: finalContent }
      }
      console.log('[sendMessage] 最终content:', JSON.stringify(finalContent).slice(0, 300))
      const finalNodes = parseMarkdownToStructure(finalContent, md)
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

/**
 * 将 markdown 中所有 ```json 代码块的内容 pretty-print
 */
function prettyPrintJsonBlocks(content: string): string {
  return content.replace(/```json\n([\s\S]*?)```/g, (match, jsonStr) => {
    try {
      const parsed = JSON.parse(jsonStr.trim())
      return '```json\n' + JSON.stringify(parsed, null, 2) + '\n```'
    } catch {
      return match
    }
  })
}

/**
 * 解析 thinking 内容为 ThinkingBlock
 * 格式：**步骤N - 标题**\n> 工具：`xxx`\n> 参数：\n> - key: `value`\n> 结果：x 条记录\n> 耗时：xms | 成功：✓
 */
function parseThinkingContent(content: string): ThinkingBlock {
  const block: ThinkingBlock = {
    stepNumber: 0,
    title: '',
    toolName: '',
    params: {},
    summary: '',
    duration: 0,
    success: true,
  }

  // 解析步骤号和标题：**步骤1 - 查询订单**
  const stepMatch = content.match(/\*\*步骤(\d+) - (.+?)\*\*/)
  if (stepMatch) {
    block.stepNumber = parseInt(stepMatch[1])
    block.title = stepMatch[2]
  }

  // 解析工具名：> 工具：`ontologyQuery`
  const toolMatch = content.match(/> 工具：`(.+?)`/)
  if (toolMatch) {
    block.toolName = toolMatch[1]
  }

  // 解析参数：> - key: `value`
  const paramMatches = content.matchAll(/> - (.+?): `([^`]+)`/g)
  for (const match of paramMatches) {
    block.params[match[1]] = match[2]
  }

  // 解析结果摘要：> 结果：xxx
  const resultMatch = content.match(/> 结果：(.+)/)
  if (resultMatch) {
    block.summary = resultMatch[1]
  }

  // 解析耗时和状态：> 耗时：100ms | 成功：✓
  const statusMatch = content.match(/> 耗时：(\d+)ms \| 成功：([✓✗])/)
  if (statusMatch) {
    block.duration = parseInt(statusMatch[1])
    block.success = statusMatch[2] === '✓'
  }

  // 非结构化内容（如路由器消息）：将整个内容去除 Markdown 符号后作为标题
  if (!block.title) {
    block.title = content.replace(/\*\*/g, '').trim().split('\n')[0]
  }

  return block
}