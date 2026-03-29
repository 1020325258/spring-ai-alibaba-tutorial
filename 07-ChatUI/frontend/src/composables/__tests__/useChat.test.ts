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