import { apiRequest } from './http'

export type RecordType = 'DIARY' | 'THOUGHT' | 'WORK_NOTE'

export interface PersonalRecord {
  recordId: number
  recordType: RecordType
  title: string | null
  content: string
  recordDate: string
  mood: string | null
  recallEnabled: boolean
  ragEnabled: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface RecordPage {
  items: RecordListItem[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface RecordListItem {
  recordId: number
  recordType: RecordType
  title: string | null
  content?: string
  excerpt?: string
  recordDate: string
  mood: string | null
  recallEnabled: boolean
  ragEnabled: boolean
  version: number
  createdAt?: string
  updatedAt?: string
}

export interface RecallRecord {
  recordId: number
  recordType: RecordType
  title: string | null
  excerpt: string
  recordDate: string
  mood: string | null
}

export interface RecordActivityDay {
  recordDate: string
  recordCount: number
}

export interface RecordActivity {
  startDate: string
  endDate: string
  totalRecords: number
  activeDays: number
  days: RecordActivityDay[]
}

export interface RecordInput {
  recordType: RecordType
  title: string | null
  content: string
  recordDate: string
  mood: string | null
  recallEnabled: boolean
  ragEnabled: boolean
}

export interface UpdateRecordInput extends RecordInput {
  version: number
}

export async function createRecord(input: RecordInput): Promise<PersonalRecord> {
  return apiRequest<PersonalRecord>('/api/records', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function getRecords(page = 1, pageSize = 12, recordType = ''): Promise<RecordPage> {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (recordType) params.set('recordType', recordType)
  return apiRequest<RecordPage>(`/api/records?${params.toString()}`)
}

export async function getRecord(recordId: number): Promise<PersonalRecord> {
  return apiRequest<PersonalRecord>(`/api/records/${recordId}`)
}

export async function updateRecord(recordId: number, input: UpdateRecordInput): Promise<PersonalRecord> {
  return apiRequest<PersonalRecord>(`/api/records/${recordId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export async function deleteRecord(recordId: number, version: number): Promise<void> {
  return apiRequest<void>(`/api/records/${recordId}?version=${version}`, { method: 'DELETE' })
}

export async function getRecallRecords(limit = 10): Promise<RecallRecord[]> {
  return apiRequest<RecallRecord[]>(`/api/records/recalls?limit=${limit}`)
}

export async function getRecordActivity(
  startDate: string,
  endDate: string,
  recordType: RecordType = 'DIARY',
): Promise<RecordActivity> {
  const params = new URLSearchParams({ startDate, endDate, recordType })
  return apiRequest<RecordActivity>(`/api/records/activity?${params.toString()}`)
}

export const recordTypeLabels: Record<RecordType, string> = {
  DIARY: '日记',
  THOUGHT: '心得',
  WORK_NOTE: '实习笔记',
}

export function formatRecordDate(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return value
  const [, year, month, day] = match
  return `${year}年${Number(month)}月${Number(day)}日`
}

export function todayInLocalTime(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
