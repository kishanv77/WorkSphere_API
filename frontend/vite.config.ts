import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server forwards API calls to Spring Boot so the app and API share one origin.
// That lets the browser send the SameSite=Strict refresh-token cookie.
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      '/actuator': { target: apiTarget, changeOrigin: true },
    },
  },
})
