import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 로컬 개발은 127.0.0.1:4080 을 쓰고, 컨테이너 배포는 nginx 가
// 런타임 환경변수로 API 컨테이너를 가리킨다.
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:4080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4000,
    strictPort: true,
    proxy: { '/api': apiTarget },
  },
})
