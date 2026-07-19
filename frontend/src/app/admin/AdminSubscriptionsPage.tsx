import { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'

// ─── Types ────────────────────────────────────────────────────────────────────

type SubscriptionStatus = 'ACTIVE' | 'PENDING_PAYMENT' | 'CANCELED' | 'EXPIRED' | 'TRIAL' | 'TRIAL_CANCELLED'
type BillingCycle = 'MONTHLY' | 'ANNUAL'
type ProfileType = 'INDIVIDUAL' | 'COMPANY'

interface AdminSubscriptionListItem {
  id: string
  profileId: string
  profileName: string
  profileType: ProfileType
  companyId: string | null
  companyName: string | null
  companySlug: string | null
  ownerUserId: string | null
  ownerName: string | null
  ownerEmail: string | null
  moduleId: string
  moduleName: string
  moduleIconPath: string | null
  planId: string
  planName: string
  planVersionId: string
  planVersionNumber: number | null
  billingCycle: BillingCycle
  price: number
  annualTotalPrice: number | null
  status: SubscriptionStatus
  startedAt: string
  expiresAt: string | null
  canceledAt: string | null
  renewalActive: boolean
}

interface AdminSubscriptionPage {
  items: AdminSubscriptionListItem[]
  total: number
  page: number
  size: number
}

interface AdminSubscriptionSummary {
  total: number
  active: number
  monthly: number
  annual: number
  canceled: number
  expired: number
  pendingPayment: number
  trial: number
  trialCancelled: number
}

interface Filters {
  search: string
  profileType: '' | 'INDIVIDUAL' | 'COMPANY'
  billingCycle: '' | BillingCycle
  status: '' | SubscriptionStatus
  expiresIn: '' | '7' | '15' | '30' | 'overdue' | 'none'
  renewalStatus: '' | 'active' | 'canceled'
  startDateFrom: string
  startDateTo: string
}

const EMPTY_FILTERS: Filters = {
  search:        '',
  profileType:   '',
  billingCycle:  '',
  status:        '',
  expiresIn:     '',
  renewalStatus: '',
  startDateFrom: '',
  startDateTo:   '',
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function brl(v: number | null, cycle?: BillingCycle) {
  if (v == null) return '—'
  if (v === 0) return 'Grátis'
  const formatted = v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  if (cycle === 'MONTHLY') return `R$ ${formatted}/mês`
  if (cycle === 'ANNUAL')  return `R$ ${formatted}/ano`
  return `R$ ${formatted}`
}

function fmtDate(d: string | null) {
  if (!d) return '—'
  return new Date(d).toLocaleDateString('pt-BR')
}

/** Planos sem expiração (ex.: Free) são sempre válidos para reativação */
function isStillValid(expiresAt: string | null) {
  if (!expiresAt) return true
  return new Date(expiresAt) > new Date()
}

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<SubscriptionStatus, string> = {
  ACTIVE:          'bg-green-900 text-green-200',
  PENDING_PAYMENT: 'bg-yellow-900 text-yellow-200',
  CANCELED:        'bg-red-900 text-red-300',
  EXPIRED:         'bg-gray-700 text-gray-400',
  TRIAL:           'bg-purple-900 text-purple-200',
  TRIAL_CANCELLED: 'bg-purple-950 text-purple-400',
}

const STATUS_LABELS: Record<SubscriptionStatus, string> = {
  ACTIVE:          'Ativa',
  PENDING_PAYMENT: 'Pend. pagamento',
  CANCELED:        'Cancelada',
  EXPIRED:         'Expirada',
  TRIAL:           '🧪 Trial',
  TRIAL_CANCELLED: '🧪 Trial (cancelado)',
}

function StatusBadge({ status }: { status: SubscriptionStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[status] ?? 'bg-gray-700 text-gray-400'}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  )
}

// ─── Detail Modal ─────────────────────────────────────────────────────────────

function DetailModal({
  sub,
  onClose,
  onCancel,
  onReactivate,
}: {
  sub: AdminSubscriptionListItem
  onClose: () => void
  onCancel: (id: string) => void
  onReactivate: (id: string) => void
}) {
  const { hasAdminPermission } = useAuth()
  const isAnnual     = sub.billingCycle === 'ANNUAL'
  const canCancel    = sub.status === 'ACTIVE' && hasAdminPermission('admin.subscriptions.cancel')
  const canReactivate = sub.status === 'CANCELED' && isStillValid(sub.expiresAt) && hasAdminPermission('admin.subscriptions.reactivate')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60" onClick={onClose} />
      <div className="relative bg-gray-800 border border-gray-700 rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700">
          <h2 className="text-base font-bold text-white">Detalhe da assinatura</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white transition-colors">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="px-6 py-5 space-y-5">
          {/* Perfil */}
          <section>
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Perfil</p>
            <div className="space-y-1.5 text-sm">
              <Row label="Nome"  value={sub.profileName} />
              <Row label="Tipo"  value={sub.profileType === 'COMPANY' ? 'Empresarial' : 'Individual'} />
              {sub.companyName && <Row label="Empresa" value={sub.companyName} />}
              {sub.companySlug && <Row label="Slug"    value={sub.companySlug} />}
            </div>
          </section>

          {/* Usuário responsável */}
          <section>
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Responsável</p>
            <div className="space-y-1.5 text-sm">
              <Row label="Nome"  value={sub.ownerName  ?? '—'} />
              <Row label="Email" value={sub.ownerEmail ?? '—'} />
            </div>
          </section>

          {/* Módulo e plano */}
          <section>
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Módulo e Plano</p>
            <div className="space-y-1.5 text-sm">
              <Row label="Módulo" value={sub.moduleName} />
              <Row label="Plano"  value={`${sub.planName}${sub.planVersionNumber != null ? ` (v${sub.planVersionNumber})` : ''}`} />
              <Row label="Ciclo"  value={isAnnual ? 'Anual' : 'Mensal'} />
              <Row label="Valor"  value={brl(sub.price, sub.billingCycle)} />
              {isAnnual && sub.annualTotalPrice != null && sub.annualTotalPrice > 0 && (
                <Row label="Total anual" value={brl(sub.annualTotalPrice)} />
              )}
            </div>
          </section>

          {/* Status e datas */}
          <section>
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Status e Datas</p>
            <div className="space-y-1.5 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-400">Status</span>
                <StatusBadge status={sub.status} />
              </div>
              <Row label="Início"      value={fmtDate(sub.startedAt)} />
              <Row label="Vencimento"  value={fmtDate(sub.expiresAt)} />
              {sub.canceledAt && <Row label="Cancelado em" value={fmtDate(sub.canceledAt)} />}
              <Row label="Renovação"   value={sub.renewalActive ? 'Ativa' : 'Cancelada'} />
            </div>
          </section>
        </div>

        {(canCancel || canReactivate) && (
          <div className="px-6 py-4 border-t border-gray-700 flex gap-3">
            {canReactivate && (
              <button
                onClick={() => { onReactivate(sub.id); onClose() }}
                className="flex-1 rounded-lg bg-green-700 hover:bg-green-600 text-white text-sm font-semibold py-2 transition-colors"
              >
                Reativar assinatura
              </button>
            )}
            {canCancel && (
              <button
                onClick={() => { onCancel(sub.id); onClose() }}
                className="flex-1 rounded-lg bg-red-700 hover:bg-red-600 text-white text-sm font-semibold py-2 transition-colors"
              >
                Cancelar assinatura
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-gray-400 shrink-0">{label}</span>
      <span className="text-gray-200 text-right">{value}</span>
    </div>
  )
}

// ─── Summary Cards ────────────────────────────────────────────────────────────

function SummaryCards({ summary }: { summary: AdminSubscriptionSummary | undefined }) {
  const cards = [
    { label: 'Total',          value: summary?.total,          color: 'text-white' },
    { label: 'Ativas',         value: summary?.active,         color: 'text-green-400' },
    { label: '🧪 Trial',       value: summary?.trial,          color: 'text-purple-400' },
    { label: 'Mensais',        value: summary?.monthly,        color: 'text-blue-400' },
    { label: 'Anuais',         value: summary?.annual,         color: 'text-purple-400' },
    { label: 'Canceladas',     value: summary?.canceled,       color: 'text-red-400' },
    { label: 'Expiradas',      value: summary?.expired,        color: 'text-gray-400' },
    { label: 'Pend. Pagamento',value: summary?.pendingPayment, color: 'text-yellow-400' },
  ]
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
      {cards.map((c) => (
        <div key={c.label} className="rounded-xl bg-gray-800 border border-gray-700 px-4 py-3">
          <p className="text-xs text-gray-400 truncate">{c.label}</p>
          <p className={`mt-1 text-2xl font-bold ${c.color}`}>
            {c.value ?? '—'}
          </p>
        </div>
      ))}
    </div>
  )
}

// ─── Filters Panel ────────────────────────────────────────────────────────────

function FiltersPanel({
  filters,
  onChange,
  onReset,
}: {
  filters: Filters
  onChange: (patch: Partial<Filters>) => void
  onReset: () => void
}) {
  const hasActive = Object.values(filters).some(v => v !== '')

  return (
    <div className="rounded-xl bg-gray-800 border border-gray-700 px-4 py-4 space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">Filtros</p>
        {hasActive && (
          <button onClick={onReset} className="text-xs text-blue-400 hover:text-blue-300 transition-colors">
            Limpar filtros
          </button>
        )}
      </div>

      {/* Row 1 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <input
          type="text"
          placeholder="Buscar perfil, módulo, plano, usuário..."
          value={filters.search}
          onChange={e => onChange({ search: e.target.value })}
          className="col-span-1 sm:col-span-2 rounded-lg bg-gray-700 border border-gray-600 text-gray-100 placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
        />
        <Select
          value={filters.profileType}
          onChange={v => onChange({ profileType: v as Filters['profileType'] })}
          options={[
            { value: '', label: 'Tipo de perfil' },
            { value: 'INDIVIDUAL', label: 'Individual' },
            { value: 'COMPANY', label: 'Empresarial' },
          ]}
        />
        <Select
          value={filters.billingCycle}
          onChange={v => onChange({ billingCycle: v as Filters['billingCycle'] })}
          options={[
            { value: '', label: 'Ciclo de cobrança' },
            { value: 'MONTHLY', label: 'Mensal' },
            { value: 'ANNUAL', label: 'Anual' },
          ]}
        />
      </div>

      {/* Row 2 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <Select
          value={filters.status}
          onChange={v => onChange({ status: v as Filters['status'] })}
          options={[
            { value: '', label: 'Status' },
            { value: 'ACTIVE', label: 'Ativa' },
            { value: 'TRIAL', label: '🧪 Trial' },
            { value: 'TRIAL_CANCELLED', label: '🧪 Trial (cancelado)' },
            { value: 'PENDING_PAYMENT', label: 'Pendente pagamento' },
            { value: 'CANCELED', label: 'Cancelada' },
            { value: 'EXPIRED', label: 'Expirada' },
          ]}
        />
        <Select
          value={filters.expiresIn}
          onChange={v => onChange({ expiresIn: v as Filters['expiresIn'] })}
          options={[
            { value: '', label: 'Vencimento' },
            { value: '7', label: 'Vence em 7 dias' },
            { value: '15', label: 'Vence em 15 dias' },
            { value: '30', label: 'Vence em 30 dias' },
            { value: 'overdue', label: 'Vencidas' },
            { value: 'none', label: 'Sem vencimento' },
          ]}
        />
        <Select
          value={filters.renewalStatus}
          onChange={v => onChange({ renewalStatus: v as Filters['renewalStatus'] })}
          options={[
            { value: '', label: 'Renovação' },
            { value: 'active', label: 'Renovação ativa' },
            { value: 'canceled', label: 'Renovação cancelada' },
          ]}
        />
        <div className="flex gap-2">
          <input
            type="date"
            value={filters.startDateFrom}
            onChange={e => onChange({ startDateFrom: e.target.value })}
            title="Data início (de)"
            className="flex-1 rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-2 py-2 focus:outline-none focus:border-blue-500"
          />
          <input
            type="date"
            value={filters.startDateTo}
            onChange={e => onChange({ startDateTo: e.target.value })}
            title="Data início (até)"
            className="flex-1 rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-2 py-2 focus:outline-none focus:border-blue-500"
          />
        </div>
      </div>
    </div>
  )
}

function Select({
  value,
  onChange,
  options,
}: {
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
    >
      {options.map(o => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20

export function AdminSubscriptionsPage() {
  const queryClient = useQueryClient()

  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage]       = useState(0)
  const [detail, setDetail]   = useState<AdminSubscriptionListItem | null>(null)

  // Debounce search to avoid too many requests
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (searchTimer.current) clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => setDebouncedSearch(filters.search), 350)
    return () => { if (searchTimer.current) clearTimeout(searchTimer.current) }
  }, [filters.search])

  // Reset page on filter change
  useEffect(() => { setPage(0) }, [filters])

  const activeFilters = { ...filters, search: debouncedSearch }

  function buildParams() {
    const p: Record<string, string> = { page: String(page), size: String(PAGE_SIZE) }
    if (activeFilters.search)        p.search        = activeFilters.search
    if (activeFilters.profileType)   p.profileType   = activeFilters.profileType
    if (activeFilters.billingCycle)  p.billingCycle  = activeFilters.billingCycle
    if (activeFilters.status)        p.status        = activeFilters.status
    if (activeFilters.expiresIn)     p.expiresIn     = activeFilters.expiresIn
    if (activeFilters.renewalStatus) p.renewalStatus = activeFilters.renewalStatus
    if (activeFilters.startDateFrom) p.startDateFrom = activeFilters.startDateFrom
    if (activeFilters.startDateTo)   p.startDateTo   = activeFilters.startDateTo
    return p
  }

  const { data: summary } = useQuery({
    queryKey: ['admin-subscriptions-summary'],
    queryFn: async () => {
      const { data } = await api.get<AdminSubscriptionSummary>('/api/v1/admin/subscriptions/summary')
      return data
    },
    staleTime: 60_000,
  })

  const { data: result, isLoading } = useQuery({
    queryKey: ['admin-subscriptions', activeFilters, page],
    queryFn: async () => {
      const { data } = await api.get<AdminSubscriptionPage>('/api/v1/admin/subscriptions', {
        params: buildParams(),
      })
      return data
    },
    staleTime: 30_000,
    placeholderData: (prev) => prev,
  })

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['admin-subscriptions'] })
    queryClient.invalidateQueries({ queryKey: ['admin-subscriptions-summary'] })
  }, [queryClient])

  const cancelMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/admin/subscriptions/${id}/cancel`),
    onSuccess: invalidate,
  })

  const reactivateMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/admin/subscriptions/${id}/reactivate`),
    onSuccess: invalidate,
  })

  const items     = result?.items ?? []
  const total     = result?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  const patchFilters = useCallback((patch: Partial<Filters>) => {
    setFilters(prev => ({ ...prev, ...patch }))
  }, [])

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white">Assinaturas</h1>
        <p className="mt-1 text-sm text-gray-400">
          Assinaturas de módulos por perfil. Cada linha representa um módulo contratado por um perfil específico.
        </p>
      </div>

      {/* Summary cards */}
      <SummaryCards summary={summary} />

      {/* Filters */}
      <FiltersPanel
        filters={filters}
        onChange={patchFilters}
        onReset={() => setFilters(EMPTY_FILTERS)}
      />

      {/* Table */}
      {isLoading ? (
        <p className="text-gray-400 text-sm py-4">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700 flex items-center justify-between">
            <span className="text-sm text-gray-400">
              {total} assinatura{total !== 1 ? 's' : ''}
              {totalPages > 1 && ` — página ${page + 1} de ${totalPages}`}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-700 text-sm">
              <thead>
                <tr>
                  {['Perfil', 'Responsável', 'Módulo', 'Plano', 'Ciclo', 'Valor', 'Status', 'Início', 'Vencimento', 'Renovação', ''].map((h) => (
                    <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700">
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={11} className="px-4 py-12 text-center text-gray-500">
                      Nenhuma assinatura encontrada com os filtros aplicados.
                    </td>
                  </tr>
                ) : (
                  items.map((sub) => <SubscriptionRow key={sub.id} sub={sub} onView={setDetail} />)
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="px-4 py-3 border-t border-gray-700 flex items-center justify-between">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="rounded-lg px-3 py-1.5 text-xs font-medium bg-gray-700 text-gray-300 hover:bg-gray-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                ← Anterior
              </button>
              <span className="text-xs text-gray-400">
                {page + 1} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                className="rounded-lg px-3 py-1.5 text-xs font-medium bg-gray-700 text-gray-300 hover:bg-gray-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Próxima →
              </button>
            </div>
          )}
        </div>
      )}

      {/* Detail Modal */}
      {detail && (
        <DetailModal
          sub={detail}
          onClose={() => setDetail(null)}
          onCancel={(id) => cancelMutation.mutate(id)}
          onReactivate={(id) => reactivateMutation.mutate(id)}
        />
      )}
    </div>
  )
}

// ─── Subscription Row ─────────────────────────────────────────────────────────

function SubscriptionRow({
  sub,
  onView,
}: {
  sub: AdminSubscriptionListItem
  onView: (sub: AdminSubscriptionListItem) => void
}) {
  const isAnnual = sub.billingCycle === 'ANNUAL'

  return (
    <tr
      className="hover:bg-gray-750 cursor-pointer transition-colors"
      onClick={() => onView(sub)}
    >
      {/* Perfil */}
      <td className="px-3 py-3 whitespace-nowrap">
        <p className="font-medium text-white">{sub.profileName}</p>
        <span className={`inline-block mt-0.5 rounded-full px-1.5 py-0 text-[10px] font-medium ${
          sub.profileType === 'COMPANY'
            ? 'bg-amber-900 text-amber-200'
            : 'bg-blue-900 text-blue-200'
        }`}>
          {sub.profileType === 'COMPANY' ? 'Empresarial' : 'Individual'}
        </span>
      </td>

      {/* Responsável */}
      <td className="px-3 py-3">
        <p className="text-gray-300 truncate max-w-[140px]">{sub.ownerName ?? '—'}</p>
        <p className="text-xs text-gray-500 truncate max-w-[140px]">{sub.ownerEmail ?? ''}</p>
      </td>

      {/* Módulo */}
      <td className="px-3 py-3 whitespace-nowrap">
        <span className="text-gray-200">{sub.moduleName}</span>
      </td>

      {/* Plano */}
      <td className="px-3 py-3 whitespace-nowrap">
        <p className="text-gray-300">{sub.planName}</p>
        {sub.planVersionNumber != null && (
          <p className="text-xs text-gray-500">v{sub.planVersionNumber}</p>
        )}
      </td>

      {/* Ciclo */}
      <td className="px-3 py-3 whitespace-nowrap text-gray-400">
        {isAnnual ? 'Anual' : 'Mensal'}
      </td>

      {/* Valor */}
      <td className="px-3 py-3 whitespace-nowrap">
        <p className="text-gray-300">{brl(sub.price, sub.billingCycle)}</p>
        {isAnnual && sub.annualTotalPrice != null && sub.annualTotalPrice > 0 && (
          <p className="text-xs text-gray-500">em até 12x</p>
        )}
      </td>

      {/* Status */}
      <td className="px-3 py-3 whitespace-nowrap">
        <StatusBadge status={sub.status} />
      </td>

      {/* Início */}
      <td className="px-3 py-3 whitespace-nowrap text-gray-400">
        {fmtDate(sub.startedAt)}
      </td>

      {/* Vencimento */}
      <td className="px-3 py-3 whitespace-nowrap text-gray-400">
        {fmtDate(sub.expiresAt)}
      </td>

      {/* Renovação */}
      <td className="px-3 py-3 whitespace-nowrap">
        <span className={`text-xs font-medium ${sub.renewalActive ? 'text-green-400' : 'text-gray-500'}`}>
          {sub.renewalActive ? '● Ativa' : '○ Cancelada'}
        </span>
      </td>

      {/* Ação: ver detalhes */}
      <td className="px-3 py-3 whitespace-nowrap text-right">
        <button
          onClick={(e) => { e.stopPropagation(); onView(sub) }}
          className="text-xs text-blue-400 hover:text-blue-300 font-medium transition-colors"
        >
          Ver detalhes
        </button>
      </td>
    </tr>
  )
}
