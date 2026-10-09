import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'AGENT_')
  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: { '/api': { target: env.AGENT_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: true } },
    },
  }
})
