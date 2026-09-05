import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/shared/services/adminApi'
import { AdminPaymentDetailDrawer } from './AdminPaymentDetailDrawer'

// ─── Types ────────────────────────────────────────────────────────────────────

type PaymentStatus = 'PENDING' | 'PROCESSING' | 'AUTHORIZED' | 'PAID' | 'FAILED' | 'CANCELLED' | 'REFUNDED' | 'EXPIRED' | 'OVERDUE'
type PaymentType = 'ONE_TIME' | 'RECURRING'
type BillingCycle = 'MONTHLY' | 'ANNUAL'

interface AdminPaymentListItem {
  id: string
  externalId: string | null
  subscriptionId: string | null
  customerId: string
  customerName: string | null
  customerEmail: string | null
  moduleId: string | null
  moduleName: string | null
  type: PaymentType
  billingCycle: BillingCycle | null
  gateway: string
  paymentMethod: string | null
  amount: number
  currency: string
  status: PaymentStatus
  createdAt: string
  lastEventType: string | null
  lastEventStatus: string | null
  lastEventAt: string | null
  hasWebhookError: boolean
}

interface AdminPaymentPage {
  items: AdminPaymentListItem[]
  total: number
  page: number
  size: number
}

interface AdminPaymentSummary {
  total: number
  paid: number
  pending: number
  failed: number
  overdue: number
  amountReceived: number
  amountPending: number
  withWebhookError: number
}

interface Filters {
  search: string
  id: string
  externalId: string
  subscriptionId: string
  customerId: string
  moduleId: string
  gateway: string
  paymentMethod: string
  type: '' | PaymentType
  billingCycle: '' | BillingCycle
  status: '' | PaymentStatus
  amountMin: string
  amountMax: string
  dateFrom: string
  dateTo: string
  hasWebhookError: '' | 'true' | 'false'
  lastWebhookStatus: string
}

const EMPTY_FILTERS: Filters = {
  search: '', id: '', externalId: '', subscriptionId: '', customerId: '', moduleId: '',
  gateway: '', paymentMethod: '', type: '', billingCycle: '', status: '',
  amountMin: '', amountMax: '', dateFrom: '', dateTo: '', hasWebhookError: '', lastWebhookStatus: '',
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function brl(v: number | null, currency?: string) {
  if (v == null) return '—'
  const formatted = v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return `${currency === 'BRL' || !currency ? 'R$' : currency} ${formatted}`
}

function fmtDateTime(d: string | null) {
  if (!d) return '—'
  return new Date(d).toLocaleString('pt-BR')
}

// ─── Badges ───────────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<PaymentStatus, string> = {
  PENDING:    'bg-yellow-900 text-yellow-200',
  PROCESSING: 'bg-yellow-900 text-yellow-200',
  AUTHORIZED: 'bg-yellow-900 text-yellow-200',
  PAID:       'bg-green-900 text-green-200',
  FAILED:     'bg-red-900 text-red-300',
  CANCELLED:  'bg-red-900 text-red-300',
  REFUNDED:   'bg-purple-900 text-purple-200',
  EXPIRED:    'bg-gray-700 text-gray-400',
  OVERDUE:    'bg-gray-700 text-gray-400',
}

const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING:    'Pendente',
  PROCESSING: 'Processando',
  AUTHORIZED: 'Autorizado',
  PAID:       'Pago',
  FAILED:     'Falhou',
  CANCELLED:  'Cancelado',
  REFUNDED:   'Reembolsado',
  EXPIRED:    'Expirado',
  OVERDUE:    'Vencido',
}

function StatusBadge({ status }: { status: PaymentStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[status] ?? 'bg-gray-700 text-gray-400'}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  )
}

function GatewayBadge({ gateway }: { gateway: string }) {
  return (
    <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-blue-900 text-blue-200">
      {gateway}
    </span>
  )
}

const METHOD_LABELS: Record<string, string> = {
  CREDIT_CARD: 'Cartão',
  PIX: 'PIX',
  BOLETO: 'Boleto',
  BANK_TRANSFER: 'Transferência',
  UNKNOWN: 'Outro',
}

// ─── Summary Cards ────────────────────────────────────────────────────────────

