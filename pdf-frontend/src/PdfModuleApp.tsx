import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/services/queryClient'
import { ModuleApiProvider } from '@/contexts/ModuleApiContext'
import { PdfMergePage } from '@/pages/PdfMergePage'

export interface PdfModuleAppProps {
  /** ModuleAccessToken já resolvido pelo Front Host para o módulo "pdf". */
  moduleToken: string
  /** Chamado quando o pdf-service devolve 401 (token expirado/inválido) — o Host deve renovar e re-renderizar com um novo token. */
  onUnauthorized?: () => void
  /** Chamado quando o pdf-service recusa a operação por limite de plano (403). */
  onFeatureLocked?: (details: { requiredPlan?: string; feature?: string }) => void
}

/**
 * Componente exposto via Module Federation (`pdf_frontend/App`). Autocontido:
 * recebe apenas o ModuleAccessToken do Host e monta seu próprio QueryClient e
 * cliente HTTP do pdf-service — nunca importa contexto do Host diretamente.
 */
export default function PdfModuleApp({ moduleToken, onUnauthorized, onFeatureLocked }: PdfModuleAppProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <ModuleApiProvider moduleToken={moduleToken} onUnauthorized={onUnauthorized}>
        <PdfMergePage onFeatureLocked={onFeatureLocked} />
      </ModuleApiProvider>
    </QueryClientProvider>
  )
}
