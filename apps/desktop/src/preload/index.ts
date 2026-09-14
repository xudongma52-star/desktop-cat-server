import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'
import type {
  CatActivityId,
  CatActivityRequestResult,
  CatActivitySnapshot,
} from '../shared/cat-activity'

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
  getActivity(): Promise<CatActivitySnapshot> {
    return ipcRenderer.invoke('desktop-cat:get-activity') as Promise<CatActivitySnapshot>
  },
  requestActivity(activityId: CatActivityId): Promise<CatActivityRequestResult> {
    return ipcRenderer.invoke('desktop-cat:request-activity', activityId) as Promise<CatActivityRequestResult>
  },
  onActivityChanged(listener: (snapshot: CatActivitySnapshot) => void): () => void {
    const wrappedListener = (_event: IpcRendererEvent, snapshot: CatActivitySnapshot): void => listener(snapshot)
    ipcRenderer.on('desktop-cat:activity-changed', wrappedListener)
    return () => ipcRenderer.removeListener('desktop-cat:activity-changed', wrappedListener)
  },
}

contextBridge.exposeInMainWorld('desktopCat', desktopCatApi)
