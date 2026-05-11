import { Navigate } from 'react-router-dom'
import { useTenant } from '@/contexts/TenantContext'
import { Spinner } from '@/components/ui/Spinner'

interface SubscriptionGuardProps {
  children: React.ReactNode
}

export function SubscriptionGuard({ children }: SubscriptionGuardProps) {
  const { currentTenant, isLoading } = useTenant()

  if (isLoading) return <Spinner fullscreen />

  const status = currentTenant?.subscription?.status
  if (status === 'suspended' || status === 'cancelled') {
    return <Navigate to="/app/billing/trial-expired" replace />
  }

  return <>{children}</>
}
