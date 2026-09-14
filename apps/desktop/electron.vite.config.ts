import { defineConfig } from 'electron-vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  //主进程，让Electron按照默认路径构建src/mian/index.ts
  main: {},
  //安全桥 构建src/preload/index.ts
  preload: {},
  //页面
  renderer: {
    //渲染进程需要vue，主进程和preload不需要vue
    plugins: [vue()],
  },
})
