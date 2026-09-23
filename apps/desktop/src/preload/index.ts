import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'
import type {
  CatActivityId,
  CatActivityRequestResult,
  CatActivitySnapshot,
} from '../shared/cat-activity'
import type { CompanionInfo } from '../shared/companion'
import type { CatProfile } from '../shared/cat-profile'
import type { Emotion } from '../shared/emotion'
import type { Reminder } from '../shared/reminder'

const desktopCatApi = {
  setIgnoreMouseEvents(ignore: boolean): void {
    ipcRenderer.send('desktop-cat:set-ignore-mouse-events', ignore)
  },
  moveWindowBy(deltaX: number, deltaY: number): void {
    ipcRenderer.send('desktop-cat:move-window-by', deltaX, deltaY)
  },
  setMovementPaused(paused: boolean): void {
    ipcRenderer.send('desktop-cat:set-movement-paused', paused)
  },
  setActivityPanelOpen(open: boolean): Promise<'left' | 'right'> {
    return ipcRenderer.invoke('desktop-cat:set-activity-panel-open', open) as Promise<'left' | 'right'>
  },
  getActivity(): Promise<CatActivitySnapshot> {
    return ipcRenderer.invoke('desktop-cat:get-activity') as Promise<CatActivitySnapshot>
  },
  getCompanionInfo(): Promise<CompanionInfo> {
    return ipcRenderer.invoke('desktop-cat:get-companion-info') as Promise<CompanionInfo>
  },
  getCatProfile(): Promise<CatProfile> {
    return ipcRenderer.invoke('desktop-cat:get-profile') as Promise<CatProfile>
  },
  createEmotion(content: string): Promise<Emotion> {
    return ipcRenderer.invoke('desktop-cat:create-emotion', content) as Promise<Emotion>
  },
  completeReminder(reminderId: number, version: number): Promise<Reminder> {
    return ipcRenderer.invoke('desktop-cat:complete-reminder', reminderId, version) as Promise<Reminder>
  },
  requestActivity(activityId: CatActivityId): Promise<CatActivityRequestResult> {
    return ipcRenderer.invoke('desktop-cat:request-activity', activityId) as Promise<CatActivityRequestResult>
  },
  onActivityChanged(listener: (snapshot: CatActivitySnapshot) => void): () => void {
    const wrappedListener = (_event: IpcRendererEvent, snapshot: CatActivitySnapshot): void => listener(snapshot)
    ipcRenderer.on('desktop-cat:activity-changed', wrappedListener)
    return () => ipcRenderer.removeListener('desktop-cat:activity-changed', wrappedListener)
  },
  onCatProfileChanged(listener: (profile: CatProfile) => void): () => void {
    const wrappedListener = (_event: IpcRendererEvent, profile: CatProfile): void => listener(profile)
    ipcRenderer.on('desktop-cat:profile-changed', wrappedListener)
    return () => ipcRenderer.removeListener('desktop-cat:profile-changed', wrappedListener)
  },
  onReminderDue(listener: (reminder: Reminder) => void): () => void {
    const wrappedListener = (_event: IpcRendererEvent, reminder: Reminder): void => listener(reminder)
    ipcRenderer.on('desktop-cat:reminder-due', wrappedListener)
    return () => ipcRenderer.removeListener('desktop-cat:reminder-due', wrappedListener)
  },
}

contextBridge.exposeInMainWorld('desktopCat', desktopCatApi)
