import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'pdf_frontend',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/PdfModuleApp.tsx',
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
    // remoteEntry.js na raiz do dist (não em assets/) para uma URL previsível
    // consumida pelo Front Host.
    assetsDir: '',
  },
  server: {
    port: 5101,
    cors: true,
  },
  preview: {
    port: 5101,
    cors: true,
  },
})
