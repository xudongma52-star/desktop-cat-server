export type CaptureSaveStatus = 'saved' | 'pending'

export interface CaptureImageInput {
  name: string
  type: string
  bytes: Uint8Array
}

export interface PendingCaptureImage {
  name: string
  type: string
  base64: string
}

export interface PendingCapture {
  captureId: string
  userId: number
  content: string
  capturedAt: string
  image?: PendingCaptureImage
}
