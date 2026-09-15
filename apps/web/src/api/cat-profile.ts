export interface CatProfile {
  profileId: number
  catName: string
  version: number
  updatedAt: string
}

interface ApiErrorResponse {
  code: string
  message: string
  requestId: string
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly requestId: string,
  ) {
    super(message)
  }
}

async function readResponse(response: Response): Promise<CatProfile> {
  if (response.ok) return response.json() as Promise<CatProfile>
  const fallback: ApiErrorResponse = {
    code: 'REQUEST_FAILED',
    message: 'Request failed.',
    requestId: response.headers.get('X-Request-Id') ?? 'unknown',
  }
  const error = await response.json().catch(() => fallback) as ApiErrorResponse
  throw new ApiError(error.message, error.code, error.requestId)
}

export async function getCatProfile(): Promise<CatProfile> {
  return readResponse(await fetch('/api/cat/profile'))
}

export async function updateCatName(profile: CatProfile, catName: string): Promise<CatProfile> {
  return readResponse(await fetch('/api/cat/profile/name', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ profileId: profile.profileId, catName, version: profile.version }),
  }))
}
