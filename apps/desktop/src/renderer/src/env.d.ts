/// <reference types="vite/client" />

import type {
  CatActivityId,
  CatActivityRequestResult,
  CatActivitySnapshot,
} from '../../shared/cat-activity'
import type { CompanionInfo } from '../../shared/companion'
import type { CatProfile } from '../../shared/cat-profile'
import type { Emotion } from '../../shared/emotion'
import type { Reminder } from '../../shared/reminder'

declare global {
  interface DesktopCatApi {
    setIgnoreMouseEvents(ignore: boolean): void
    moveWindowBy(deltaX: number, deltaY: number): void
    setMovementPaused(paused: boolean): void
    setActivityPanelOpen(open: boolean): Promise<'left' | 'right'>
    getActivity(): Promise<CatActivitySnapshot>
    getCompanionInfo(): Promise<CompanionInfo>
    getCatProfile(): Promise<CatProfile>
    createEmotion(content: string): Promise<Emotion>
    completeReminder(reminderId: number, version: number): Promise<Reminder>
    requestActivity(activityId: CatActivityId): Promise<CatActivityRequestResult>
    onActivityChanged(listener: (snapshot: CatActivitySnapshot) => void): () => void
    onCatProfileChanged(listener: (profile: CatProfile) => void): () => void
    onReminderDue(listener: (reminder: Reminder) => void): () => void
  }

  interface Window {
    desktopCat: DesktopCatApi
  }
}

export {}
