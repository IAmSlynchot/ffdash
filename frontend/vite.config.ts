/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Forward API calls to the Spring Boot backend during development,
      // so the frontend can just call relative /api/... URLs.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  // No jsdom/component-testing setup — the only tests today are pure functions
  // (see src/api/aggregations.test.ts), which run fine under Vite's default node environment.
  test: {
    include: ['src/**/*.test.ts'],
  },
})
