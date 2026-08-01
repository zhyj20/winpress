import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const frontendPort = Number(process.env.WINPRESS_FRONTEND_PORT || process.env.FRONTEND_PORT || 5217)
const backendPort = Number(
  process.env.VITE_BACKEND_PORT ||
    process.env.WINPRESS_BACKEND_PORT ||
    process.env.BACKEND_PORT ||
    8192,
)
const backendUrl = `http://127.0.0.1:${backendPort}`

export default defineConfig({
  cacheDir: 'tmp/vite',
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    host: '127.0.0.1',
    port: frontendPort,
    strictPort: true,
    proxy: {
      // Match the API path segment, not every public route beginning with "api".
      // Without the trailing slash, /api-integration was sent to Spring Boot in
      // local development and rendered a backend 404 instead of the public page.
      '/api/': {
        target: backendUrl,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin')
          })
        },
      },
    },
  },
  preview: {
    host: '127.0.0.1',
    port: frontendPort,
    strictPort: true,
  },
})
