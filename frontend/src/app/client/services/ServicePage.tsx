import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import type { ResolvedServiceRoute } from '@/shared/types'
import { getServiceComponent } from '@/modules/serviceRegistry'

export function ServicePage() {
  const { routeKey = '' } = useParams<{ routeKey: string }>()
  const navigate = useNavigate()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['service-route', routeKey],
    queryFn: async () => {
      const { data } = await api.get<ResolvedServiceRoute>(
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

  // ALLOWED — tenta carregar o componente do registry
  const ServiceComponent = getServiceComponent(routeKey)

  if (!ServiceComponent) {
    return (
      <ServiceFeedback
        icon="🚧"
        title="Serviço em breve"
        description={`O serviço "${data.serviceName}" ainda não possui uma tela implementada. Em breve estará disponível.`}
        onBack={() => navigate('/app/dashboard')}
      />
    )
  }

  return <ServiceComponent />
}

// ─── Feedback helper ──────────────────────────────────────────────────────────

function ServiceFeedback({
  icon,
  title,
  description,
  onBack,
}: {
  icon: string
  title: string
  description: string
  onBack: () => void
}) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center max-w-md mx-auto">
      <div className="text-5xl mb-4">{icon}</div>
      <h2 className="text-xl font-bold text-gray-900 mb-2">{title}</h2>
      <p className="text-sm text-gray-500 mb-6">{description}</p>
      <button
        onClick={onBack}
        className="rounded-lg bg-primary-600 px-5 py-2 text-sm font-medium text-white hover:bg-primary-700 transition-colors"
      >
        Voltar ao Dashboard
      </button>
    </div>
  )
}
