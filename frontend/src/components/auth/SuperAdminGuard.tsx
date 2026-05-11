import { Navigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { Spinner } from '@/components/ui/Spinner'

interface SuperAdminGuardProps {
  children: React.ReactNode
}

export function SuperAdminGuard({ children }: SuperAdminGuardProps) {
  const { isSuperAdmin, isLoading } = useAuth()

  if (isLoading) return <Spinner fullscreen />

  if (!isSuperAdmin) {
    return <Navigate to="/app/dashboard" replace />
  }

  return <>{children}</>
}
