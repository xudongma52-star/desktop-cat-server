import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const previewApi = loadEnv(mode, process.cwd(), '').ARTICLE_PREVIEW_API
  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        // 本轮本地联调可连接独立数据库副本；默认仍使用现有服务器。
        ...(previewApi ? { '/api/records': { target: previewApi, changeOrigin: true } } : {}),
        '/api': {
          // 本机既有 SSH 隧道提供真实后端 HTTPS 入口，避免连到 8080 的样例预览服务。
          target: 'https://maxmeme.cn',
          changeOrigin: true,
        },
      },
    },
  }
})
