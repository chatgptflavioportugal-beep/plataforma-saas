import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
import type { Plan, PlanVersionModule, TrialCampaign, TrialCampaignParticipant, TrialCampaignStatus } from '@/shared/types'
import { StatusBadge, fmtDate, CampaignForm, EMPTY_FORM, toCampaignPayload, type FormState } from './TrialCampaignsModal'

function fmtDateTime(d: string | null) {
  if (!d) return '—'
  return new Date(d).toLocaleString('pt-BR')
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

// ─── drawer de detalhe ──────────────────────────────────────────────────────────

function CampaignDetailDrawer({ campaignId, onClose }: { campaignId: string; onClose: () => void }) {
  const [tab, setTab] = useState<'indicadores' | 'participantes'>('indicadores')
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
            {(['indicadores', 'participantes'] as const).map((t) => (
              <button key={t} type="button" onClick={() => setTab(t)}
                className={`px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                  tab === t ? 'border-indigo-500 text-indigo-300' : 'border-transparent text-gray-400 hover:text-gray-200'
                }`}>
                {t === 'indicadores' ? 'Indicadores' : 'Participantes'}
              </button>
            ))}
          </div>
        </div>

        <div className="p-6">
          {isLoading || !detail ? (
            <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
          ) : tab === 'indicadores' ? (
            <div className="space-y-4">
              <div className="flex items-center gap-2">
                <StatusBadge status={detail.status} esgotado={detail.usedSlots >= detail.maxSlots} />
              </div>
              <div className="grid grid-cols-3 gap-4 text-sm">
                <div>
                  <p className="text-xs text-gray-500">Usuários</p>
                  <p className="text-white font-semibold">{detail.usedSlots} / {detail.maxSlots}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Dias</p>
                  <p className="text-white font-semibold">{detail.days}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Prioridade</p>
                  <p className="text-white font-semibold">{detail.priority}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Início</p>
                  <p className="text-white font-semibold">{fmtDate(detail.startDate)}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Fim</p>
                  <p className="text-white font-semibold">{fmtDate(detail.endDate)}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Conversão</p>
                  <p className="text-white font-semibold">{detail.conversionPercent ?? 0}%</p>
                </div>
              </div>
              <p className="text-xs text-gray-500">{detail.totalParticipants ?? 0} participante(s) no total.</p>
              {detail.notes && (
                <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-3">
                  <p className="text-xs text-gray-500 mb-1">Observações</p>
                  <p className="text-sm text-gray-300">{detail.notes}</p>
                </div>
              )}
            </div>
          ) : (
            <ParticipantsTab campaignId={campaignId} />
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

  const [filterStatus, setFilterStatus] = useState('')
  const [detailId, setDetailId] = useState<string | null>(null)

  const [showCreate, setShowCreate] = useState(false)
  const [createPlanId, setCreatePlanId] = useState('')
  const [createModuleId, setCreateModuleId] = useState('')
  const [createForm, setCreateForm] = useState<FormState>({ ...EMPTY_FORM })
  const [error, setError] = useState<string | null>(null)

  const { data: campaigns = [], isLoading } = useQuery({
    queryKey: ['admin-trial-campaigns', filterStatus],
    queryFn: async () => {
      const { data } = await api.get<TrialCampaign[]>('/api/v1/admin/trial-campaigns', {
        params: filterStatus ? { status: filterStatus } : {},
      })
      return data
    },
  })

  const createMutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.post('/api/v1/admin/trial-campaigns', payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-trial-campaigns'] })
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

  const statusOptions: { value: TrialCampaignStatus | ''; label: string }[] = [
    { value: '', label: 'Todos os status' },
    { value: 'ACTIVE', label: 'Ativo' },
    { value: 'SCHEDULED', label: 'Programado' },
    { value: 'CLOSED', label: 'Encerrado' },
    { value: 'CANCELLED', label: 'Cancelado' },
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

      <div className="flex items-center gap-3">
        <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}
          className="rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500">
          {statusOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <span className="text-xs text-gray-500">({campaigns.length} campanha{campaigns.length !== 1 ? 's' : ''})</span>
      </div>

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
          <div className="py-12 text-center text-sm text-gray-400">Nenhuma campanha de Trial cadastrada.</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-700 text-sm">
            <thead>
              <tr className="bg-gray-800/80">
                {['Campanha', 'Módulo', 'Plano', 'Status', 'Vagas', 'Dias', 'Início', 'Fim', 'Prior.'].map((h) => (
                  <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">{h}</th>
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
                  <td className="px-3 py-3"><StatusBadge status={c.status} esgotado={c.usedSlots >= c.maxSlots} /></td>
                  <td className="px-3 py-3 text-gray-300 whitespace-nowrap">{c.usedSlots} / {c.maxSlots}</td>
                  <td className="px-3 py-3 text-gray-300">{c.days}</td>
                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap">{fmtDate(c.startDate)}</td>
                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap">{fmtDate(c.endDate)}</td>
                  <td className="px-3 py-3 text-gray-400">{c.priority}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {detailId && (
        <CampaignDetailDrawer campaignId={detailId} onClose={() => setDetailId(null)} />
      )}
    </div>
  )
}
