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
    port: 5174,
    strictPort: false, // 允许端口自动调整
    host: '0.0.0.0', // 允许外部访问
    proxy: {
      // 代理API请求到后端 - 这是包含最终修复的配置
      '/api': {
        target: 'http://localhost:8080', // 您的Spring Boot应用地址
        changeOrigin: true,
        secure: false,

        // 关键修复 #1: 我们告诉代理我们将自己处理请求体，以绕过其内部缓冲缺陷。
        selfHandleRequest: true,
        timeout: 3600000,
        proxyTimeout: 3600000,

        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            console.error('❌ API代理错误:', err.message);
          });

          proxy.on('proxyReq', (proxyReq, req, res) => {
            console.log('🔄 API代理请求:', req.method, req.url);

            req.on('data', (chunk) => {
              proxyReq.write(chunk);
            });
            req.on('end', () => {
              proxyReq.end();
            });
            req.on('error', (err) => {
                console.error('❌ 浏览器请求流错误:', err);
                proxyReq.destroy();
            });
          });

          proxy.on('proxyRes', (proxyRes, req, _res) => {
            console.log('✅ API代理响应:', req.method, req.url, '- Status:', proxyRes.statusCode);
          });
        }
      },
      // 代理WebSocket连接到后端，这部分无需修改
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
