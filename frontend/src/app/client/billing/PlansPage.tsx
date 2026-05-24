import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { Button } from '@/shared/components/Button'
import { useTenant } from '@/core/workspaces/TenantContext'
import type { ModuleBillingOption, ModulePlan, ModuleService } from '@/shared/types'

// Raw shape returned by the API (JSON strings not yet parsed)
type ModuleBillingOptionRaw = {
  module_id: string
  module_name: string
  module_slug: string
  module_description: string | null
  icon_path: string | null
  services_json: string
  available_plans_json: string
}

function parseJson<T>(json?: string | null): T | null {
  if (!json) return null
  try { return JSON.parse(json) as T } catch { return null }
}

function brl(value: number) {
  if (value === 0) return 'Grátis'
  return `R$ ${value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function planAnnualTotal(plan: ModulePlan): number {
  return plan.annual_total_price > 0 ? plan.annual_total_price : plan.annual_monthly_price * 12
}

type SelectedConfig = {
  module: ModuleBillingOption
  plan: ModulePlan
  isAnnual: boolean
}

// ─── Profile Banner ───────────────────────────────────────────────────────────

type ProfileBannerProps = {
  profileName: string
  profileType: 'individual' | 'business'
}

function ProfileBanner({ profileName, profileType }: ProfileBannerProps) {
  const typeLabel = profileType === 'individual' ? 'Individual' : 'Empresarial'
  const typeColor = profileType === 'individual'
    ? 'bg-blue-50 border-blue-200 text-blue-800'
    : 'bg-amber-50 border-amber-200 text-amber-800'

  return (
    <div className={`rounded-xl border px-5 py-3.5 flex items-center gap-3 ${typeColor}`}>
      <div className="h-8 w-8 rounded-full bg-white/70 flex items-center justify-center shrink-0 shadow-sm">
        <span className="text-sm font-bold">
          {profileName.charAt(0).toUpperCase()}
        </span>
      </div>
      <div className="min-w-0">
        <p className="text-xs font-semibold uppercase tracking-wide opacity-70">
          Você está configurando módulos para
        </p>
        <p className="font-bold text-base leading-tight truncate">{profileName}</p>
      </div>
      <span className="ml-auto shrink-0 rounded-full bg-white/60 border border-current/20 px-2.5 py-0.5 text-xs font-semibold">
        Tipo: {typeLabel}
      </span>
    </div>
  )
}

// ─── Module Card ──────────────────────────────────────────────────────────────

type ModuleCardProps = {
  module: ModuleBillingOption
  selected: SelectedConfig | undefined
  onChoose: () => void
}

function ModuleCard({ module, selected, onChoose }: ModuleCardProps) {
  const plans = module.available_plans
  const hasPlans = plans.length > 0
  const planNames = plans.map(p => p.plan_name).join(', ')

  let mainPrice: string | null = null
  let subPrice: string | null = null

  if (selected) {
    if (selected.isAnnual) {
      const total = planAnnualTotal(selected.plan)
      mainPrice = total === 0 ? 'Grátis' : `${brl(total)}/ano`
      if (total > 0) subPrice = `em até 12x de ${brl(selected.plan.annual_monthly_price)}`
    } else {
      mainPrice = selected.plan.monthly_price === 0 ? 'Grátis' : `${brl(selected.plan.monthly_price)}/mês`
    }
  } else if (hasPlans) {
    const minMonthly = Math.min(...plans.map(p => p.monthly_price))
    if (minMonthly > 0) mainPrice = `A partir de ${brl(minMonthly)}/mês`
  }

  return (
    <div
      className={`relative rounded-2xl border bg-white flex flex-col transition-shadow ${
        selected
          ? 'border-primary-500 ring-2 ring-primary-500 ring-offset-2 shadow-md'
          : 'border-gray-200 shadow-sm hover:shadow-md'
      }`}
    >
      {selected && (
        <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 whitespace-nowrap flex items-center gap-1.5">
          <span className="rounded-full bg-primary-600 px-3 py-1 text-xs font-semibold text-white shadow">
            {selected.plan.plan_name}
          </span>
          <span className={`rounded-full px-2 py-1 text-xs font-semibold shadow ${
            selected.isAnnual ? 'bg-green-600 text-white' : 'bg-gray-600 text-white'
          }`}>
            {selected.isAnnual ? 'Anual' : 'Mensal'}
          </span>
        </div>
      )}

      <div className="p-6 flex-1 flex flex-col">
        <div className="flex items-center gap-3">
          {module.icon_path ? (
            <img src={module.icon_path} alt="" className="h-8 w-8 object-contain shrink-0" />
          ) : (
            <span className="h-8 w-8 rounded-lg bg-primary-100 flex items-center justify-center shrink-0">
              <span className="block h-4 w-4 rounded-sm bg-primary-400" />
            </span>
          )}
          <h3 className="text-lg font-bold text-gray-900">{module.module_name}</h3>
        </div>

        {module.module_description && (
          <p className="mt-2 text-sm text-gray-500 min-h-[2rem]">{module.module_description}</p>
        )}

        {module.services.length > 0 && (
          <div className="mt-4">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1.5">Serviços</p>
            <ul className="space-y-1">
              {module.services.map(svc => (
                <li key={svc.id} className="text-sm text-gray-700 flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full bg-gray-300 shrink-0" />
                  {svc.name}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex-1" />

        <div className="mt-4 pt-4 border-t border-gray-100 space-y-0.5">
          {hasPlans ? (
            <>
              {!selected && (
                <p className="text-xs text-gray-400">
                  Disponível em: <span className="font-medium text-gray-600">{planNames}</span>
                </p>
              )}
              {mainPrice && (
                <p className={selected ? 'text-sm font-bold text-gray-900' : 'text-xs text-gray-400'}>
                  {mainPrice}
                </p>
              )}
              {subPrice && <p className="text-xs text-gray-500">{subPrice}</p>}
            </>
          ) : (
            <p className="text-xs text-gray-400">Nenhum plano configurado</p>
          )}
        </div>

        <Button
          className="mt-4 w-full"
          variant={selected ? 'secondary' : 'primary'}
          onClick={onChoose}
          disabled={!hasPlans}
        >
          {selected ? 'Trocar plano' : 'Escolher plano'}
        </Button>
      </div>
    </div>
  )
}

// ─── Plan Modal ───────────────────────────────────────────────────────────────

type PlanModalProps = {
  module: ModuleBillingOption
  initialIsAnnual: boolean
  currentPlanId: string | undefined
  onSelect: (plan: ModulePlan, isAnnual: boolean) => void
  onClose: () => void
}

function PlanModal({ module, initialIsAnnual, currentPlanId, onSelect, onClose }: PlanModalProps) {
  const [isAnnual, setIsAnnual] = useState(initialIsAnnual)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] flex flex-col">
        <div className="border-b border-gray-100 px-6 py-4 flex items-start justify-between gap-4 shrink-0">
          <div>
            <h2 className="text-lg font-bold text-gray-900">{module.module_name}</h2>
            <p className="text-sm text-gray-500">Escolha o plano e o ciclo de cobrança</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-xl leading-none mt-0.5 shrink-0"
          >
            ✕
          </button>
        </div>

        <div className="px-6 pt-4 pb-2 flex items-center justify-center gap-4">
          <span className={`text-sm font-medium ${!isAnnual ? 'text-gray-900' : 'text-gray-400'}`}>Mensal</span>
          <button
            type="button"
            onClick={() => setIsAnnual(v => !v)}
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
            <span className={`text-sm font-medium ${isAnnual ? 'text-gray-900' : 'text-gray-400'}`}>Anual</span>
            <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-semibold text-green-700">
              Economize até 20%
            </span>
          </div>
        </div>

        <div className="overflow-y-auto p-6 space-y-4">
          {module.available_plans.map(plan => {
            const isCurrent = plan.plan_id === currentPlanId && isAnnual === initialIsAnnual
            const total = planAnnualTotal(plan)

            return (
              <div
                key={plan.plan_id}
                className={`rounded-xl border p-4 transition-all ${
                  isCurrent
                    ? 'border-primary-500 bg-primary-50 ring-1 ring-primary-400'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <div className="flex items-start gap-4">
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-gray-900">{plan.plan_name}</p>

                    {isAnnual ? (
                      <>
                        <p className="mt-1 text-2xl font-bold text-gray-900">
                          {total === 0
                            ? 'Grátis'
                            : <>{brl(total)}<span className="text-sm font-normal text-gray-400">/ano</span></>
                          }
                        </p>
                        {total > 0 && (
                          <p className="text-sm text-gray-500 mt-0.5">
                            em até 12x de {brl(plan.annual_monthly_price)}
                          </p>
                        )}
                      </>
                    ) : (
                      <>
                        <p className="mt-1 text-2xl font-bold text-gray-900">
                          {plan.monthly_price === 0
                            ? 'Grátis'
                            : <>{brl(plan.monthly_price)}<span className="text-sm font-normal text-gray-400">/mês</span></>
                          }
                        </p>
                        {plan.monthly_price > 0 && (
                          <p className="text-xs text-gray-400 mt-0.5">cobrança mensal</p>
                        )}
                      </>
                    )}

                    {plan.limits.length > 0 && (
                      <ul className="mt-3 space-y-1.5">
                        {plan.limits.map((limit, i) => (
                          <li key={i} className="text-xs text-gray-600 flex items-start gap-1.5">
                            <span className="text-green-500 shrink-0 mt-px">✓</span>
                            <span>
                              {limit.title}
                              {limit.limit_value && (
                                <span className="text-gray-400">
                                  {' '}— {limit.limit_value}{limit.unit ? ` ${limit.unit}` : ''}
                                </span>
                              )}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  <Button
                    variant={isCurrent ? 'secondary' : 'primary'}
                    size="sm"
                    onClick={() => onSelect(plan, isAnnual)}
                    className="shrink-0 mt-1"
                  >
                    {isCurrent ? 'Selecionado' : 'Selecionar'}
                  </Button>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// ─── Confirm Subscription Modal ───────────────────────────────────────────────

type ConfirmModalProps = {
  selected: SelectedConfig[]
  profileName: string
  profileType: 'individual' | 'business'
  isLoading: boolean
  onConfirm: () => void
  onClose: () => void
}

function ConfirmSubscriptionModal({
  selected,
  profileName,
  profileType,
  isLoading,
  onConfirm,
  onClose,
}: ConfirmModalProps) {
  const monthlyItems = selected.filter(c => !c.isAnnual)
  const annualItems  = selected.filter(c => c.isAnnual)
  const monthlyTotal = monthlyItems.reduce((s, c) => s + c.plan.monthly_price, 0)
  const annualTotal  = annualItems.reduce((s, c) => s + planAnnualTotal(c.plan), 0)
  const annualInstallment = annualItems.reduce((s, c) => s + c.plan.annual_monthly_price, 0)

  const isIndividual = profileType === 'individual'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={!isLoading ? onClose : undefined} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="border-b border-gray-100 px-6 py-5 shrink-0">
          <h2 className="text-lg font-bold text-gray-900">Confirmar assinatura</h2>
          <p className="mt-1 text-sm text-gray-500">
            {isIndividual
              ? 'Você está prestes a adicionar os módulos selecionados ao seu Perfil Individual.'
              : `Você está prestes a adicionar os módulos selecionados ao perfil:`}
          </p>
          {!isIndividual && (
            <p className="mt-1 text-sm font-bold text-gray-800">{profileName}</p>
          )}
        </div>

        {/* Body */}
        <div className="overflow-y-auto px-6 py-5 space-y-5">
          {/* Profile info */}
          <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 space-y-1">
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Perfil</span>
              <span className="font-semibold text-gray-800">{profileName}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Tipo de perfil</span>
              <span className="font-medium text-gray-700">
                {isIndividual ? 'Individual' : 'Empresarial'}
              </span>
            </div>
          </div>

          {/* Modules summary */}
          <div>
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">
              Módulos selecionados
            </p>
            <ul className="space-y-2">
              {selected.map(({ module, plan, isAnnual }) => {
                const price = isAnnual
                  ? `${brl(planAnnualTotal(plan))}/ano`
                  : `${brl(plan.monthly_price)}/mês`
                return (
                  <li key={module.module_id} className="flex items-start justify-between gap-3 text-sm">
                    <div>
                      <p className="font-semibold text-gray-800">{module.module_name}</p>
                      <p className="text-gray-500">
                        Plano {plan.plan_name} · {isAnnual ? 'Anual' : 'Mensal'}
                      </p>
                    </div>
                    <span className="font-bold text-gray-900 whitespace-nowrap">{price}</span>
                  </li>
                )
              })}
            </ul>
          </div>

          {/* Financial summary */}
          <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 space-y-2">
            {monthlyItems.length > 0 && (
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Cobranças mensais</span>
                <span className="font-bold text-gray-900">
                  {monthlyTotal === 0 ? 'Grátis' : `${brl(monthlyTotal)}/mês`}
                </span>
              </div>
            )}
            {annualItems.length > 0 && (
              <>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Cobranças anuais</span>
                  <span className="font-bold text-gray-900">
                    {annualTotal === 0 ? 'Grátis' : `${brl(annualTotal)}/ano`}
                  </span>
                </div>
                {annualInstallment > 0 && (
                  <p className="text-xs text-gray-400 text-right">
                    em até 12x de {brl(annualInstallment)}
                  </p>
                )}
              </>
            )}
          </div>

          <p className="text-xs text-gray-400 leading-relaxed">
            {isIndividual
              ? 'Esses módulos ficarão disponíveis apenas para o seu uso individual.'
              : `Esses módulos ficarão disponíveis apenas para ${profileName} e seus usuários autorizados.`}
          </p>
        </div>

        {/* Footer */}
        <div className="border-t border-gray-100 px-6 py-4 flex gap-3 shrink-0">
          <Button
            variant="secondary"
            className="flex-1"
            onClick={onClose}
            disabled={isLoading}
          >
            Cancelar
          </Button>
          <Button
            variant="primary"
            className="flex-1"
            onClick={onConfirm}
            disabled={isLoading}
          >
            {isLoading ? 'Confirmando...' : 'Confirmar assinatura'}
          </Button>
        </div>
      </div>
    </div>
  )
}

// ─── Configuration Panel ──────────────────────────────────────────────────────

type ConfigPanelProps = {
  selected: SelectedConfig[]
  onRemove: (moduleId: string) => void
  onConfirm: () => void
}

function ConfigPanel({ selected, onRemove, onConfirm }: ConfigPanelProps) {
  const monthlyItems = selected.filter(c => !c.isAnnual)
  const annualItems  = selected.filter(c => c.isAnnual)
  const monthlyTotal = monthlyItems.reduce((s, c) => s + c.plan.monthly_price, 0)
  const annualTotalSum = annualItems.reduce((s, c) => s + planAnnualTotal(c.plan), 0)
  const annualInstallmentTotal = annualItems.reduce((s, c) => s + c.plan.annual_monthly_price, 0)
  const hasMonthly = monthlyItems.length > 0
  const hasAnnual  = annualItems.length > 0
  const hasBoth    = hasMonthly && hasAnnual

  return (
    <div className="lg:w-80 shrink-0 rounded-2xl border border-gray-200 bg-white shadow-sm p-6 lg:sticky lg:top-6 space-y-5">
      <h2 className="text-base font-bold text-gray-900">Minha configuração</h2>

      {hasMonthly && (
        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Cobranças mensais</p>
          <ul className="space-y-4">
            {monthlyItems.map(({ module, plan }) => (
              <li key={module.module_id} className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-gray-800 truncate">{module.module_name}</p>
                  <p className="text-xs text-gray-500">Plano {plan.plan_name}</p>
                  <p className="text-sm font-bold text-gray-900 mt-1">
                    {plan.monthly_price === 0 ? 'Grátis' : `${brl(plan.monthly_price)}/mês`}
                  </p>
                  {plan.monthly_price > 0 && (
                    <p className="text-xs text-gray-400">cobrança mensal</p>
                  )}
                  <span className="inline-block mt-1.5 rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">
                    Mensal
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => onRemove(module.module_id)}
                  className="text-gray-300 hover:text-red-400 transition-colors shrink-0 mt-0.5 text-sm leading-none"
                  title="Remover módulo"
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {hasBoth && <div className="border-t border-gray-100" />}

      {hasAnnual && (
        <div className="space-y-3">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Cobranças anuais</p>
          <ul className="space-y-4">
            {annualItems.map(({ module, plan }) => {
              const total = planAnnualTotal(plan)
              return (
                <li key={module.module_id} className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-gray-800 truncate">{module.module_name}</p>
                    <p className="text-xs text-gray-500">Plano {plan.plan_name}</p>
                    {total === 0 ? (
                      <p className="text-sm font-bold text-gray-900 mt-1">Grátis</p>
                    ) : (
                      <>
                        <p className="text-sm font-bold text-gray-900 mt-1">{brl(total)}/ano</p>
                        <p className="text-xs text-gray-500">em até 12x de {brl(plan.annual_monthly_price)}</p>
                      </>
                    )}
                    <span className="inline-block mt-1.5 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">
                      Anual
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => onRemove(module.module_id)}
                    className="text-gray-300 hover:text-red-400 transition-colors shrink-0 mt-0.5 text-sm leading-none"
                    title="Remover módulo"
                  >
                    ✕
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}

      <div className="border-t border-gray-100 pt-4 space-y-2">
        {hasMonthly && (
          <div className="flex justify-between items-baseline">
            <span className="text-xs text-gray-500">Cobranças mensais</span>
            <span className="text-sm font-semibold text-gray-900">
              {monthlyTotal === 0 ? 'Grátis' : `${brl(monthlyTotal)}/mês`}
            </span>
          </div>
        )}
        {hasAnnual && (
          <div className="space-y-0.5">
            <div className="flex justify-between items-baseline">
              <span className="text-xs text-gray-500">Cobranças anuais</span>
              <span className="text-sm font-semibold text-gray-900">
                {annualTotalSum === 0 ? 'Grátis' : `${brl(annualTotalSum)}/ano`}
              </span>
            </div>
            {annualInstallmentTotal > 0 && (
              <div className="flex justify-end">
                <span className="text-xs text-gray-400">em até 12x de {brl(annualInstallmentTotal)}</span>
              </div>
            )}
          </div>
        )}
        {hasAnnual && (
          <p className="text-xs text-gray-400 leading-relaxed">
            Módulos anuais podem ser pagos em até 12 vezes.
          </p>
        )}
      </div>

      <Button className="w-full" variant="primary" onClick={onConfirm}>
        Confirmar assinatura
      </Button>
    </div>
  )
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

export function PlansPage() {
  const { currentTenant } = useTenant()
  const [modalModule, setModalModule] = useState<ModuleBillingOption | null>(null)
  const [configuration, setConfiguration] = useState<Record<string, SelectedConfig>>({})
  const [showConfirmModal, setShowConfirmModal] = useState(false)

  const profileName = currentTenant?.tenant.name ?? 'Seu perfil'
  const profileType = currentTenant?.tenant.type ?? 'individual'

  const { data: modules = [], isLoading } = useQuery({
    queryKey: ['module-billing-options'],
    queryFn: async () => {
      const { data } = await api.get<ModuleBillingOptionRaw[]>('/api/v1/public/modules/billing-options')
      return data.map(raw => ({
        module_id: raw.module_id,
        module_name: raw.module_name,
        module_slug: raw.module_slug,
        module_description: raw.module_description,
        icon_path: raw.icon_path,
        services: parseJson<ModuleService[]>(raw.services_json) ?? [],
        available_plans: parseJson<ModulePlan[]>(raw.available_plans_json) ?? [],
      })) as ModuleBillingOption[]
    },
  })

  const confirmMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        modules: selectedList.map(({ module, plan, isAnnual }) => ({
          module_id: module.module_id,
          plan_version_id: plan.plan_version_id,
          billing_cycle: isAnnual ? 'ANNUAL' : 'MONTHLY',
        })),
      }
      await api.post('/api/v1/subscriptions/modules', payload)
    },
    onSuccess: () => {
      setShowConfirmModal(false)
      setConfiguration({})
    },
  })

  function selectPlan(module: ModuleBillingOption, plan: ModulePlan, isAnnual: boolean) {
    setConfiguration(prev => ({ ...prev, [module.module_id]: { module, plan, isAnnual } }))
    setModalModule(null)
  }

  function removeModule(moduleId: string) {
    setConfiguration(prev => {
      const next = { ...prev }
      delete next[moduleId]
      return next
    })
  }

  const selectedList = Object.values(configuration)
  const hasConfig = selectedList.length > 0

  return (
    <div className="space-y-6">
      {/* Active profile banner */}
      {currentTenant && (
        <ProfileBanner profileName={profileName} profileType={profileType} />
      )}

      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-2xl font-bold text-gray-900">Escolha seus módulos</h1>
        <p className="text-gray-500">
          Monte sua assinatura com os módulos que você precisa, cada um no plano e ciclo ideal.
        </p>
      </div>

      {/* Main layout */}
      <div className={`flex gap-6 items-start ${hasConfig ? 'flex-col lg:flex-row' : ''}`}>
        <div className="flex-1 min-w-0">
          {isLoading ? (
            <div className="text-center py-16 text-gray-400 text-sm">Carregando módulos...</div>
          ) : modules.length === 0 ? (
            <div className="text-center py-16 text-gray-400 text-sm">
              Nenhum módulo disponível no momento.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
              {modules.map(module => (
                <ModuleCard
                  key={module.module_id}
                  module={module}
                  selected={configuration[module.module_id]}
                  onChoose={() => setModalModule(module)}
                />
              ))}
            </div>
          )}
        </div>

        {hasConfig && (
          <ConfigPanel
            selected={selectedList}
            onRemove={removeModule}
            onConfirm={() => setShowConfirmModal(true)}
          />
        )}
      </div>

      {/* Footer note */}
      <p className="text-center text-xs text-gray-400">
        Todos os módulos incluem 14 dias grátis para testar. Pagamentos seguros e encriptados.
      </p>

      {/* Plan selection modal */}
      {modalModule && (
        <PlanModal
          module={modalModule}
          initialIsAnnual={configuration[modalModule.module_id]?.isAnnual ?? false}
          currentPlanId={configuration[modalModule.module_id]?.plan.plan_id}
          onSelect={(plan, isAnnual) => selectPlan(modalModule, plan, isAnnual)}
          onClose={() => setModalModule(null)}
        />
      )}

      {/* Confirmation modal */}
      {showConfirmModal && (
        <ConfirmSubscriptionModal
          selected={selectedList}
          profileName={profileName}
          profileType={profileType}
          isLoading={confirmMutation.isPending}
          onConfirm={() => confirmMutation.mutate()}
          onClose={() => setShowConfirmModal(false)}
        />
      )}
    </div>
  )
}
