import { apiRequest } from './http'
export { ApiError } from './http'

export interface CatProfile {
  profileId: number
  catName: string
  version: number
  updatedAt: string
}

export async function getCatProfile(): Promise<CatProfile> {
  return apiRequest<CatProfile>('/api/cat/profile')
}

export async function updateCatName(profile: CatProfile, catName: string): Promise<CatProfile> {
  return apiRequest<CatProfile>('/api/cat/profile/name', {
    method: 'PATCH',
    body: JSON.stringify({ profileId: profile.profileId, catName, version: profile.version }),
  })
}
