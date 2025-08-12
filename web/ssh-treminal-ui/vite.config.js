import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  define: {
    // 修复SockJS客户端的global变量问题
    global: 'globalThis',
  },
  server: {
    // 端口自适应：如果5173被占用，Vite会自动选择下一个可用端口
    port: 5173,
    strictPort: false, // 允许端口自动调整
    host: '0.0.0.0', // 允许外部访问
    proxy: {
      // 代理API请求到后端 - 最重要的配置
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        timeout: 30000,
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            console.error('❌ API代理错误:', err.message);
          });
          proxy.on('proxyReq', (proxyReq, req, _res) => {
            console.log('🔄 API代理请求:', req.method, req.url, '-> http://localhost:8080' + req.url);
          });
          proxy.on('proxyRes', (proxyRes, req, _res) => {
            console.log('✅ API代理响应:', req.method, req.url, '- Status:', proxyRes.statusCode);
          });
        }
      },
      // 代理WebSocket连接到后端
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
        secure: false
      }
    }
  },
  // 开发环境变量
  envPrefix: 'VITE_'
})