import { apiRequest } from './http'

export interface CaptureItem {
  captureId: string
  content: string
  imageUrl: string | null
  capturedAt: string
  version: number
  createdAt: string
  updatedAt: string
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
