import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { Spinner } from '@/shared/components/Spinner'
import { supabase } from './supabase'
import { useEffect, useState } from 'react'

interface SuperAdminGuardProps {
  children: React.ReactNode
}

export function SuperAdminGuard({ children }: SuperAdminGuardProps) {
  const { isSuperAdmin, isAdminUser, isLoading, profile } = useAuth()
  const [providerChecked, setProviderChecked] = useState(false)
  const [providerBlocked, setProviderBlocked] = useState(false)

  useEffect(() => {
    if (isLoading) return

    const isAdmin = isSuperAdmin || isAdminUser
    if (!isAdmin) {
      setProviderChecked(true)
      return
    }

    // Verificar se o usuário entrou via Google/OAuth social
    supabase.auth.getSession().then(({ data: { session } }) => {
      const provider = session?.user?.app_metadata?.provider
      if (provider && provider !== 'email') {
        setProviderBlocked(true)
      }
      setProviderChecked(true)
    })
  }, [isLoading, isSuperAdmin, isAdminUser])

  if (isLoading || !providerChecked) return <Spinner fullscreen />

  const isAdmin = isSuperAdmin || isAdminUser

  if (!isAdmin) {
    return <Navigate to="/login" replace />
  }

  if (providerBlocked) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-900">
        <div className="max-w-md w-full mx-4">
          <div className="rounded-xl bg-red-900/30 border border-red-700/50 p-8 text-center">
            <div className="text-red-400 text-4xl mb-4">⚠</div>
            <h2 className="text-lg font-semibold text-white mb-2">
              Acesso administrativo não permitido
            </h2>
            <p className="text-sm text-gray-300 mb-6">
              Acesso administrativo não permitido com login social.
            </p>
            <p className="text-sm text-gray-400 mb-6">
              Para acessar a área administrativa, utilize e-mail e senha cadastrados pela plataforma.
            </p>
            <button
              onClick={() => supabase.auth.signOut()}
              className="w-full rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-600"
            >
              Sair e tentar novamente
            </button>
          </div>
        </div>
      </div>
    )
  }

  // Verificar se perfil está ativo (apenas para ADMIN_USER)
  if (isAdminUser && !profile?.is_active) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-900">
        <div className="max-w-md w-full mx-4">
          <div className="rounded-xl bg-gray-800 border border-gray-700 p-8 text-center">
            <h2 className="text-lg font-semibold text-white mb-2">
              Conta administrativa inativa
            </h2>
            <p className="text-sm text-gray-400 mb-6">
              Sua conta administrativa foi desativada. Entre em contato com o administrador.
            </p>
            <button
              onClick={() => supabase.auth.signOut()}
              className="w-full rounded-lg bg-gray-700 px-4 py-2 text-sm font-medium text-white hover:bg-gray-600"
            >
              Sair
            </button>
          </div>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
