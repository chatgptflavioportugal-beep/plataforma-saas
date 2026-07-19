import { useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
import type {
  Plan, PlanVersionModule, PlatformModule,
  TrialCampaign, TrialCampaignParticipant, TrialCampaignStatus,
  TrialCampaignHistoryEntry, TrialCampaignListResult,
} from '@/shared/types'
import { StatusBadge, fmtDate, CampaignForm, EMPTY_FORM, campaignToForm, toCampaignPayload, type FormState } from './TrialCampaignsModal'

const PAGE_SIZE = 20

function fmtDateTime(d: string | null | undefined) {
  if (!d) return '—'
  return new Date(d).toLocaleString('pt-BR')
}

function brl(v: number | null | undefined) {
  if (v == null) return '—'
  return `R$ ${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-gray-500 shrink-0">{label}</span>
      <span className="text-gray-200 text-right">{value}</span>
    </div>
  )
}

// ─── barra de utilização de vagas ───────────────────────────────────────────

function UsageBar({ used, max }: { used: number; max: number }) {
  const pct = max > 0 ? Math.min(100, Math.round((used / max) * 100)) : 0
  return (
    <div className="flex items-center gap-2 min-w-[110px]">
      <span className="text-xs text-gray-300 whitespace-nowrap tabular-nums">{used} / {max}</span>
      <div className="flex-1 h-1.5 rounded-full bg-gray-700 overflow-hidden">
        <div className={`h-full ${pct >= 100 ? 'bg-amber-500' : 'bg-indigo-500'}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-500 tabular-nums w-8 text-right">{pct}%</span>
    </div>
  )
}

// ─── seletor de plano/módulo para criar campanha ──────────────────────────────

function PlanModulePicker({
  planId, moduleId, onChangePlan, onChangeModule,
}: {
  planId: string
  moduleId: string
  onChangePlan: (id: string) => void
  onChangeModule: (id: string) => void
}) {
  const { data: plans = [] } = useQuery({
    queryKey: ['admin-plans-current'],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>('/api/v1/admin/plans')
      return data.filter((p) => p.is_current_version)
    },
  })

  const { data: modules = [] } = useQuery({
    queryKey: ['plan-modules', planId],
    queryFn: async () => {
      const { data } = await api.get<PlanVersionModule[]>(`/api/v1/admin/plans/${planId}/modules`)
      return data
    },
    enabled: !!planId,
  })

  return (
    <div className="grid grid-cols-2 gap-3">
      <div>
        <label className="block text-xs text-gray-400 mb-1">Plano *</label>
        <select value={planId} onChange={(e) => { onChangePlan(e.target.value); onChangeModule('') }}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500">
          <option value="">Selecione…</option>
          {plans.map((p) => <option key={p.id} value={p.id}>{p.name} (v{p.version})</option>)}
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-400 mb-1">Módulo *</label>
        <select value={moduleId} onChange={(e) => onChangeModule(e.target.value)} disabled={!planId}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 disabled:opacity-50">
          <option value="">Selecione…</option>
          {modules.map((m) => <option key={m.id} value={m.id}>{m.module_name}</option>)}
        </select>
      </div>
    </div>
  )
}

// ─── filtros ────────────────────────────────────────────────────────────────

interface Filters {
  status: '' | TrialCampaignStatus
  moduleId: string
  planId: string
  createdBy: string
  startDateFrom: string
  startDateTo: string
  hasSlots: boolean
}

const EMPTY_FILTERS: Filters = {
  status: '', moduleId: '', planId: '', createdBy: '', startDateFrom: '', startDateTo: '', hasSlots: false,
}

function Select({ value, onChange, options }: { value: string; onChange: (v: string) => void; options: { value: string; label: string }[] }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)}
      className="rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-3 py-2 focus:outline-none focus:border-blue-500">
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}

