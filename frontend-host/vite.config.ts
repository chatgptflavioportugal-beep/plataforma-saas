import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const pdfRemoteUrl = env.VITE_PDF_REMOTE_URL || 'http://localhost:5101'
  const whatsappRemoteUrl = env.VITE_WHATSAPP_REMOTE_URL || 'http://localhost:5102'

  return {
    plugins: [
      react(),
      federation({
        name: 'frontend_host',
        remotes: {
          pdf_frontend: `${pdfRemoteUrl}/remoteEntry.js`,
          whatsapp_frontend: `${whatsappRemoteUrl}/remoteEntry.js`,
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
    },
    server: {
      port: 5100,
    },
    preview: {
      port: 5100,
    },
  }
})
