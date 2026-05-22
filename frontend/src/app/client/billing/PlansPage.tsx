import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useTenant } from '@/core/workspaces/TenantContext'
import { Button } from '@/shared/components/Button'
import type { Plan } from '@/shared/types'

type PlanModule = { module_name: string; module_slug: string; module_icon_path: string | null }

function parsePlanModules(modulesJson?: string): PlanModule[] {
  if (!modulesJson) return []
  try {
    return JSON.parse(modulesJson) as PlanModule[]
  } catch {
    return []
  }
}

function brl(value: number) {
  if (value === 0) return 'Grátis'
  return `R$ ${value.toFixed(2).replace('.', ',')}`
}


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

            const monthlyPrice       = plan.total_monthly_price ?? 0
            const annualMonthlyPrice = plan.total_annual_monthly_price ?? 0
            const showAnnual  = isAnnual && plan.billing_type !== 'monthly'
            const displayPrice = showAnnual ? annualMonthlyPrice : monthlyPrice
            const savings = plan.discount_annual_percent > 0 ? plan.discount_annual_percent : null

            const modules = parsePlanModules(plan.modules_json)

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
                            Cobrado anualmente — {brl((plan.total_annual_price ?? annualMonthlyPrice * 12))}/ano
                          </p>
                        )}
                        {isAnnual && !showAnnual && plan.billing_type === 'monthly' && (
                          <p className="mt-1 text-xs text-gray-400">Disponível apenas mensal</p>
                        )}
                      </>
                    )}

                    {showAnnual && savings && (
                      <span className="mt-2 inline-flex items-center rounded-full bg-green-50 border border-green-200 px-2.5 py-0.5 text-xs font-semibold text-green-700">
                        {savings}% de economia
                      </span>
                    )}

                    {showAnnual && monthlyPrice > 0 && monthlyPrice !== annualMonthlyPrice && (
                      <p className="mt-1 text-xs text-gray-400">
                        Era{' '}
                        <span className="line-through">{brl(monthlyPrice)}/mês</span>
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

                  {/* Módulos */}
                  {modules.length > 0 && (
                    <div className="mt-5">
                      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">
                        Módulos incluídos
                      </p>
                      <ul className="space-y-1.5">
                        {modules.map((mod) => (
                          <li key={mod.module_slug} className="flex items-center gap-2 text-sm text-gray-700">
                            {mod.module_icon_path ? (
                              <img
                                src={mod.module_icon_path}
                                alt=""
                                className="h-4 w-4 shrink-0 object-contain"
                              />
                            ) : (
                              <span className="h-4 w-4 shrink-0 rounded-sm bg-primary-100 flex items-center justify-center">
                                <span className="block h-2 w-2 rounded-sm bg-primary-400" />
                              </span>
                            )}
                            {mod.module_name}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  <div className="flex-1" />

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
