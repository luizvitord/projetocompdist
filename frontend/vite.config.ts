import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/escolar': 'http://localhost:8080',
      '/administrativa': 'http://localhost:8080',
      '/usuarios': 'http://localhost:8080',
      '/notificacao': 'http://localhost:8080',
      // WebSocket das notificações: direto para o notificacao-service (o gateway não faz proxy de WebSocket)
      '/ws': { target: 'http://localhost:8084', ws: true }
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    globals: true
  }
});
