import { useTenant } from '@/core/workspaces/TenantContext'
import { Spinner } from '@/shared/components/Spinner'
import { useAuth } from '@/core/auth/AuthContext'

interface SubscriptionGuardProps {
  children: React.ReactNode
}

export function SubscriptionGuard({ children }: SubscriptionGuardProps) {
  const { currentTenant, isLoading, tenantProfileError } = useTenant()
  const { signOut } = useAuth()

  if (isLoading) return <Spinner fullscreen />

  // A busca do perfil do tenant falhou (401/402): sem isso, currentTenant nunca
  // chega a ser preenchido e ficaríamos presos num spinner infinito sem motivo
  // visível. Mostra o erro em vez de travar a tela silenciosamente.
  if (tenantProfileError) {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-gray-50 px-6 text-center">
        <p className="max-w-md text-gray-700">
          Não foi possível carregar o seu perfil: {tenantProfileError}
        </p>
        <button
          onClick={() => signOut()}
          className="rounded-md bg-primary-600 px-4 py-2 text-white hover:bg-primary-700"
        >
          Voltar ao login
        </button>
      </div>
    )
  }

  // Sem perfil de tenant carregado: TenantProvider está redirecionando
  // (p/ /onboarding se sem tenants, p/ /select-profile se múltiplos sem seleção,
  // ou aguardando o tenant-profile após auto-seleção). Bloqueia o render do
  // dashboard para que o usuário nunca veja o layout sem perfil ativo.
  if (!currentTenant) return <Spinner fullscreen />

  // A conta nunca é bloqueada por expiração de assinatura/trial — isso é
  // controlado por módulo (ver Dashboard/PlansPage), não a nível de plataforma.
  return <>{children}</>
}
