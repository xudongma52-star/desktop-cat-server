export interface ApiFieldError {
  field?: string
  message: string
}

export interface ApiErrorResponse {
  code?: string
  message?: string
  requestId?: string
  details?: ApiFieldError[]
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly requestId: string,
    readonly status: number,
    readonly details: ApiFieldError[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers,
      signal: init.signal ?? AbortSignal.timeout(8000),
    })
  } catch (cause) {
    throw new ApiError('暂时无法连接到服务，请稍后再试。', 'NETWORK_ERROR', 'unknown', 0, [])
  }

  if (response.ok) {
    if (response.status === 204) return undefined as T
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }

  const fallbackRequestId = response.headers.get('X-Request-Id') ?? 'unknown'
  let payload: ApiErrorResponse = {}
  try {
    payload = await response.json() as ApiErrorResponse
  } catch {
    // 非 JSON 错误也统一转换，页面无需理解底层响应格式。
  }

  throw new ApiError(
    payload.message || `请求失败（HTTP ${response.status}）。`,
    payload.code || 'REQUEST_FAILED',
    payload.requestId || fallbackRequestId,
    response.status,
    payload.details || [],
  )
}

export function describeApiError(caught: unknown, fallback: string): string {
  if (!(caught instanceof ApiError)) return fallback
  const requestSuffix = caught.requestId && caught.requestId !== 'unknown'
    ? `（请求编号：${caught.requestId}）`
    : ''
  return `${caught.message}${requestSuffix}`
}
