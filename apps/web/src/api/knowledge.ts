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
