/**
 * contextBridge：把有限的 API 暴露给 Vue
 * ipcRenderer：从 Vue 所在进程向 Electron 主进程发消息
 */
import { contextBridge, ipcRenderer } from 'electron'

//Vue 只能发送一个布尔值，不能自由发送任意 Electron 命令。
const desktopCatApi = {
  setIgnoreMouseEvents(ignore: boolean): void {
    ipcRenderer.send('desktop-cat:set-ignore-mouse-events', ignore)
  },
  moveWindowBy(deltaX: number, deltaY: number): void {
    ipcRenderer.send('desktop-cat:move-window-by', deltaX, deltaY)
  },
}

//暴露到浏览器
contextBridge.exposeInMainWorld('desktopCat', desktopCatApi)