function SummaryCards({ summary }: { summary: AdminPaymentSummary | undefined }) {
  const cards = [
    { label: 'Total',              value: summary?.total,                                color: 'text-white' },
    { label: 'Pagos',               value: summary?.paid,                                color: 'text-green-400' },
    { label: 'Pendentes',           value: summary?.pending,                             color: 'text-yellow-400' },
    { label: 'Falhos',              value: summary?.failed,                              color: 'text-red-400' },
    { label: 'Vencidos',            value: summary?.overdue,                             color: 'text-gray-400' },
    { label: 'Valor recebido',      value: summary ? brl(summary.amountReceived) : undefined, color: 'text-green-400' },
    { label: 'Valor pendente',      value: summary ? brl(summary.amountPending) : undefined,  color: 'text-yellow-400' },
    { label: 'Erro de webhook',     value: summary?.withWebhookError,                    color: 'text-red-400' },
  ]
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
      {cards.map((c) => (
        <div key={c.label} className="rounded-xl bg-gray-800 border border-gray-700 px-4 py-3">
          <p className="text-xs text-gray-400 truncate">{c.label}</p>
          <p className={`mt-1 text-xl font-bold ${c.color}`}>
            {c.value ?? '—'}
          </p>
        </div>
      ))}
    </div>
  )
}

// ─── Filters Panel ────────────────────────────────────────────────────────────

function Select({
  value, onChange, options,
}: {
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

function TextInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <input
      type="text"
      placeholder={placeholder}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
    />
  )
}

