import { useAuth } from './AuthContext'
import { Spinner } from '@/shared/components/Spinner'

const ADMIN_APP_URL = import.meta.env.VITE_ADMIN_APP_URL as string | undefined

interface ClientAreaGuardProps {
  children: React.ReactNode
}

/**
 * Bloqueia contas administrativas (SUPER_ADMIN/ADMIN_USER) de entrar na área
 * de clientes. O Front Host não conhece o Admin Service nem rotas /admin —
 * a área administrativa é o projeto frontend-admin, totalmente separado.
 */
export function ClientAreaGuard({ children }: ClientAreaGuardProps) {
  const { isAdminAccount, isLoading, signOut } = useAuth()

  if (isLoading) return <Spinner fullscreen />

  if (isAdminAccount) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="max-w-md w-full text-center space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Esta é a área de clientes</h2>
          <p className="text-sm text-gray-500">
            Contas administrativas devem acessar o painel administrativo, não a área de clientes.
          </p>
          {ADMIN_APP_URL && (
            <a
              href={ADMIN_APP_URL}
              className="inline-block rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
            >
              Ir para o painel administrativo
            </a>
          )}
          <button onClick={() => signOut()} className="block mx-auto text-sm text-gray-400 hover:text-gray-600">
            Sair
          </button>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
