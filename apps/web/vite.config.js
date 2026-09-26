import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:4080';
export default defineConfig({
    plugins: [react()],
    server: {
        port: 4000,
        strictPort: true,
        proxy: { '/api': apiTarget },
    },
});