function FiltersPanel({
  filters, onChange, onReset,
}: {
  filters: Filters
  onChange: (patch: Partial<Filters>) => void
  onReset: () => void
}) {
  const hasActive = Object.values(filters).some((v) => v !== '')

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

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <TextInput value={filters.search} onChange={(v) => onChange({ search: v })} placeholder="Buscar cliente, e-mail..." />
        <TextInput value={filters.id} onChange={(v) => onChange({ id: v })} placeholder="ID interno do pagamento" />
        <TextInput value={filters.externalId} onChange={(v) => onChange({ externalId: v })} placeholder="ID externo (gateway)" />
        <TextInput value={filters.subscriptionId} onChange={(v) => onChange({ subscriptionId: v })} placeholder="ID da assinatura" />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <TextInput value={filters.customerId} onChange={(v) => onChange({ customerId: v })} placeholder="ID do perfil/empresa" />
        <TextInput value={filters.moduleId} onChange={(v) => onChange({ moduleId: v })} placeholder="ID do módulo" />
        <Select
          value={filters.gateway}
          onChange={(v) => onChange({ gateway: v })}
          options={[
            { value: '', label: 'Todos os gateways' },
            { value: 'STRIPE', label: 'Stripe' },
            { value: 'ASAAS', label: 'Asaas' },
          ]}
        />
        <Select
          value={filters.paymentMethod}
          onChange={(v) => onChange({ paymentMethod: v })}
          options={[
            { value: '', label: 'Todos os métodos' },
            { value: 'CREDIT_CARD', label: 'Cartão' },
            { value: 'PIX', label: 'PIX' },
            { value: 'BOLETO', label: 'Boleto' },
            { value: 'BANK_TRANSFER', label: 'Transferência' },
            { value: 'UNKNOWN', label: 'Outro' },
          ]}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <Select
          value={filters.type}
          onChange={(v) => onChange({ type: v as Filters['type'] })}
          options={[
            { value: '', label: 'Todos os tipos' },
            { value: 'ONE_TIME', label: 'Pagamento único' },
            { value: 'RECURRING', label: 'Recorrente' },
          ]}
        />
        <Select
          value={filters.billingCycle}
          onChange={(v) => onChange({ billingCycle: v as Filters['billingCycle'] })}
          options={[
            { value: '', label: 'Mensal/Anual' },
            { value: 'MONTHLY', label: 'Mensal' },
            { value: 'ANNUAL', label: 'Anual' },
          ]}
        />
        <Select
          value={filters.status}
          onChange={(v) => onChange({ status: v as Filters['status'] })}
          options={[
            { value: '', label: 'Status' },
            { value: 'PENDING', label: 'Pendente' },
            { value: 'PROCESSING', label: 'Processando' },
            { value: 'AUTHORIZED', label: 'Autorizado' },
            { value: 'PAID', label: 'Pago' },
            { value: 'FAILED', label: 'Falhou' },
            { value: 'CANCELLED', label: 'Cancelado' },
            { value: 'REFUNDED', label: 'Reembolsado' },
            { value: 'EXPIRED', label: 'Expirado' },
            { value: 'OVERDUE', label: 'Vencido' },
          ]}
        />
        <Select
          value={filters.hasWebhookError}
          onChange={(v) => onChange({ hasWebhookError: v as Filters['hasWebhookError'] })}
          options={[
            { value: '', label: 'Erro de webhook: todos' },
            { value: 'true', label: 'Com erro de webhook' },
            { value: 'false', label: 'Sem erro de webhook' },
          ]}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <input
          type="number" step="0.01" placeholder="Valor mínimo"
          value={filters.amountMin}
          onChange={(e) => onChange({ amountMin: e.target.value })}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
        />
        <input
          type="number" step="0.01" placeholder="Valor máximo"
          value={filters.amountMax}
          onChange={(e) => onChange({ amountMax: e.target.value })}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
        />
        <input
          type="date" title="Data inicial"
          value={filters.dateFrom}
          onChange={(e) => onChange({ dateFrom: e.target.value })}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-2 py-2 focus:outline-none focus:border-blue-500"
        />
        <input
          type="date" title="Data final"
          value={filters.dateTo}
          onChange={(e) => onChange({ dateTo: e.target.value })}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-2 py-2 focus:outline-none focus:border-blue-500"
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <Select
          value={filters.lastWebhookStatus}
          onChange={(v) => onChange({ lastWebhookStatus: v })}
          options={[
            { value: '', label: 'Status do último webhook' },
            { value: 'RECEIVED', label: 'Pendente' },
            { value: 'PROCESSED', label: 'Processado' },
            { value: 'FAILED', label: 'Falhou' },
            { value: 'IGNORED', label: 'Ignorado' },
          ]}
        />
      </div>
    </div>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20

export function AdminPaymentsPage() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<string | null>(null)

  const [debouncedSearch, setDebouncedSearch] = useState('')
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (searchTimer.current) clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => setDebouncedSearch(filters.search), 350)
    return () => { if (searchTimer.current) clearTimeout(searchTimer.current) }
  }, [filters.search])

  useEffect(() => { setPage(0) }, [filters])

  const activeFilters = { ...filters, search: debouncedSearch }

  function buildParams() {
    const p: Record<string, string> = { page: String(page), size: String(PAGE_SIZE) }
    if (activeFilters.search) p.search = activeFilters.search
    if (activeFilters.id) p.id = activeFilters.id
    if (activeFilters.externalId) p.externalId = activeFilters.externalId
    if (activeFilters.subscriptionId) p.subscriptionId = activeFilters.subscriptionId
    if (activeFilters.customerId) p.customerId = activeFilters.customerId
    if (activeFilters.moduleId) p.moduleId = activeFilters.moduleId
    if (activeFilters.gateway) p.gateway = activeFilters.gateway
    if (activeFilters.paymentMethod) p.paymentMethod = activeFilters.paymentMethod
    if (activeFilters.type) p.type = activeFilters.type
    if (activeFilters.billingCycle) p.billingCycle = activeFilters.billingCycle
    if (activeFilters.status) p.status = activeFilters.status
    if (activeFilters.amountMin) p.amountMin = activeFilters.amountMin
    if (activeFilters.amountMax) p.amountMax = activeFilters.amountMax
    if (activeFilters.dateFrom) p.dateFrom = activeFilters.dateFrom
    if (activeFilters.dateTo) p.dateTo = activeFilters.dateTo
    if (activeFilters.hasWebhookError) p.hasWebhookError = activeFilters.hasWebhookError
    if (activeFilters.lastWebhookStatus) p.lastWebhookStatus = activeFilters.lastWebhookStatus
    return p
  }

  const { data: summary } = useQuery({
    queryKey: ['admin-payments-summary', activeFilters],
    queryFn: async () => {
      const { data } = await adminApi.get<AdminPaymentSummary>('/api/v1/admin/payments/summary', { params: buildParams() })
      return data
    },
    staleTime: 30_000,
  })

  const { data: result, isLoading, isError } = useQuery({
    queryKey: ['admin-payments', activeFilters, page],
    queryFn: async () => {
      const { data } = await adminApi.get<AdminPaymentPage>('/api/v1/admin/payments', { params: buildParams() })
      return data
    },
    staleTime: 30_000,
    placeholderData: (prev) => prev,
  })

  const items = result?.items ?? []
  const total = result?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  const patchFilters = (patch: Partial<Filters>) => {
    setFilters((prev) => ({ ...prev, ...patch }))
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Pagamentos</h1>
        <p className="mt-1 text-sm text-gray-400">
          Histórico de pagamentos processados pelos gateways, com o histórico completo de eventos de webhook de cada um.
        </p>
      </div>

      <SummaryCards summary={summary} />

      <FiltersPanel filters={filters} onChange={patchFilters} onReset={() => setFilters(EMPTY_FILTERS)} />

      {isError ? (
        <p className="text-red-400 text-sm py-4">Erro ao carregar pagamentos. Tente novamente.</p>
      ) : isLoading ? (
        <p className="text-gray-400 text-sm py-4">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700 flex items-center justify-between">
            <span className="text-sm text-gray-400">
              {total} pagamento{total !== 1 ? 's' : ''}
              {totalPages > 1 && ` — página ${page + 1} de ${totalPages}`}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-700 text-sm">
              <thead>
                <tr>
                  {['Data', 'ID', 'Cliente', 'Módulo', 'Tipo', 'Gateway', 'Método', 'Valor', 'Status', 'Último evento', 'Erro', ''].map((h) => (
                    <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700">
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={12} className="px-4 py-12 text-center text-gray-500">
                      Nenhum pagamento encontrado com os filtros aplicados.
                    </td>
                  </tr>
                ) : (
                  items.map((p) => <PaymentRow key={p.id} payment={p} onView={setDetailId} />)
                )}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="px-4 py-3 border-t border-gray-700 flex items-center justify-between">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="rounded-lg px-3 py-1.5 text-xs font-medium bg-gray-700 text-gray-300 hover:bg-gray-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                ← Anterior
              </button>
              <span className="text-xs text-gray-400">{page + 1} / {totalPages}</span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-lg px-3 py-1.5 text-xs font-medium bg-gray-700 text-gray-300 hover:bg-gray-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Próxima →
              </button>
            </div>
          )}
        </div>
      )}

      {detailId && <AdminPaymentDetailDrawer paymentId={detailId} onClose={() => setDetailId(null)} />}
    </div>
  )
}

