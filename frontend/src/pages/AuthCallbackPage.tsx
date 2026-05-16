import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'

export function AuthCallbackPage() {
  const { session, profile, isLoading } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    // Aguarda o AuthProvider terminar de carregar sessão + profile.
    // Sem esse guard o effect dispara duas vezes: primeiro com profile=null
    // (navegaria admin para /app/dashboard) e depois com profile carregado.
    if (isLoading) return
    if (!session) {
      navigate('/login', { replace: true })
      return
    }

    const returnUrl = sessionStorage.getItem('auth_return_url')
    if (returnUrl) {
      sessionStorage.removeItem('auth_return_url')
      navigate(returnUrl, { replace: true })
      return
    }

    // profile é sempre definido aqui porque isLoading=false garante que
    // loadProfile() completou (setProfile + setIsLoading são batched no React 18).
    const destination = profile?.system_role === 'SUPER_ADMIN'
      ? '/admin/dashboard'
      : '/app/dashboard'
    navigate(destination, { replace: true })
  }, [isLoading, session, navigate])
  // Nota: profile propositalmente fora das deps — isLoading=false já garante
  // que o profile está carregado. Incluir profile causaria dupla navegação.

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="text-center space-y-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary-600 border-t-transparent mx-auto" />
        <p className="text-gray-500 text-sm">Autenticando...</p>
      </div>
    </div>
  )
}
