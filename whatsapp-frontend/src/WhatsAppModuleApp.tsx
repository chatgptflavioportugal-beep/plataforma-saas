import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/services/queryClient'
import { ModuleApiProvider } from '@/contexts/ModuleApiContext'
import { WhatsAppPage } from '@/pages/WhatsAppPage'

export interface WhatsAppModuleAppProps {
  /** ModuleAccessToken já resolvido pelo Front Host para o módulo "whatsapp". */
  moduleToken: string
  /** Chamado quando o whatsapp-service devolve 401 (token expirado/inválido). */
  onUnauthorized?: () => void
}

/**
 * Componente exposto via Module Federation (`whatsapp_frontend/App`).
 * Autocontido: recebe apenas o ModuleAccessToken do Host e monta seu próprio
 * QueryClient e cliente HTTP do whatsapp-service.
 */
export default function WhatsAppModuleApp({ moduleToken, onUnauthorized }: WhatsAppModuleAppProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <ModuleApiProvider moduleToken={moduleToken} onUnauthorized={onUnauthorized}>
        <WhatsAppPage />
      </ModuleApiProvider>
    </QueryClientProvider>
  )
}
