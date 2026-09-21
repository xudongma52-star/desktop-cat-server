import { apiRequest } from './http'

export interface CarouselPhoto {
  photoId: number
  contentUrl: string
  createdAt: string
}

export function getCarouselPhotos(): Promise<CarouselPhoto[]> {
  return apiRequest<CarouselPhoto[]>('/api/carousel/photos')
}

export function uploadCarouselPhoto(file: Blob): Promise<CarouselPhoto> {
  const form = new FormData()
  form.append('file', file, 'carousel-photo.webp')
  return apiRequest<CarouselPhoto>('/api/carousel/photos', {
    method: 'POST',
    body: form,
  })
}

export function deleteCarouselPhoto(photoId: number): Promise<void> {
  return apiRequest<void>(`/api/carousel/photos/${photoId}`, { method: 'DELETE' })
}
