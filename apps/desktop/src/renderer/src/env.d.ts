/// <reference types="vite/client" />

import type {
  CatActivityId,
  CatActivityRequestResult,
  CatActivitySnapshot,
} from '../../shared/cat-activity'
import type { CompanionInfo } from '../../shared/companion'

declare global {
  interface DesktopCatApi {
    setIgnoreMouseEvents(ignore: boolean): void
    moveWindowBy(deltaX: number, deltaY: number): void
    setMovementPaused(paused: boolean): void
    setActivityPanelOpen(open: boolean): Promise<'left' | 'right'>
    getActivity(): Promise<CatActivitySnapshot>
    getCompanionInfo(): Promise<CompanionInfo>
    requestActivity(activityId: CatActivityId): Promise<CatActivityRequestResult>
    onActivityChanged(listener: (snapshot: CatActivitySnapshot) => void): () => void
  }

  interface Window {
    desktopCat: DesktopCatApi
  }
}

export {}
