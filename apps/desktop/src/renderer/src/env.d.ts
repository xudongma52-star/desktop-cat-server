/// <reference types="vite/client" />

//定义 preload 暴露的 API 形状。
interface DesktopCatApi {
  setIgnoreMouseEvents(ignore: boolean): void
}

interface Window {
  desktopCat: DesktopCatApi
}
