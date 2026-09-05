import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/shared/services/adminApi'

// ─── Types ────────────────────────────────────────────────────────────────────

interface PaymentSubscription {
  id: string
  moduleId: string
  moduleName: string
  planId: string
  planName: string
  billingCycle: 'MONTHLY' | 'ANNUAL' | null
  status: string
  startedAt: string | null
  expiresAt: string | null
}

interface PaymentDetail {
  id: string
  externalId: string | null
  customerId: string
  customerName: string | null
  customerEmail: string | null
  type: 'ONE_TIME' | 'RECURRING'
  amount: number
  currency: string
  feeAmount: number | null
  netAmount: number | null
  status: string
  gateway: string
  paymentMethod: string | null
  gatewayCustomerId: string | null
  gatewayPaymentId: string | null
  gatewaySubscriptionId: string | null
  metadata: string | null
  checkoutUrl: string | null
  createdAt: string
  updatedAt: string
  subscription: PaymentSubscription | null
}

interface PaymentEvent {
  id: string
  gateway: string
  externalEventId: string
  eventType: string
  status: 'RECEIVED' | 'PROCESSED' | 'FAILED' | 'IGNORED'
  errorMessage: string | null
  attempts: number
  createdAt: string
  processedAt: string | null
}

interface PaymentEventDetail extends PaymentEvent {
  paymentId: string
  payload: string
}

interface TimelineEntry {
  kind: 'PAYMENT_CREATED' | 'WEBHOOK_EVENT'
  eventType: string | null
  status: string | null
  occurredAt: string
  processedAt: string | null
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function fmtDateTime(d: string | null | undefined) {
  if (!d) return '—'
  return new Date(d).toLocaleString('pt-BR')
}

function brl(v: number | null, currency?: string) {
  if (v == null) return '—'
  return `${currency ?? 'R$'} ${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-gray-500 shrink-0">{label}</span>
      <span className="text-gray-200 text-right">{value}</span>
    </div>
  )
}

// ─── Badges ───────────────────────────────────────────────────────────────────

const PAYMENT_STATUS_COLORS: Record<string, string> = {
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

function PaymentStatusBadge({ status }: { status: string }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${PAYMENT_STATUS_COLORS[status] ?? 'bg-gray-700 text-gray-400'}`}>
      {status}
    </span>
  )
}

const EVENT_STATUS_COLORS: Record<string, string> = {
  RECEIVED:  'bg-yellow-900 text-yellow-200',
  PROCESSED: 'bg-green-900 text-green-200',
  FAILED:    'bg-red-900 text-red-300',
  IGNORED:   'bg-gray-700 text-gray-400',
}

const EVENT_STATUS_LABELS: Record<string, string> = {
  RECEIVED:  'Pendente',
  PROCESSED: 'Processado',
  FAILED:    'Falhou',
  IGNORED:   'Ignorado',
}

