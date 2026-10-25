import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// npm run dev    -> http://localhost:5173 (calls to /api go to Spring Boot on 8080)
// npm run build  -> writes the finished app into Spring Boot's static folder,
//                   so one running project serves both the API and the UI
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
});
