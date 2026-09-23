import { apiRequest } from './http'

export interface SystemStatus {
  status: string
  application: string
  springBootVersion: string
  javaVersion: string
  timestamp: string
}

export async function getSystemStatus(): Promise<SystemStatus> {
  return apiRequest<SystemStatus>('/api/system/status')
}
