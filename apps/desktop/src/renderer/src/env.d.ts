/// <reference types="vite/client" />

import type {
  CatActivityId,
  CatActivityRequestResult,
  CatActivitySnapshot,
} from '../../shared/cat-activity'
import type { CompanionInfo } from '../../shared/companion'
import type { CatProfile } from '../../shared/cat-profile'

declare global {
  interface DesktopCatApi {
    setIgnoreMouseEvents(ignore: boolean): void
    moveWindowBy(deltaX: number, deltaY: number): void
    setMovementPaused(paused: boolean): void
    setActivityPanelOpen(open: boolean): Promise<'left' | 'right'>
    getActivity(): Promise<CatActivitySnapshot>
    getCompanionInfo(): Promise<CompanionInfo>
    getCatProfile(): Promise<CatProfile>
    requestActivity(activityId: CatActivityId): Promise<CatActivityRequestResult>
    onActivityChanged(listener: (snapshot: CatActivitySnapshot) => void): () => void
    onCatProfileChanged(listener: (profile: CatProfile) => void): () => void
  }

  interface Window {
    desktopCat: DesktopCatApi
  }
}

export {}
