import { apiRequest } from './http'

export interface CaptureItem {
  captureId: string
  content: string
  imageUrl: string | null
  classificationTarget: 'RECORD' | 'EMOTION' | null
  classificationOrigin: 'AI' | 'USER' | null
  recordResolution: 'DIRECT' | 'AI_ARTICLE' | null
  capturedAt: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface CaptureDailyPage {
  date: string | null
  items: CaptureItem[]
  page: number
  totalDays: number
}

export interface CaptureArticleDraft {
  title: string
  content: string
  recordDate: string
  captureIds: string[]
}

export interface CapturePage {
  items: CaptureItem[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface CaptureInput {
  captureId: string
  content: string
  capturedAt: string
}

export function createCapture(input: CaptureInput): Promise<CaptureItem> {
  return apiRequest<CaptureItem>('/api/captures', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function createCaptureWithImage(input: CaptureInput, image: File): Promise<CaptureItem> {
  const body = new FormData()
  body.append('capture', new Blob([JSON.stringify(input)], { type: 'application/json' }))
  body.append('image', image)
  return apiRequest<CaptureItem>('/api/captures', {
    method: 'POST', body, signal: AbortSignal.timeout(120_000),
  })
}

export function getCaptures(page = 1, q = ''): Promise<CapturePage> {
  const params = new URLSearchParams({ page: String(page), pageSize: '20' })
  if (q) params.set('q', q)
  return apiRequest<CapturePage>(`/api/captures?${params}`)
}

export function getDailyCaptures(page = 1): Promise<CaptureDailyPage> {
  return apiRequest<CaptureDailyPage>(`/api/captures/daily?page=${page}`)
}

export function classifyCaptureDay(date: string): Promise<{ classifiedCount: number }> {
  return apiRequest<{ classifiedCount: number }>('/api/captures/classify-day', {
    method: 'POST',
    body: JSON.stringify({ date }),
    signal: AbortSignal.timeout(210_000),
  })
}

export function getEmotionCaptures(date: string): Promise<CaptureItem[]> {
  return apiRequest<CaptureItem[]>(`/api/captures/emotions?date=${encodeURIComponent(date)}`)
}

export function saveCaptureDirectly(
  captureId: string,
  ragEnabled: boolean,
  content?: string,
): Promise<{ recordId: number }> {
  return apiRequest<{ recordId: number }>(`/api/captures/${captureId}/record`, {
    method: 'POST',
    body: JSON.stringify({ ragEnabled, content }),
  })
}

export function generateCaptureArticle(captureIds: string[]): Promise<CaptureArticleDraft> {
  return apiRequest<CaptureArticleDraft>('/api/captures/article-draft', {
    method: 'POST',
    body: JSON.stringify({ captureIds }),
    signal: AbortSignal.timeout(210_000),
  })
}

export function saveCaptureArticle(input: CaptureArticleDraft & { ragEnabled: boolean }): Promise<{ recordId: number }> {
  return apiRequest<{ recordId: number }>('/api/captures/article', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function getRecordSources(recordId: number): Promise<CaptureItem[]> {
  return apiRequest<CaptureItem[]>(`/api/records/${recordId}/sources`)
}

export function updateCapture(item: CaptureItem, content: string): Promise<CaptureItem> {
  return apiRequest<CaptureItem>(`/api/captures/${item.captureId}`, {
    method: 'PUT',
    body: JSON.stringify({ content, version: item.version }),
  })
}

export function deleteCapture(item: CaptureItem): Promise<void> {
  return apiRequest<void>(`/api/captures/${item.captureId}?version=${item.version}`, {
    method: 'DELETE',
  })
}
