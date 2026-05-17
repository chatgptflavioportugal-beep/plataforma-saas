import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useTenant } from '@/core/workspaces/TenantContext'
import { Button } from '@/shared/components/Button'
import type { Plan } from '@/shared/types'

function brl(value: number) {
  if (value === 0) return 'Grátis'
  return `R$ ${value.toFixed(2).replace('.', ',')}`
}

function CheckIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`h-4 w-4 flex-shrink-0 ${active ? 'text-green-400' : 'text-gray-600'}`}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={2.5}
    >
      {active ? (
        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
      ) : (
        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
      )}
    </svg>
  )
}

const FEATURE_LABELS: Record<string, string> = {
  'pdf.merge':        'Merge de PDFs',
  'ai.agents':        'Agentes de IA',
  'reports.export':   'Exportação de relatórios',
  'api.access':       'Acesso à API',
  white_label:        'White-label',
  priority_support:   'Suporte prioritário',
}

const DISPLAY_FEATURES = ['pdf.merge', 'ai.agents', 'reports.export', 'api.access', 'white_label', 'priority_support']

type PlanTypeFilter = 'individual' | 'business'

export function PlansPage() {
  const { currentTenant } = useTenant()

  // Inicializa o tipo de plano com base no tenant ativo
  const defaultType: PlanTypeFilter = currentTenant?.tenant?.type === 'individual' ? 'individual' : 'business'
  const [planType, setPlanType] = useState<PlanTypeFilter>(defaultType)
  const [isAnnual, setIsAnnual] = useState(false)

  const { data: plans = [] } = useQuery({
    queryKey: ['plans', planType],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>(`/api/v1/public/plans?type=${planType}`)
      return data
    },
  })

  const currentPlanCode = currentTenant?.plan?.code

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-2xl font-bold text-gray-900">Escolha seu plano</h1>
        <p className="text-gray-500">Sem taxas ocultas. Cancele quando quiser.</p>
      </div>

      {/* Filtros: tipo de plano + ciclo */}
      <div className="flex flex-col items-center gap-4">

        {/* Seletor Individual / Empresarial */}
        <div className="flex rounded-xl border border-gray-200 bg-gray-100 p-1">
          {(['individual', 'business'] as const).map((type) => (
            <button
              key={type}
              type="button"
              onClick={() => setPlanType(type)}
              className={`px-5 py-2 rounded-lg text-sm font-medium transition-all ${
                planType === type
                  ? 'bg-white text-gray-900 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {type === 'individual' ? 'Individual' : 'Empresarial'}
            </button>
          ))}
        </div>

        {/* Toggle Mensal / Anual */}
        <div className="flex items-center gap-4">
          <span className={`text-sm font-medium ${!isAnnual ? 'text-gray-900' : 'text-gray-400'}`}>
            Mensal
          </span>

          <button
            type="button"
            onClick={() => setIsAnnual((v) => !v)}
            className={`relative inline-flex h-7 w-14 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 ${
              isAnnual ? 'bg-primary-600' : 'bg-gray-300'
            }`}
          >
            <span
              className={`inline-block h-5 w-5 transform rounded-full bg-white shadow-md transition-transform ${
                isAnnual ? 'translate-x-8' : 'translate-x-1'
              }`}
            />
          </button>

          <div className="flex items-center gap-2">
            <span className={`text-sm font-medium ${isAnnual ? 'text-gray-900' : 'text-gray-400'}`}>
              Anual
            </span>
            <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-semibold text-green-700">
              Economize até 20%
            </span>
          </div>
        </div>

      </div>

      {/* Cards de planos */}
      {plans.length === 0 ? (
        <div className="text-center py-16 text-gray-400 text-sm">
          Nenhum plano {planType === 'individual' ? 'individual' : 'empresarial'} disponível no momento.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {plans.map((plan) => {
            const isCurrent = plan.code === currentPlanCode
            const isPopular = plan.is_most_popular

            const showAnnual  = isAnnual && plan.price_annual != null && plan.billing_type !== 'monthly'
            const displayPrice = showAnnual ? plan.price_annual! : plan.price_monthly
            const savings = plan.discount_annual_percent > 0 ? plan.discount_annual_percent : null

            return (
              <div
                key={plan.id}
                className={`relative rounded-2xl border flex flex-col transition-shadow ${
                  isPopular
                    ? 'border-primary-500 shadow-xl ring-2 ring-primary-500 ring-offset-2 bg-white'
                    : 'border-gray-200 bg-white shadow-sm hover:shadow-md'
                }`}
              >
                {isPopular && (
                  <div className="absolute -top-3.5 left-1/2 -translate-x-1/2">
                    <span className="rounded-full bg-primary-600 px-3 py-1 text-xs font-semibold text-white shadow">
                      Mais popular
                    </span>
                  </div>
                )}

                <div className="p-6 flex-1 flex flex-col">
                  <h3 className="text-lg font-bold text-gray-900">{plan.name}</h3>
                  <p className="mt-1 text-sm text-gray-500 min-h-[2.5rem]">{plan.description}</p>

                  {/* Preço */}
                  <div className="mt-5">
                    {displayPrice === 0 ? (
                      <p className="text-4xl font-extrabold text-gray-900">Grátis</p>
                    ) : (
                      <>
                        <p className="text-4xl font-extrabold text-gray-900">
                          {brl(displayPrice)}
                          <span className="text-base font-normal text-gray-400">/mês</span>
                        </p>
                        {showAnnual && (
                          <p className="mt-1 text-xs text-gray-500">
                            Cobrado anualmente — {brl(displayPrice * 12)}/ano
                          </p>
                        )}
                        {isAnnual && savings && !showAnnual && plan.billing_type === 'monthly' && (
                          <p className="mt-1 text-xs text-gray-400">Disponível apenas mensal</p>
                        )}
                      </>
                    )}

                    {showAnnual && savings && (
                      <span className="mt-2 inline-flex items-center rounded-full bg-green-50 border border-green-200 px-2.5 py-0.5 text-xs font-semibold text-green-700">
                        {savings}% de economia
                      </span>
                    )}

                    {showAnnual && plan.price_monthly > 0 && (
                      <p className="mt-1 text-xs text-gray-400">
                        Era{' '}
                        <span className="line-through">{brl(plan.price_monthly)}/mês</span>
                      </p>
                    )}
                  </div>

                  {/* Limites */}
                  <p className="mt-4 text-sm text-gray-500">
                    {plan.max_users === -1 ? 'Usuários ilimitados' : `Até ${plan.max_users} usuário${plan.max_users !== 1 ? 's' : ''}`}
                    {plan.max_ai_requests_month !== -1 && (
                      <> · {plan.max_ai_requests_month} req. IA/mês</>
                    )}
                  </p>

                  {/* Features */}
                  <ul className="mt-5 space-y-2.5 flex-1">
                    {DISPLAY_FEATURES.map((key) => {
                      const active = !!(plan.features as Record<string, boolean | number>)[key]
                      return (
                        <li key={key} className="flex items-center gap-2 text-sm">
                          <CheckIcon active={active} />
                          <span className={active ? 'text-gray-700' : 'text-gray-400'}>
                            {FEATURE_LABELS[key] ?? key}
                          </span>
                        </li>
                      )
                    })}
                  </ul>

                  <Button
                    className="mt-6 w-full"
                    variant={isCurrent ? 'secondary' : 'primary'}
                    disabled={isCurrent}
                  >
                    {isCurrent ? 'Plano atual' : 'Assinar'}
                  </Button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Nota rodapé */}
      <p className="text-center text-xs text-gray-400">
        Todos os planos incluem 14 dias grátis para testar. Pagamentos seguros e encriptados.
      </p>
    </div>
  )
}
