import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/core/auth/AuthContext'

export function AuthCallbackPage() {
  const { session, isLoading } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  useEffect(() => {
    if (isLoading) return
    if (!session) {
      navigate('/login', { replace: true })
      return
    }

    // returnUrl pode vir de: (1) query param (emailRedirectTo do Supabase) ou
    // (2) sessionStorage (OAuth / fluxo na mesma aba)
    const returnUrl =
      searchParams.get('returnUrl') ||
      sessionStorage.getItem('auth_return_url')
    if (returnUrl) {
      sessionStorage.removeItem('auth_return_url')
      navigate(returnUrl, { replace: true })
      return
    }

    // Contas administrativas não passam por aqui (usam o frontend-admin,
    // separado) — o ClientAreaGuard bloqueia se acabarem caindo aqui mesmo assim.
    navigate('/app/dashboard', { replace: true })
  }, [isLoading, session, navigate, searchParams])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="text-center space-y-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent mx-auto" />
        <p className="text-gray-500 text-sm">Autenticando...</p>
      </div>
    </div>
  )
}
