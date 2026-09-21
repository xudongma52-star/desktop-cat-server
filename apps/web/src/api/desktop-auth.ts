import { apiRequest } from './http'

export interface DesktopAuthorizationRequest {
  redirectUri: string
  codeChallenge: string
  state: string
  deviceName: string
  platform: string
  appVersion?: string
}

export interface DesktopAuthorization {
  callbackUrl: string
}

export function authorizeDesktop(
  request: DesktopAuthorizationRequest,
): Promise<DesktopAuthorization> {
  return apiRequest<DesktopAuthorization>('/api/auth/desktop/authorize', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
