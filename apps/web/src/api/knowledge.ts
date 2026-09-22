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
  matches: KnowledgeMatch[]
}

export async function retrieveKnowledge(question: string): Promise<KnowledgeSearchResult> {
  return apiRequest<KnowledgeSearchResult>('/api/knowledge/retrieve', {
    method: 'POST',
    body: JSON.stringify({ question }),
  })
}