function EventStatusBadge({ status }: { status: string }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${EVENT_STATUS_COLORS[status] ?? 'bg-gray-700 text-gray-400'}`}>
      {EVENT_STATUS_LABELS[status] ?? status}
    </span>
  )
}

function prettyJson(raw: string | null): string {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

// ─── Payload modal ────────────────────────────────────────────────────────────

function EventPayloadModal({ paymentId, eventId, onClose }: { paymentId: string; eventId: string; onClose: () => void }) {
  const { data: event, isLoading } = useQuery({
    queryKey: ['admin-payment-event-detail', paymentId, eventId],
    queryFn: async () => {
      const { data } = await adminApi.get<PaymentEventDetail>(`/api/v1/admin/payments/${paymentId}/events/${eventId}`)
      return data
    },
  })

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/70 p-4">
      <div className="w-full max-w-xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[85vh] overflow-y-auto">
        <div className="sticky top-0 flex items-center justify-between px-5 py-3 bg-gray-900 border-b border-gray-700">
          <h3 className="text-sm font-semibold text-white">{event?.eventType ?? 'Evento de webhook'}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>
        <div className="p-5 space-y-4">
          {isLoading || !event ? (
            <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
          ) : (
            <>
              <div className="space-y-1.5 text-sm">
                <Row label="Status" value={<EventStatusBadge status={event.status} />} />
                <Row label="Tentativas" value={String(event.attempts)} />
                <Row label="Recebido em" value={fmtDateTime(event.createdAt)} />
                <Row label="Processado em" value={fmtDateTime(event.processedAt)} />
                {event.errorMessage && <Row label="Erro" value={<span className="text-red-300">{event.errorMessage}</span>} />}
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Payload</p>
                <pre className="rounded-xl bg-gray-800 border border-gray-700 p-3 text-xs text-gray-300 overflow-x-auto whitespace-pre-wrap break-all">
                  {prettyJson(event.payload)}
                </pre>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Aba Geral ────────────────────────────────────────────────────────────────

function GeralTab({ detail }: { detail: PaymentDetail }) {
  return (
    <div className="space-y-5">
      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Pagamento</p>
        <div className="space-y-1.5 text-sm">
          <Row label="ID interno" value={detail.id} />
          <Row label="ID externo" value={detail.externalId ?? '—'} />
          <Row label="Cliente" value={detail.customerName ?? '—'} />
          <Row label="E-mail" value={detail.customerEmail ?? '—'} />
          <Row label="Tipo" value={detail.type === 'RECURRING' ? 'Recorrente' : 'Pagamento único'} />
          <div className="flex justify-between">
            <span className="text-gray-500">Status</span>
            <PaymentStatusBadge status={detail.status} />
          </div>
          <Row label="Valor bruto" value={brl(detail.amount, detail.currency)} />
          <Row label="Taxa" value={brl(detail.feeAmount, detail.currency)} />
          <Row label="Valor líquido" value={brl(detail.netAmount, detail.currency)} />
          <Row label="Criado em" value={fmtDateTime(detail.createdAt)} />
          <Row label="Atualizado em" value={fmtDateTime(detail.updatedAt)} />
        </div>
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Assinatura</p>
        {detail.subscription ? (
          <div className="space-y-1.5 text-sm">
            <Row label="Módulo" value={detail.subscription.moduleName} />
            <Row label="Plano" value={detail.subscription.planName} />
            <Row label="Ciclo" value={detail.subscription.billingCycle === 'ANNUAL' ? 'Anual' : detail.subscription.billingCycle === 'MONTHLY' ? 'Mensal' : '—'} />
            <Row label="Status da assinatura" value={detail.subscription.status} />
            <Row label="Início" value={fmtDateTime(detail.subscription.startedAt)} />
            <Row label="Vencimento" value={fmtDateTime(detail.subscription.expiresAt)} />
          </div>
        ) : (
          <p className="text-sm text-gray-500">Sem assinatura associada.</p>
        )}
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Gateway</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Gateway" value={detail.gateway} />
          <Row label="Método de pagamento" value={detail.paymentMethod ?? '—'} />
          <Row label="Customer ID" value={detail.gatewayCustomerId ?? '—'} />
          <Row label="Payment ID" value={detail.gatewayPaymentId ?? '—'} />
          <Row label="Subscription ID" value={detail.gatewaySubscriptionId ?? '—'} />
          {detail.checkoutUrl && <Row label="Checkout URL" value={detail.checkoutUrl} />}
        </div>
        {detail.metadata && detail.metadata !== '{}' && (
          <div className="mt-3">
            <p className="text-xs text-gray-500 mb-1.5">Metadata (bruto, somente leitura)</p>
            <pre className="rounded-xl bg-gray-800 border border-gray-700 p-3 text-xs text-gray-300 overflow-x-auto whitespace-pre-wrap break-all">
              {prettyJson(detail.metadata)}
            </pre>
          </div>
        )}
      </section>
    </div>
  )
}

// ─── Aba Histórico de Eventos ─────────────────────────────────────────────────

type EventShortcut = 'todos' | 'processados' | 'erro' | 'pendentes'

function EventsTab({ paymentId }: { paymentId: string }) {
  const [shortcut, setShortcut] = useState<EventShortcut>('todos')
  const [payloadEventId, setPayloadEventId] = useState<string | null>(null)

  const statusParam = shortcut === 'processados' ? 'PROCESSED'
    : shortcut === 'erro' ? 'FAILED'
    : shortcut === 'pendentes' ? 'RECEIVED'
    : undefined

  const { data: events = [], isLoading } = useQuery({
    queryKey: ['admin-payment-events', paymentId, statusParam],
    queryFn: async () => {
      const { data } = await adminApi.get<PaymentEvent[]>(`/api/v1/admin/payments/${paymentId}/events`, {
        params: statusParam ? { status: statusParam } : undefined,
      })
      return data
    },
  })

  const shortcuts: { key: EventShortcut; label: string }[] = [
    { key: 'todos', label: 'Todos' },
    { key: 'processados', label: 'Processados' },
    { key: 'erro', label: 'Com erro' },
    { key: 'pendentes', label: 'Pendentes' },
  ]

  return (
    <div className="space-y-3">
      <div className="flex gap-1.5">
        {shortcuts.map((s) => (
          <button
            key={s.key}
            onClick={() => setShortcut(s.key)}
            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${
              shortcut === s.key ? 'bg-indigo-700 text-white' : 'bg-gray-800 text-gray-400 hover:text-gray-200'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
      ) : events.length === 0 ? (
        <p className="text-sm text-gray-500 text-center py-4">Nenhum evento encontrado.</p>
      ) : (
        <ul className="space-y-2">
          {events.map((e) => (
            <li
              key={e.id}
              onClick={() => setPayloadEventId(e.id)}
              className="cursor-pointer rounded-lg bg-gray-800/50 border border-gray-700 hover:border-gray-600 px-3 py-2 text-xs space-y-1 transition-colors"
            >
              <div className="flex items-center justify-between gap-3">
                <span className="text-white font-medium">{e.eventType}</span>
                <EventStatusBadge status={e.status} />
              </div>
              <div className="flex items-center justify-between gap-3 text-gray-500">
                <span>{e.attempts > 1 ? `${e.attempts} tentativas` : '1 tentativa'}</span>
                <span>{fmtDateTime(e.createdAt)}</span>
              </div>
              {e.status === 'FAILED' && e.errorMessage && (
                <p className="text-red-300">{e.errorMessage}</p>
              )}
            </li>
          ))}
        </ul>
      )}

      {payloadEventId && (
        <EventPayloadModal paymentId={paymentId} eventId={payloadEventId} onClose={() => setPayloadEventId(null)} />
      )}
    </div>
  )
}

// ─── Aba Timeline ─────────────────────────────────────────────────────────────

function TimelineTab({ paymentId }: { paymentId: string }) {
  const { data: timeline = [], isLoading } = useQuery({
    queryKey: ['admin-payment-timeline', paymentId],
    queryFn: async () => {
      const { data } = await adminApi.get<TimelineEntry[]>(`/api/v1/admin/payments/${paymentId}/timeline`)
      return data
    },
  })

  if (isLoading) return <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
  if (timeline.length === 0) return <p className="text-sm text-gray-500 text-center py-4">Sem histórico.</p>

  return (
    <ol className="relative border-l border-gray-700 ml-2 space-y-5">
      {timeline.map((t, i) => (
        <li key={i} className="ml-4">
          <div className="absolute -ml-[21px] mt-1 h-2.5 w-2.5 rounded-full bg-indigo-500" />
          <p className="text-sm font-medium text-white">
            {t.kind === 'PAYMENT_CREATED' ? 'Pagamento criado' : t.eventType}
          </p>
          <div className="flex items-center gap-2 mt-0.5">
            {t.status && <EventStatusBadge status={t.status} />}
            <span className="text-xs text-gray-500">{fmtDateTime(t.occurredAt)}</span>
          </div>
        </li>
      ))}
    </ol>
  )
}

// ─── Drawer principal ─────────────────────────────────────────────────────────

export function AdminPaymentDetailDrawer({ paymentId, onClose }: { paymentId: string; onClose: () => void }) {
  const [tab, setTab] = useState<'geral' | 'eventos' | 'timeline'>('geral')

  const { data: detail, isLoading } = useQuery({
    queryKey: ['admin-payment-detail', paymentId],
    queryFn: async () => {
      const { data } = await adminApi.get<PaymentDetail>(`/api/v1/admin/payments/${paymentId}`)
      return data
    },
  })

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <div>
            <h2 className="text-lg font-semibold text-white">Pagamento {detail?.externalId ?? paymentId}</h2>
            {detail && <p className="text-xs text-gray-400 mt-0.5">{detail.gateway} · {brl(detail.amount, detail.currency)}</p>}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <div className="px-6 pt-4">
          <div className="flex gap-1 border-b border-gray-700">
            {(['geral', 'eventos', 'timeline'] as const).map((t) => (
              <button key={t} type="button" onClick={() => setTab(t)}
                className={`px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                  tab === t ? 'border-indigo-500 text-indigo-300' : 'border-transparent text-gray-400 hover:text-gray-200'
                }`}>
                {t === 'geral' ? 'Geral' : t === 'eventos' ? 'Histórico de Eventos' : 'Timeline'}
              </button>
            ))}
          </div>
        </div>

        <div className="p-6">
          {isLoading || !detail ? (
            <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
          ) : tab === 'geral' ? (
            <GeralTab detail={detail} />
          ) : tab === 'eventos' ? (
            <EventsTab paymentId={paymentId} />
          ) : (
            <TimelineTab paymentId={paymentId} />
          )}
        </div>
      </div>
    </div>
  )
}
