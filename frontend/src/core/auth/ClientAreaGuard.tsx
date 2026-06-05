import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { Spinner } from '@/shared/components/Spinner'

interface ClientAreaGuardProps {
  children: React.ReactNode
}

export function ClientAreaGuard({ children }: ClientAreaGuardProps) {
  const { isSuperAdmin, isAdminUser, isLoading } = useAuth()

  if (isLoading) return <Spinner fullscreen />

  if (isSuperAdmin || isAdminUser) {
    return <Navigate to="/admin/dashboard" replace />
  }

  return <>{children}</>
}
