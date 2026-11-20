import { defineConfig } from 'vitest/config';

// En Docker Compose el backend se llama "backend"; fuera de Docker, localhost.
const destinoApi = process.env['VITE_PROXY_TARGET'] ?? 'http://localhost:8080';

export default defineConfig({
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: destinoApi, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
});
