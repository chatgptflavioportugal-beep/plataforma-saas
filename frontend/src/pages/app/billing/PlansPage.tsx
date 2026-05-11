import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useTenant } from '@/contexts/TenantContext'
import { Button } from '@/components/ui/Button'
import type { Plan } from '@/types'

export function PlansPage() {
  const { currentTenant } = useTenant()

  const { data: plans = [] } = useQuery({
    queryKey: ['plans'],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>('/api/v1/public/plans')
      return data
    },
  })

  const currentPlanCode = currentTenant?.plan?.code

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Planos</h1>
        <p className="text-gray-500 mt-1">Escolha o plano ideal para sua equipe</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {plans.map((plan) => {
          const isCurrent = plan.code === currentPlanCode
          const isPopular = plan.code === 'pro'

          return (
            <div
              key={plan.id}
              className={`rounded-xl border p-6 flex flex-col ${
                isPopular
                  ? 'border-primary-500 shadow-lg ring-2 ring-primary-500 ring-offset-2'
                  : 'border-gray-200 bg-white shadow-sm'
              }`}
            >
              {isPopular && (
                <span className="mb-3 inline-flex self-start rounded-full bg-primary-600 px-2.5 py-0.5 text-xs font-medium text-white">
                  Mais popular
                </span>
              )}

              <h3 className="text-lg font-bold text-gray-900">{plan.name}</h3>
              <p className="mt-1 text-sm text-gray-500">{plan.description}</p>

              <div className="mt-4">
                {plan.price_monthly === 0 ? (
                  <p className="text-3xl font-bold text-gray-900">Grátis</p>
                ) : (
                  <p className="text-3xl font-bold text-gray-900">
                    R$ {plan.price_monthly.toFixed(2).replace('.', ',')}
                    <span className="text-base font-normal text-gray-400">/mês</span>
                  </p>
                )}
              </div>

              <ul className="mt-6 space-y-2 flex-1">
                <li className="flex items-center gap-2 text-sm text-gray-600">
                  <span className="text-green-500">✓</span>
                  {plan.max_users === -1 ? 'Usuários ilimitados' : `Até ${plan.max_users} usuários`}
                </li>
                <li className="flex items-center gap-2 text-sm text-gray-600">
                  <span className={plan.features['pdf.merge'] ? 'text-green-500' : 'text-gray-300'}>
                    {plan.features['pdf.merge'] ? '✓' : '✕'}
                  </span>
                  Merge de PDFs
                </li>
                <li className="flex items-center gap-2 text-sm text-gray-600">
                  <span className={plan.features['ai.agents'] ? 'text-green-500' : 'text-gray-300'}>
                    {plan.features['ai.agents'] ? '✓' : '✕'}
                  </span>
                  Agentes de IA
                </li>
                <li className="flex items-center gap-2 text-sm text-gray-600">
                  <span className={plan.features['reports.export'] ? 'text-green-500' : 'text-gray-300'}>
                    {plan.features['reports.export'] ? '✓' : '✕'}
                  </span>
                  Exportação de relatórios
                </li>
              </ul>

              <Button
                className="mt-6 w-full"
                variant={isCurrent ? 'secondary' : 'primary'}
                disabled={isCurrent}
              >
                {isCurrent ? 'Plano atual' : 'Assinar'}
              </Button>
            </div>
          )
        })}
      </div>
    </div>
  )
}