// ─── Payment Row ────────────────────────────────────────────────────────────

function PaymentRow({ payment, onView }: { payment: AdminPaymentListItem; onView: (id: string) => void }) {
  return (
    <tr className="hover:bg-gray-700/50 cursor-pointer transition-colors" onClick={() => onView(payment.id)}>
      <td className="px-3 py-3 whitespace-nowrap text-gray-400">{fmtDateTime(payment.createdAt)}</td>

      <td className="px-3 py-3 whitespace-nowrap">
        <p className="text-gray-200 font-mono text-xs">{payment.externalId ?? payment.id.slice(0, 8)}</p>
      </td>

      <td className="px-3 py-3">
        <p className="text-gray-300 truncate max-w-[140px]">{payment.customerName ?? '—'}</p>
        <p className="text-xs text-gray-500 truncate max-w-[140px]">{payment.customerEmail ?? ''}</p>
      </td>

      <td className="px-3 py-3 whitespace-nowrap text-gray-300">{payment.moduleName ?? '—'}</td>

      <td className="px-3 py-3 whitespace-nowrap text-gray-400">
        {payment.type === 'RECURRING'
          ? (payment.billingCycle === 'ANNUAL' ? 'Recorrente / Anual' : payment.billingCycle === 'MONTHLY' ? 'Recorrente / Mensal' : 'Recorrente')
          : 'Único'}
      </td>

      <td className="px-3 py-3 whitespace-nowrap"><GatewayBadge gateway={payment.gateway} /></td>

      <td className="px-3 py-3 whitespace-nowrap text-gray-300">{payment.paymentMethod ? (METHOD_LABELS[payment.paymentMethod] ?? payment.paymentMethod) : '—'}</td>

      <td className="px-3 py-3 whitespace-nowrap text-gray-200">{brl(payment.amount, payment.currency)}</td>

      <td className="px-3 py-3 whitespace-nowrap"><StatusBadge status={payment.status} /></td>

      <td className="px-3 py-3 whitespace-nowrap">
        <p className="text-gray-300 truncate max-w-[160px]">{payment.lastEventType ?? '—'}</p>
        <p className="text-xs text-gray-500">{fmtDateTime(payment.lastEventAt)}</p>
      </td>

      <td className="px-3 py-3 whitespace-nowrap text-center">
        {payment.hasWebhookError ? <span className="text-red-400" title="Há evento de webhook com erro">✕</span> : <span className="text-green-500" title="Sem erros">✓</span>}
      </td>

      <td className="px-3 py-3 whitespace-nowrap text-right">
        <button
          onClick={(e) => { e.stopPropagation(); onView(payment.id) }}
          className="text-xs text-blue-400 hover:text-blue-300 font-medium transition-colors"
        >
          Ver detalhes
        </button>
      </td>
    </tr>
  )
}
