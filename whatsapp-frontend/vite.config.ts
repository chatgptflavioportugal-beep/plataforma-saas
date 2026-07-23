import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'whatsapp_frontend',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/WhatsAppModuleApp.tsx',
      },
      shared: ['react', 'react-dom'],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    target: 'esnext',
    modulePreload: false,
    cssCodeSplit: false,
    assetsDir: '',
  },
  server: {
    port: 5102,
    cors: true,
  },
  preview: {
    port: 5102,
    cors: true,
  },
})
