import { apiRequest } from './http'

export interface Emotion {
  emotionId: number
  content: string
  recordDate: string
  createdAt: string
}

export function todayInLocalTime(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export async function getEmotions(date = todayInLocalTime()): Promise<Emotion[]> {
  return apiRequest<Emotion[]>(`/api/emotions?date=${encodeURIComponent(date)}`)
}