function FiltersPanel({
  searchInput, onSearchChange, filters, onChange, onReset, moduleOptions, planOptions, creatorOptions,
}: {
  searchInput: string
  onSearchChange: (v: string) => void
  filters: Filters
  onChange: (patch: Partial<Filters>) => void
  onReset: () => void
  moduleOptions: { value: string; label: string }[]
  planOptions: { value: string; label: string }[]
  creatorOptions: { value: string; label: string }[]
}) {
  const emptyFiltersRecord = EMPTY_FILTERS as unknown as Record<string, unknown>
  const hasActive = searchInput !== '' || Object.entries(filters).some(([k, v]) => v !== emptyFiltersRecord[k])

  return (
    <div className="rounded-xl bg-gray-800 border border-gray-700 px-4 py-4 space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">Filtros</p>
        {hasActive && (
          <button onClick={onReset} className="text-xs text-blue-400 hover:text-blue-300 transition-colors">Limpar filtros</button>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <input
          type="text"
          placeholder="Buscar campanha..."
          value={searchInput}
          onChange={(e) => onSearchChange(e.target.value)}
          className="col-span-1 sm:col-span-2 rounded-lg bg-gray-700 border border-gray-600 text-gray-100 placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
        />
        <Select value={filters.status} onChange={(v) => onChange({ status: v as Filters['status'] })}
          options={[
            { value: '', label: 'Todos os status' },
            { value: 'ACTIVE', label: 'Ativos' },
            { value: 'SCHEDULED', label: 'Programados' },
            { value: 'CLOSED', label: 'Encerrados' },
            { value: 'CANCELLED', label: 'Cancelados' },
          ]}
        />
        <Select value={filters.moduleId} onChange={(v) => onChange({ moduleId: v })}
          options={[{ value: '', label: 'Módulo' }, ...moduleOptions]}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <Select value={filters.planId} onChange={(v) => onChange({ planId: v })}
          options={[{ value: '', label: 'Plano' }, ...planOptions]}
        />
        <Select value={filters.createdBy} onChange={(v) => onChange({ createdBy: v })}
          options={[{ value: '', label: 'Criado por' }, ...creatorOptions]}
        />
        <div className="flex gap-2">
          <input type="date" value={filters.startDateFrom} onChange={(e) => onChange({ startDateFrom: e.target.value })}
            title="Vigência a partir de"
            className="flex-1 rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-2 py-2 focus:outline-none focus:border-blue-500" />
          <input type="date" value={filters.startDateTo} onChange={(e) => onChange({ startDateTo: e.target.value })}
            title="Vigência até"
            className="flex-1 rounded-lg bg-gray-700 border border-gray-600 text-gray-100 text-sm px-2 py-2 focus:outline-none focus:border-blue-500" />
        </div>
        <div className="flex items-center gap-4 text-xs text-gray-400 px-1">
          <label className="flex items-center gap-1.5 cursor-pointer">
            <input type="checkbox" checked={filters.hasSlots} onChange={(e) => onChange({ hasSlots: e.target.checked })}
              className="rounded border-gray-600 bg-gray-700 text-blue-500 focus:ring-0" />
            Somente com vagas
          </label>
          <label className="flex items-center gap-1.5 opacity-50 cursor-not-allowed" title="Em breve">
            <input type="checkbox" disabled className="rounded border-gray-600 bg-gray-700" />
            Trial reutilizável
          </label>
        </div>
      </div>
    </div>
  )
}

// ─── cabeçalho ordenável ──────────────────────────────────────────────────────

function SortableHeader({
  label, sortKey, currentSort, currentDir, onSort,
}: {
  label: string
  sortKey?: string
  currentSort: string | null
  currentDir: 'asc' | 'desc'
  onSort: (key: string) => void
}) {
  if (!sortKey) {
    return <th className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">{label}</th>
  }
  const active = currentSort === sortKey
  return (
    <th
      onClick={() => onSort(sortKey)}
      className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap cursor-pointer select-none hover:text-gray-200 transition-colors"
    >
      {label} {active ? (currentDir === 'asc' ? '▲' : '▼') : ''}
    </th>
  )
}

// ─── ações da linha ──────────────────────────────────────────────────────────

function RowActions({
  campaign, onView, onEdit, onCancel,
}: {
  campaign: TrialCampaign
  onView: (id: string) => void
  onEdit: (c: TrialCampaign) => void
  onCancel: (c: TrialCampaign) => void
}) {
  const { hasAdminPermission } = useAuth()
  const isCancelled = campaign.status === 'CANCELLED'
  const canEdit = !isCancelled && hasAdminPermission('admin.trials.edit')
  const canCancel = !isCancelled && hasAdminPermission('admin.trials.cancel') && (campaign.status === 'ACTIVE' || campaign.status === 'SCHEDULED')

  return (
    <div className="flex items-center gap-1.5" onClick={(e) => e.stopPropagation()}>
      <button type="button" onClick={() => onView(campaign.id)}
        className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
        Visualizar
      </button>
      {canEdit && (
        <button type="button" onClick={() => onEdit(campaign)}
          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
          Editar
        </button>
      )}
      {canCancel && (
        <button type="button" onClick={() => onCancel(campaign)}
          className="px-2.5 py-1 rounded-md bg-red-900/60 text-red-300 text-xs hover:bg-red-800/60 transition-colors">
          Cancelar
        </button>
      )}
    </div>
  )
}

// ─── modal de edição ─────────────────────────────────────────────────────────

function EditCampaignModal({ campaign, onClose, onSaved }: { campaign: TrialCampaign; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<FormState>(campaignToForm(campaign))
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.put(`/api/v1/admin/trial-campaigns/${campaign.id}`, payload),
    onSuccess: () => { onSaved(); onClose() },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar campanha de Trial')
    },
  })

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-white">Editar campanha</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>
        {error && (
          <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
        )}
        <CampaignForm form={form} onChange={setForm} />
        {campaign.usedSlots > 0 && campaign.status !== 'CANCELLED' && (
          <p className="text-xs text-gray-500">Dias e vagas só são alterados enquanto a campanha ainda não tiver participantes — depois disso, crie uma nova campanha.</p>
        )}
        <div className="flex gap-2">
          <button type="button" onClick={() => mutation.mutate(toCampaignPayload(form))} disabled={mutation.isPending}
            className="px-4 py-1.5 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors">
            {mutation.isPending ? 'Salvando…' : 'Salvar'}
          </button>
          <button type="button" onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
            Cancelar
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── modal de confirmação de cancelamento ────────────────────────────────────

function CancelConfirmModal({
  campaign, onClose, onConfirm, isPending,
}: {
  campaign: TrialCampaign
  onClose: () => void
  onConfirm: () => void
  isPending: boolean
}) {
  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-md bg-gray-900 border border-red-900/60 rounded-2xl shadow-2xl p-6 space-y-4">
        <h2 className="text-lg font-semibold text-white">Cancelar Trial</h2>
        <p className="text-sm text-gray-300">
          Você tem certeza que deseja cancelar este Trial (<span className="text-white font-medium">{campaign.name}</span>)?
        </p>
        <div className="rounded-lg bg-gray-800/60 border border-gray-700 p-3 space-y-2 text-xs text-gray-400">
          <p>Após o cancelamento:</p>
          <ul className="list-disc list-inside space-y-1">
            <li>Novos usuários não poderão mais iniciar este Trial.</li>
            <li>Os participantes atuais continuarão utilizando normalmente até o término do período contratado.</li>
          </ul>
          <p>Esta ação poderá ser revertida apenas criando uma nova campanha de Trial.</p>
        </div>
        <div className="flex gap-2 justify-end pt-1">
          <button type="button" onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
            Voltar
          </button>
          <button type="button" onClick={onConfirm} disabled={isPending}
            className="px-4 py-1.5 rounded-lg bg-red-700 text-white text-xs hover:bg-red-600 disabled:opacity-50 transition-colors">
            {isPending ? 'Cancelando…' : 'Cancelar Trial'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── aba Participantes ─────────────────────────────────────────────────────────

function ParticipantsTab({ campaignId }: { campaignId: string }) {
  const { data: participants = [], isLoading } = useQuery({
    queryKey: ['trial-campaign-participants', campaignId],
    queryFn: async () => {
      const { data } = await api.get<TrialCampaignParticipant[]>(`/api/v1/admin/trial-campaigns/${campaignId}/participants`)
      return data
    },
  })

  if (isLoading) return <p className="text-sm text-gray-400 py-4 text-center">Carregando…</p>
  if (participants.length === 0) return <p className="text-sm text-gray-400 py-4 text-center">Nenhum participante ainda.</p>

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-xs">
        <thead>
          <tr className="text-gray-500">
            <th className="text-left py-1.5 pr-3">Empresa</th>
            <th className="text-left py-1.5 pr-3">Usuário</th>
            <th className="text-left py-1.5 pr-3">Início</th>
            <th className="text-left py-1.5 pr-3">Fim</th>
            <th className="text-left py-1.5 pr-3">Status</th>
            <th className="text-left py-1.5">Virou cliente?</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-700/50">
          {participants.map((p, i) => (
            <tr key={i}>
              <td className="py-1.5 pr-3 text-white">{p.tenantName} <span className="text-gray-500">({p.tenantType === 'INDIVIDUAL' ? 'Individual' : 'Empresa'})</span></td>
              <td className="py-1.5 pr-3 text-gray-300">{p.userName ?? p.userEmail ?? '—'}</td>
              <td className="py-1.5 pr-3 text-gray-400">{fmtDateTime(p.startedAt)}</td>
              <td className="py-1.5 pr-3 text-gray-400">{fmtDateTime(p.finishedAt)}</td>
              <td className="py-1.5 pr-3 text-gray-400">{p.status ?? '—'}</td>
              <td className="py-1.5">
                {p.becameCustomer
                  ? <span className="text-green-400 font-medium">Sim</span>
                  : <span className="text-gray-500">Não</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ─── aba Histórico ──────────────────────────────────────────────────────────

const HISTORY_ACTION_LABELS: Record<string, string> = {
  'trial_campaign.created':   'Criado',
  'trial_campaign.updated':   'Alterado',
  'trial_campaign.closed':    'Encerrado',
  'trial_campaign.cancelled': 'Cancelado',
}

function HistoryTab({ campaignId }: { campaignId: string }) {
  const { data: history = [], isLoading } = useQuery({
    queryKey: ['trial-campaign-history', campaignId],
    queryFn: async () => {
      const { data } = await api.get<TrialCampaignHistoryEntry[]>(`/api/v1/admin/trial-campaigns/${campaignId}/history`)
      return data
    },
  })

  if (isLoading) return <p className="text-sm text-gray-400 py-4 text-center">Carregando…</p>
  if (history.length === 0) return <p className="text-sm text-gray-400 py-4 text-center">Nenhum evento registrado ainda.</p>

  return (
    <ul className="space-y-2">
      {history.map((h, i) => (
        <li key={i} className="flex items-center justify-between gap-3 rounded-lg bg-gray-800/50 border border-gray-700 px-3 py-2 text-xs">
          <span className="text-white font-medium">{HISTORY_ACTION_LABELS[h.action] ?? h.action}</span>
          <span className="text-gray-400">{h.actorName ?? 'Sistema'}</span>
          <span className="text-gray-500 whitespace-nowrap">{fmtDateTime(h.createdAt)}</span>
        </li>
      ))}
    </ul>
  )
}

// ─── aba Geral ──────────────────────────────────────────────────────────────

function GeralTab({ detail }: { detail: TrialCampaign }) {
  const esgotado = detail.usedSlots >= detail.maxSlots
  return (
    <div className="space-y-5">
      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Informações Gerais</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Nome da campanha" value={detail.name} />
          <div className="flex justify-between">
            <span className="text-gray-500">Status</span>
            <StatusBadge status={detail.status} esgotado={esgotado} expired={detail.expired} />
          </div>
          <Row label="Descrição" value={detail.notes ?? '—'} />
          <Row label="Prioridade" value={String(detail.priority)} />
        </div>
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Módulo</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Nome" value={detail.moduleName} />
          <Row label="Slug" value={detail.moduleSlug ?? '—'} />
          <Row label="Ícone" value={detail.moduleIcon ?? '—'} />
        </div>
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Plano</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Nome do plano" value={detail.planName ?? '—'} />
          <Row label="Versão" value={detail.planVersion != null ? `v${detail.planVersion}` : '—'} />
          <Row label="Preço mensal" value={brl(detail.planMonthlyPrice)} />
          <Row label="Preço anual" value={brl(detail.planAnnualPrice)} />
        </div>
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Configuração</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Dias de Trial" value={String(detail.days)} />
          <Row label="Máximo de participantes" value={String(detail.maxSlots)} />
          <Row label="Participantes utilizados" value={String(detail.usedSlots)} />
          <Row label="Participantes restantes" value={String(Math.max(0, detail.maxSlots - detail.usedSlots))} />
          <Row label="Data inicial" value={fmtDate(detail.startDate)} />
          <Row label="Data final" value={fmtDate(detail.endDate)} />
        </div>
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Estatísticas</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Participantes" value={String(detail.totalParticipants ?? 0)} />
          <Row label="Trials ativos" value={String(detail.participantsActive ?? 0)} />
          <Row label="Trials expirados" value={String(detail.participantsExpired ?? 0)} />
          <Row label="Trials cancelados" value={String(detail.participantsCancelled ?? 0)} />
          <Row label="Conversão" value={`${detail.conversionPercent ?? 0}%`} />
        </div>
      </section>

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Auditoria</p>
        <div className="space-y-1.5 text-sm">
          <Row label="Criado por" value={detail.createdByName ?? '—'} />
          <Row label="Data de criação" value={fmtDateTime(detail.createdAt)} />
          <Row label="Última alteração" value={detail.updatedAt ? fmtDateTime(detail.updatedAt) : '—'} />
          <Row label="Último responsável" value={detail.updatedByName ?? '—'} />
        </div>
      </section>
    </div>
  )
}

// ─── drawer de detalhe (3 abas) ──────────────────────────────────────────────

function CampaignDetailDrawer({ campaignId, onClose }: { campaignId: string; onClose: () => void }) {
  const [tab, setTab] = useState<'geral' | 'participantes' | 'historico'>('geral')
  const { data: detail, isLoading } = useQuery({
    queryKey: ['trial-campaign-detail', campaignId],
    queryFn: async () => {
      const { data } = await api.get<TrialCampaign>(`/api/v1/admin/trial-campaigns/${campaignId}`)
      return data
    },
  })

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <div>
            <h2 className="text-lg font-semibold text-white">{detail?.name ?? 'Campanha'}</h2>
            {detail && <p className="text-xs text-gray-400 mt-0.5">{detail.moduleName}{detail.planName ? ` · ${detail.planName}` : ''}</p>}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <div className="px-6 pt-4">
          <div className="flex gap-1 border-b border-gray-700">
            {(['geral', 'participantes', 'historico'] as const).map((t) => (
              <button key={t} type="button" onClick={() => setTab(t)}
                className={`px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                  tab === t ? 'border-indigo-500 text-indigo-300' : 'border-transparent text-gray-400 hover:text-gray-200'
                }`}>
                {t === 'geral' ? 'Geral' : t === 'participantes' ? 'Participantes' : 'Histórico'}
              </button>
            ))}
          </div>
        </div>

        <div className="p-6">
          {isLoading || !detail ? (
            <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
          ) : tab === 'geral' ? (
            <GeralTab detail={detail} />
          ) : tab === 'participantes' ? (
            <ParticipantsTab campaignId={campaignId} />
          ) : (
            <HistoryTab campaignId={campaignId} />
          )}
        </div>
      </div>
    </div>
  )
}

// ─── página principal ───────────────────────────────────────────────────────────

export function AdminTrialsPage() {
  const qc = useQueryClient()
  const { hasAdminPermission } = useAuth()
  const canCreate = hasAdminPermission('admin.trials.create')

  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [searchInput, setSearchInput] = useState('')
  const [sortBy, setSortBy] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [page, setPage] = useState(1)

  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const [detailId, setDetailId] = useState<string | null>(null)
  const [editCampaign, setEditCampaign] = useState<TrialCampaign | null>(null)
  const [cancelCampaign, setCancelCampaign] = useState<TrialCampaign | null>(null)

  const [showCreate, setShowCreate] = useState(false)
  const [createPlanId, setCreatePlanId] = useState('')
  const [createModuleId, setCreateModuleId] = useState('')
  const [createForm, setCreateForm] = useState<FormState>({ ...EMPTY_FORM })
  const [error, setError] = useState<string | null>(null)

  function updateFilters(patch: Partial<Filters>) {
    setFilters((f) => ({ ...f, ...patch }))
    setPage(1)
  }

  function handleSearchChange(v: string) {
    setSearchInput(v)
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current)
    searchDebounceRef.current = setTimeout(() => setPage(1), 400)
  }

  function resetFilters() {
    setFilters(EMPTY_FILTERS)
    setSearchInput('')
    setPage(1)
  }

  function toggleSort(key: string) {
    if (sortBy === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortBy(key)
      setSortDir('asc')
    }
  }

  const { data: listResult, isLoading } = useQuery({
    queryKey: ['admin-trial-campaigns', filters, searchInput, sortBy, sortDir, page],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size: PAGE_SIZE }
      if (filters.status) params.status = filters.status
      if (filters.moduleId) params.moduleId = filters.moduleId
      if (filters.planId) params.planId = filters.planId
      if (filters.createdBy) params.createdBy = filters.createdBy
      if (filters.startDateFrom) params.startDateFrom = filters.startDateFrom
      if (filters.startDateTo) params.startDateTo = filters.startDateTo
      if (filters.hasSlots) params.hasSlots = true
      if (searchInput) params.search = searchInput
      if (sortBy) { params.sortBy = sortBy; params.sortDir = sortDir }
      const { data } = await api.get<TrialCampaignListResult>('/api/v1/admin/trial-campaigns', { params })
      return data
    },
  })

  const campaigns = listResult?.items ?? []
  const total = listResult?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const { data: modules = [] } = useQuery({
    queryKey: ['admin-modules-all'],
    queryFn: async () => {
      const { data } = await api.get<PlatformModule[]>('/api/v1/admin/modules')
      return data
    },
  })

  const { data: plansForFilter = [] } = useQuery({
    queryKey: ['admin-plans-current-all'],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>('/api/v1/admin/plans')
      return data.filter((p) => p.is_current_version)
    },
  })

  const creatorOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const c of campaigns) {
      if (c.createdByUserId && c.createdByName) map.set(c.createdByUserId, c.createdByName)
    }
    return Array.from(map.entries()).map(([value, label]) => ({ value, label }))
  }, [campaigns])

  function invalidateList() {
    qc.invalidateQueries({ queryKey: ['admin-trial-campaigns'] })
  }

  const createMutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.post('/api/v1/admin/trial-campaigns', payload),
    onSuccess: () => {
      invalidateList()
      setShowCreate(false)
      setCreateForm({ ...EMPTY_FORM })
      setCreatePlanId('')
      setCreateModuleId('')
      setError(null)
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao criar campanha de Trial')
    },
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/admin/trial-campaigns/${id}/cancel`, {}),
    onSuccess: () => {
      invalidateList()
      setCancelCampaign(null)
    },
  })

  const columns: { label: string; sortKey?: string }[] = [
    { label: 'Campanha', sortKey: 'name' },
    { label: 'Módulo', sortKey: 'moduleName' },
    { label: 'Plano', sortKey: 'planName' },
    { label: 'Status', sortKey: 'status' },
    { label: 'Dias' },
    { label: 'Utilização', sortKey: 'usedSlots' },
    { label: 'Vigência', sortKey: 'startDate' },
    { label: 'Ações' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Trials</h1>
          <p className="text-sm text-gray-400 mt-0.5">Campanhas de Trial por módulo/plano — vagas, validade e participantes</p>
        </div>
        {canCreate && (
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors">
            <span>+</span> Nova campanha
          </button>
        )}
      </div>

      <FiltersPanel
        searchInput={searchInput}
        onSearchChange={handleSearchChange}
        filters={filters}
        onChange={updateFilters}
        onReset={resetFilters}
        moduleOptions={modules.map((m) => ({ value: m.id, label: m.name }))}
        planOptions={plansForFilter.map((p) => ({ value: p.id, label: `${p.name} v${p.version}` }))}
        creatorOptions={creatorOptions}
      />

      {showCreate && (
        <div className="rounded-xl bg-gray-800 border border-indigo-700 p-4 space-y-3">
          <h4 className="text-sm font-semibold text-indigo-300">Nova campanha de Trial</h4>
          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}
          <PlanModulePicker
            planId={createPlanId} moduleId={createModuleId}
            onChangePlan={setCreatePlanId} onChangeModule={setCreateModuleId}
          />
          <CampaignForm form={createForm} onChange={setCreateForm} />
          <div className="flex gap-2">
            <button type="button"
              onClick={() => createMutation.mutate(toCampaignPayload(createForm, createModuleId))}
              disabled={!createForm.name.trim() || !createModuleId || createMutation.isPending}
              className="px-4 py-1.5 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors">
              {createMutation.isPending ? 'Salvando…' : 'Criar campanha'}
            </button>
            <button type="button" onClick={() => setShowCreate(false)}
              className="px-4 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
          </div>
        </div>
      )}

      <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-x-auto">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Carregando…</div>
        ) : campaigns.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">Nenhuma campanha de Trial encontrada.</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-700 text-sm">
            <thead>
              <tr className="bg-gray-800/80">
                {columns.map((c) => (
                  <SortableHeader key={c.label} label={c.label} sortKey={c.sortKey}
                    currentSort={sortBy} currentDir={sortDir} onSort={toggleSort} />
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/60">
              {campaigns.map((c) => (
                <tr key={c.id} onClick={() => setDetailId(c.id)}
                  className="hover:bg-gray-700/30 transition-colors cursor-pointer">
                  <td className="px-3 py-3 text-white font-medium whitespace-nowrap">{c.name}</td>
                  <td className="px-3 py-3 text-gray-300 whitespace-nowrap">{c.moduleName}</td>
                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap">{c.planName ?? '—'}{c.planVersion ? ` v${c.planVersion}` : ''}</td>
                  <td className="px-3 py-3"><StatusBadge status={c.status} esgotado={c.usedSlots >= c.maxSlots} expired={c.expired} /></td>
                  <td className="px-3 py-3 text-gray-300">{c.days}</td>
                  <td className="px-3 py-3"><UsageBar used={c.usedSlots} max={c.maxSlots} /></td>
                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap">{fmtDate(c.startDate)} – {fmtDate(c.endDate)}</td>
                  <td className="px-3 py-3">
                    <RowActions campaign={c} onView={setDetailId} onEdit={setEditCampaign} onCancel={setCancelCampaign} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {total > 0 && (
        <div className="flex items-center justify-between text-xs text-gray-400">
          <span>{total} campanha{total !== 1 ? 's' : ''} encontrada{total !== 1 ? 's' : ''}</span>
          {totalPages > 1 && (
            <div className="flex items-center gap-2">
              <button type="button" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}
                className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 hover:bg-gray-600 disabled:opacity-40 transition-colors">
                Anterior
              </button>
              <span>Página {page} de {totalPages}</span>
              <button type="button" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}
                className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 hover:bg-gray-600 disabled:opacity-40 transition-colors">
                Próxima
              </button>
            </div>
          )}
        </div>
      )}

      {detailId && (
        <CampaignDetailDrawer campaignId={detailId} onClose={() => setDetailId(null)} />
      )}

      {editCampaign && (
        <EditCampaignModal campaign={editCampaign} onClose={() => setEditCampaign(null)} onSaved={invalidateList} />
      )}

      {cancelCampaign && (
        <CancelConfirmModal
          campaign={cancelCampaign}
          onClose={() => setCancelCampaign(null)}
          onConfirm={() => cancelMutation.mutate(cancelCampaign.id)}
          isPending={cancelMutation.isPending}
        />
      )}
    </div>
  )
}
