import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  // Same-origin in dev so the HttpOnly SameSite=Strict refresh cookie works through the proxy.
  server: { port: 5173, proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false }, '/ws': { target: 'ws://localhost:8080', ws: true } } },
})
