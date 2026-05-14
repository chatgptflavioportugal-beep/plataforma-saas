import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'

export function AuthCallbackPage() {
  const { session, profile, isLoading } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (isLoading) return
    if (!session) {
      navigate('/login', { replace: true })
      return
    }
    const destination = profile?.system_role === 'SUPER_ADMIN' ? '/admin/dashboard' : '/app/dashboard'
    navigate(destination, { replace: true })
  }, [isLoading, session, profile, navigate])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="text-center space-y-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent mx-auto" />
        <p className="text-gray-500 text-sm">Autenticando...</p>
      </div>
    </div>
  )
}
