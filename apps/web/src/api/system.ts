export interface SystemStatus {
  status: string
  application: string
  springBootVersion: string
  javaVersion: string
  timestamp: string
}

export async function getSystemStatus(): Promise<SystemStatus> {
  const response = await fetch('/api/system/status', {
    signal: AbortSignal.timeout(8000),
  })
  if (!response.ok) {
    throw new Error(`Backend request failed (HTTP ${response.status})`)
  }
  return response.json() as Promise<SystemStatus>
}
