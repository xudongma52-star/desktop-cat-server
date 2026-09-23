import { ApiError, apiRequest } from './http'

export interface AuthUser {
  userId: number
  username: string
}

export interface AuthStatus {
  authenticated: boolean
  userId: number | null
  username: string | null
}

export interface AuthCredentials {
  username: string
  password: string
}

export function getAuthStatus(): Promise<AuthStatus> {
  return apiRequest<AuthStatus>('/api/auth/status')
}

export function registerUser(credentials: AuthCredentials): Promise<AuthUser> {
  return apiRequest<AuthUser>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

export function loginUser(credentials: AuthCredentials): Promise<AuthUser> {
  return apiRequest<AuthUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

export function logoutUser(): Promise<void> {
  return apiRequest<void>('/api/auth/logout', { method: 'POST' })
}

export function describeAuthError(caught: unknown, fallback: string): string {
  if (!(caught instanceof ApiError)) return fallback
  const messages: Record<string, string> = {
    INVALID_CREDENTIALS: '用户名或密码不对，请再检查一下。',
    USERNAME_ALREADY_EXISTS: '这个用户名已经有人使用了，换一个试试吧。',
    USERNAME_REQUIRED: '请先填写用户名。',
    USERNAME_TOO_SHORT: '用户名至少需要 3 个字符。',
    USERNAME_TOO_LONG: '用户名不能超过 32 个字符。',
    PASSWORD_REQUIRED: '请先填写密码。',
    PASSWORD_TOO_SHORT: '密码至少需要 6 个字符。',
    PASSWORD_TOO_LONG: '密码包含的内容太长了，请稍微精简一些。',
    NETWORK_ERROR: '暂时连接不上小窝，请确认后端已经启动。',
    ACCESS_DENIED: '请求校验已过期，请刷新页面后再试。',
  }
  return messages[caught.code] ?? fallback
}
