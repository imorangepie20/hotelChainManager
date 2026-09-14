import { defineConfig } from '@playwright/test'

// 사용자가 미리 시작한 고객 웹만 사용한다. 이 설정은 서버를 자동 기동하지 않는다.
export default defineConfig({
  testDir: './test',
  use: { baseURL: process.env.CUSTOMER_WEB_BASE_URL ?? 'http://127.0.0.1:4000', browserName: 'chromium' },
  reporter: 'list',
})
