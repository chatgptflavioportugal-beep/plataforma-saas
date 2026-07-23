import WhatsAppModuleApp from '@/WhatsAppModuleApp'

const DEV_MODULE_TOKEN = import.meta.env.VITE_DEV_MODULE_TOKEN as string | undefined

/**
 * Entry usada apenas quando este projeto roda standalone (`npm run dev`),
 * fora do Front Host. Em produção, quem monta `WhatsAppModuleApp` é sempre
 * o Front Host, via Module Federation.
 */
export function App() {
  if (!DEV_MODULE_TOKEN) {
    return (
      <div className="flex h-screen items-center justify-center text-center p-8">
        <p className="text-gray-500 max-w-md">
          Rodando whatsapp-frontend em modo standalone. Defina <code>VITE_DEV_MODULE_TOKEN</code> no
          .env local com um ModuleAccessToken válido (slug "whatsapp") para testar contra o
          whatsapp-service, ou acesse este módulo através do Front Host em desenvolvimento normal.
        </p>
      </div>
    )
  }

  return <WhatsAppModuleApp moduleToken={DEV_MODULE_TOKEN} />
}
