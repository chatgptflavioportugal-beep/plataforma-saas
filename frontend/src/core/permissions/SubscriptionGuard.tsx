import { Navigate } from 'react-router-dom'
import { useTenant } from '@/core/workspaces/TenantContext'
import { Spinner } from '@/shared/components/Spinner'

interface SubscriptionGuardProps {
  children: React.ReactNode
}

export function SubscriptionGuard({ children }: SubscriptionGuardProps) {
  const { currentTenant, isLoading } = useTenant()

  if (isLoading) return <Spinner fullscreen />

  // Sem contexto de tenant carregado: TenantProvider está redirecionando
  // (p/ /onboarding se sem tenants, p/ /select-context se múltiplos sem seleção,
  // ou aguardando o tenant-context após auto-seleção). Bloqueia o render do
  // dashboard para que o usuário nunca veja o layout sem perfil ativo.
  if (!currentTenant) return <Spinner fullscreen />

  const status = currentTenant.subscription?.status
  if (status === 'suspended' || status === 'cancelled') {
    return <Navigate to="/app/billing/trial-expired" replace />
  }

  return <>{children}</>
}
