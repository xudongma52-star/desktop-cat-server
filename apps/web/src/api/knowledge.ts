import { apiRequest } from './http'
import type { RecordType } from './records'

export interface KnowledgeMatch {
  recordId: number
  recordType: RecordType
  title: string | null
  content: string
  recordDate: string
  score: number
}

export interface KnowledgeSearchResult {
  searchableRecordCount: number
  candidateLimitReached: boolean
  answer: string | null
  answerGenerated: boolean
  matches: KnowledgeMatch[]
}

export interface KnowledgeChatSummary {
  chatId: number
  title: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeMessage {
  messageId: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources: KnowledgeMatch[]
  createdAt: string
}

export interface KnowledgeMessagePage {
  items: KnowledgeMessage[]
  hasMore: boolean
  nextBeforeMessageId: number | null
}

export interface KnowledgeChatResponse {
  chat: KnowledgeChatSummary
  userMessage: KnowledgeMessage
  assistantMessage: KnowledgeMessage
}

export async function retrieveKnowledge(question: string): Promise<KnowledgeSearchResult> {
  const timeoutController = new AbortController()
  const timeoutId = window.setTimeout(() => timeoutController.abort(), 130_000)

  try {
    return apiRequest<KnowledgeSearchResult>('/api/knowledge/retrieve', {
      method: 'POST',
      body: JSON.stringify({ question }),
      signal: timeoutController.signal,
    })
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export function listKnowledgeChats(): Promise<KnowledgeChatSummary[]> {
  return apiRequest<KnowledgeChatSummary[]>('/api/knowledge/chats')
}

export function renameKnowledgeChat(chatId: number, title: string): Promise<KnowledgeChatSummary> {
  return apiRequest<KnowledgeChatSummary>(`/api/knowledge/chats/${chatId}/title`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  })
}

export function deleteKnowledgeChat(chatId: number): Promise<void> {
  return apiRequest<void>(`/api/knowledge/chats/${chatId}`, { method: 'DELETE' })
}

export function listKnowledgeMessages(
  chatId: number,
  beforeMessageId?: number | null,
): Promise<KnowledgeMessagePage> {
  const query = beforeMessageId ? `?beforeMessageId=${beforeMessageId}` : ''
  return apiRequest<KnowledgeMessagePage>(`/api/knowledge/chats/${chatId}/messages${query}`)
}

export async function sendKnowledgeChat(
  chatId: number | null,
  question: string,
): Promise<KnowledgeChatResponse> {
  const timeoutController = new AbortController()
  // 同步压缩通常增加一次模型调用；较旧对话首次恢复可能需要分批补齐摘要。
  const timeoutId = window.setTimeout(() => timeoutController.abort(), 190_000)

  try {
    return apiRequest<KnowledgeChatResponse>('/api/knowledge/chat', {
      method: 'POST',
      body: JSON.stringify({ chatId, question }),
      signal: timeoutController.signal,
    })
  } finally {
    window.clearTimeout(timeoutId)
  }
}
