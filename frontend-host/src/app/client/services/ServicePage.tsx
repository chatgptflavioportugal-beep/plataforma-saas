import { Suspense, type ComponentType } from 'react'
import { useParams, useNavigate, type NavigateFunction } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '@/shared/services/catalogApi'
import type { ResolvedServiceRoute } from '@/shared/types'
import { getRemoteModule, type RemoteModuleProps } from '@/module-loader/remoteModules'
import { ModuleProvider, useModule } from '@/core/modules/ModuleContext'

export function ServicePage() {
  const { routeKey = '' } = useParams<{ routeKey: string }>()
  const navigate = useNavigate()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['service-route', routeKey],
    queryFn: async () => {
      const { data } = await catalogApi.get<ResolvedServiceRoute>(
        `/api/v1/services/resolve-route/${routeKey}`,
      )
      return data
    },
    enabled: !!routeKey,
    retry: false,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
      </div>
    )
  }

  if (isError || !data) {
    return <ServiceFeedback icon="❌" title="Erro ao carregar serviço" description="Tente novamente mais tarde." onBack={() => navigate('/app/dashboard')} />
  }

  if (data.accessStatus === 'NOT_FOUND') {
    return <ServiceFeedback icon="🔍" title="Serviço não encontrado" description="O endereço acessado não corresponde a nenhum serviço da plataforma." onBack={() => navigate('/app/dashboard')} />
  }

  if (data.accessStatus === 'DENIED') {
    return <ServiceFeedback icon="🚫" title="Acesso não permitido" description={`Você não possui permissão para acessar "${data.serviceName ?? routeKey}". Entre em contato com o administrador da conta.`} onBack={() => navigate('/app/dashboard')} />
  }

  if (data.accessStatus === 'EXPIRED') {
    return (
      <ServiceFeedback
        icon="⚠️"
        title="Assinatura expirada"
        description={`Sua assinatura do módulo "${data.moduleName ?? routeKey}" expirou. Para continuar utilizando este módulo, renove sua assinatura ou escolha outro plano.`}
        onBack={() => navigate('/app/dashboard')}
        primaryAction={{ label: 'Renovar assinatura', onClick: () => navigate('/app/billing/plans') }}
      />
    )
  }

  // ALLOWED — resolve o Micro Frontend do módulo via Module Federation
  const moduleSlug = data.moduleSlug ?? routeKey.split('-')[0]
  const RemoteComponent = getRemoteModule(moduleSlug)

  if (!RemoteComponent) {
    return (
      <ServiceFeedback
        icon="🚧"
        title="Serviço em breve"
        description={`O serviço "${data.serviceName}" ainda não possui uma tela implementada. Em breve estará disponível.`}
        onBack={() => navigate('/app/dashboard')}
      />
    )
  }

  return (
    <ModuleProvider moduleSlug={moduleSlug}>
      <ModuleGate navigate={navigate} RemoteComponent={RemoteComponent} />
    </ModuleProvider>
  )
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function LoadingSpinner() {
  return (
    <div className="flex items-center justify-center py-24">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
    </div>
  )
}

/**
 * Modal bloqueante exibido durante a ativação automática do plano Free (primeiro acesso).
 * Sem botão de fechar e sem dispensa por clique fora — o usuário não pode interagir
 * com a tela até a ativação terminar.
 */
function ActivationModal({ message }: { message: string }) {
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl max-w-sm w-full shadow-xl p-8 text-center">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600 mx-auto mb-5" />
        <h2 className="text-lg font-bold text-gray-900 mb-2">Ativando Plano Free</h2>
        <p className="text-sm text-gray-500">{message}</p>
      </div>
    </div>
  )
}

/**
 * Aguarda o ModuleAccessToken e então monta o Micro Frontend remoto,
 * repassando o token bruto por prop — o Host nunca chama o backend do módulo.
 */
function ModuleGate({
  RemoteComponent,
  navigate,
}: {
  RemoteComponent: ComponentType<RemoteModuleProps>
  navigate: NavigateFunction
}) {
  const { isLoading, activationMessage, error, moduleToken, refreshToken } = useModule()

  if (activationMessage) return <ActivationModal message={activationMessage} />

  if (isLoading || !moduleToken) return <LoadingSpinner />

  if (error) {
    return (
      <ServiceFeedback
        icon="🚫"
        title="Acesso não permitido"
        description={error}
        onBack={() => navigate('/app/dashboard')}
      />
    )
  }

  return (
    <Suspense fallback={<LoadingSpinner />}>
      <RemoteComponent
        moduleToken={moduleToken}
        onUnauthorized={refreshToken}
        onFeatureLocked={(details) =>
          navigate('/app/billing/feature-locked', { state: details })
        }
      />
    </Suspense>
  )
}

// ─── Feedback helper ──────────────────────────────────────────────────────────

function ServiceFeedback({
  icon,
  title,
  description,
  onBack,
  primaryAction,
}: {
  icon: string
  title: string
  description: string
  onBack: () => void
  /** Ação em destaque, exibida acima do botão de voltar (ex.: "Renovar assinatura"). */
  primaryAction?: { label: string; onClick: () => void }
}) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center max-w-md mx-auto">
      <div className="text-5xl mb-4">{icon}</div>
      <h2 className="text-xl font-bold text-gray-900 mb-2">{title}</h2>
      <p className="text-sm text-gray-500 mb-6">{description}</p>
      <div className="flex flex-col gap-3 w-full max-w-xs">
        {primaryAction && (
          <button
            onClick={primaryAction.onClick}
            className="rounded-lg bg-red-600 px-5 py-2 text-sm font-medium text-white hover:bg-red-700 transition-colors"
          >
            {primaryAction.label}
          </button>
        )}
        <button
          onClick={onBack}
          className={
            primaryAction
              ? 'rounded-lg border border-gray-200 px-5 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors'
              : 'rounded-lg bg-primary-600 px-5 py-2 text-sm font-medium text-white hover:bg-primary-700 transition-colors'
          }
        >
          Voltar ao Dashboard
        </button>
      </div>
    </div>
  )
}
