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
  nodeName: string
  displayTitle: string
  stepTitle: string
  toolName: string
  params: Record<string, string>
  resultSummary: string
  recordCount?: number
  resultData?: unknown
  duration: number
  success: boolean
  errorMessage?: string
}

/**
 * 查找指定 nodeName 的节点
 */
function findNode<T>(jsonArray: T[], nodeName: string): T | undefined {
  return jsonArray.find((item: any) => item.nodeName === nodeName)
}

/**
 * 解析结构化事件（基于 nodeName）
 */
function parseStructuredEvent(jsonData: any, msg: Message): { thinkingBlocks?: ThinkingBlock[], conclusion?: string } | null {
  const nodeName = jsonData.nodeName
  if (!nodeName) return null

  switch (nodeName) {
    case 'router':
      // 路由决策事件
      return {
        thinkingBlocks: [{
          nodeName: 'router',
          displayTitle: jsonData.displayTitle || '意图识别',
          stepTitle: jsonData.stepTitle || '路由决策',
          toolName: jsonData.toolName || 'router',
          params: jsonData.params || {},
          paramsDescription: jsonData.paramsDescription || {},
          resultSummary: jsonData.resultSummary || '路由成功',
          duration: jsonData.duration ?? 0,
          success: jsonData.success ?? true,
        }]
      }

    case 'tool_call':
      // 工具调用事件
      return {
        thinkingBlocks: [{
          nodeName: 'tool_call',
          displayTitle: jsonData.displayTitle || '工具调用',
          stepTitle: jsonData.stepTitle || jsonData.toolName || '',
          toolName: jsonData.toolName || '',
          params: jsonData.params || {},
          paramsDescription: jsonData.paramsDescription || {},
          resultSummary: jsonData.resultSummary || '',
          recordCount: jsonData.recordCount,
          resultData: jsonData.resultData,
          duration: jsonData.duration ?? 0,
          success: jsonData.success ?? true,
          errorMessage: jsonData.errorMessage,
        }]
      }

    case 'queryAgent':
      // 数据查询结论事件
      return {
        conclusion: jsonData.content || ''
      }

    case 'investigateAgent':
    case 'admin':
      // 问题排查/智能推荐 - 作为普通文本处理
      return {
        conclusion: jsonData.content || ''
      }

    default:
      return null
  }
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

          // 尝试解析 JSON 格式事件（基于 nodeName）
          try {
            const jsonData = JSON.parse(chunk)
            if (jsonData.nodeName) {
              const result = parseStructuredEvent(jsonData, messages.value[assistantIdx])
              if (result) {
                const msg = messages.value[assistantIdx]
                if (result.thinkingBlocks) {
                  const thinkingBlocks = [...(msg.thinkingBlocks || []), ...result.thinkingBlocks]
                  messages.value[assistantIdx] = { ...msg, thinkingBlocks }
                }
                if (result.conclusion !== undefined) {
                  messages.value[assistantIdx] = {
                    ...msg,
                    conclusion: (msg.conclusion || '') + result.conclusion,
                  }
                }
                continue
              }
            }
          } catch {
            // 不是 JSON 格式，作为普通文本处理
          }

          // 累积内容：每个 SSE data: 行对应原始内容的一行，必须加 \n 还原换行
          // 空 chunk（来自原始内容中的空行）也要保留为 \n
          pendingContent += chunk + '\n'

          // 立即更新 UI（真正的流式输出）
          const msg = messages.value[assistantIdx]
          messages.value[assistantIdx] = {
            ...msg,
            content: msg.content + pendingContent,
          }
          pendingContent = ''

          // 减少解析频率，每 5 次更新再解析一次 nodes（避免频繁解析影响性能）
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