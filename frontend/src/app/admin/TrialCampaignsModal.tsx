import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import type { TrialCampaign, TrialCampaignStatus } from '@/shared/types'

export const STATUS_LABELS: Record<TrialCampaignStatus, string> = {
  ACTIVE: 'Ativo',
  SCHEDULED: 'Programado',
  CLOSED: 'Encerrado',
  CANCELLED: 'Cancelado',
}

const STATUS_VARIANTS: Record<TrialCampaignStatus, 'green' | 'gray' | 'blue' | 'red'> = {
  ACTIVE: 'green',
  SCHEDULED: 'blue',
  CLOSED: 'gray',
  CANCELLED: 'red',
}

export function StatusBadge({ status, esgotado, expired }: { status: TrialCampaignStatus; esgotado: boolean; expired?: boolean }) {
  const cls = {
    green: 'bg-green-900/50 text-green-300 border border-green-700',
    gray: 'bg-gray-700 text-gray-400 border border-gray-600',
    blue: 'bg-blue-900/50 text-blue-300 border border-blue-700',
    red: 'bg-red-900/50 text-red-300 border border-red-700',
  }
  if (status === 'ACTIVE' && esgotado) {
    return <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-amber-900/50 text-amber-300 border border-amber-700">Esgotado</span>
  }
  if (status === 'CLOSED' && expired) {
    return <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-orange-900/50 text-orange-300 border border-orange-700">Expirado</span>
  }
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${cls[STATUS_VARIANTS[status]]}`}>{STATUS_LABELS[status]}</span>
}

export function fmtDate(d: string | null) {
  if (!d) return '—'
  const [y, m, day] = d.split('-')
  return `${day}/${m}/${y}`
}

export interface FormState {
  name: string
  status: TrialCampaignStatus
  days: string
  maxSlots: string
  startDate: string
  endDate: string
  notes: string
  priority: string
}

export const EMPTY_FORM: FormState = {
  name: '', status: 'ACTIVE', days: '7', maxSlots: '100',
  startDate: '', endDate: '', notes: '', priority: '0',
}

export function campaignToForm(c: TrialCampaign): FormState {
  return {
    name: c.name,
    status: c.status,
    days: String(c.days),
    maxSlots: String(c.maxSlots),
    startDate: c.startDate ?? '',
    endDate: c.endDate ?? '',
    notes: c.notes ?? '',
    priority: String(c.priority),
  }
}

// Backend usa Jackson com property-naming-strategy=SNAKE_CASE globalmente — os
// records de request (TrialCampaignRequest) esperam as chaves do JSON em
// snake_case para vincular corretamente aos campos camelCase da record.
export function toCampaignPayload(f: FormState, planVersionModuleId?: string) {
  return {
    ...(planVersionModuleId ? { plan_version_module_id: planVersionModuleId } : {}),
    name: f.name,
    status: f.status,
    days: parseInt(f.days) || null,
    max_slots: parseInt(f.maxSlots) || null,
    start_date: f.startDate || null,
    end_date: f.endDate || null,
    notes: f.notes || null,
    priority: parseInt(f.priority) || 0,
  }
}

interface TrialCampaignsModalProps {
  planId: string
  planVersionModuleId: string
  moduleName: string
  isFreePlan?: boolean
  onClose: () => void
}

export function TrialCampaignsModal({ planId, planVersionModuleId, moduleName, isFreePlan, onClose }: TrialCampaignsModalProps) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState<FormState>({ ...EMPTY_FORM })
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<FormState>({ ...EMPTY_FORM })
  const [error, setError] = useState<string | null>(null)

  const { data: allCampaigns = [], isLoading } = useQuery({
    queryKey: ['trial-campaigns-by-plan', planId],
    queryFn: async () => {
      const { data } = await api.get<TrialCampaign[]>(`/api/v1/admin/trial-campaigns/by-plan/${planId}`)
      return data
    },
  })

  const campaigns = allCampaigns
    .filter((c) => c.planVersionModuleId === planVersionModuleId)
    .sort((a, b) => b.priority - a.priority || a.name.localeCompare(b.name))

  function invalidate() {
    qc.invalidateQueries({ queryKey: ['trial-campaigns-by-plan', planId] })
  }

  const createMutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.post('/api/v1/admin/trial-campaigns', payload),
    onSuccess: () => {
      invalidate()
      setShowCreate(false)
      setCreateForm({ ...EMPTY_FORM })
      setError(null)
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao criar campanha de Trial')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Record<string, unknown> }) =>
      api.put(`/api/v1/admin/trial-campaigns/${id}`, payload),
    onSuccess: () => {
      invalidate()
      setEditingId(null)
      setError(null)
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar campanha de Trial')
    },
  })

  const closeMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/admin/trial-campaigns/${id}/close`, {}),
    onSuccess: invalidate,
  })

  function startEdit(c: TrialCampaign) {
    setEditingId(c.id)
    setEditForm(campaignToForm(c))
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <div>
            <h2 className="text-lg font-semibold text-white">Gerenciar Trials</h2>
            <p className="text-xs text-gray-400 mt-0.5">{moduleName}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <div className="p-6 space-y-4">
          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}

          {isFreePlan && (
            <p className="rounded-lg bg-amber-900/30 border border-amber-700 px-3 py-2 text-sm text-amber-300">
              O plano Free já é a modalidade gratuita permanente do módulo — não é possível criar novas campanhas Trial para ele. Campanhas antigas continuam listadas abaixo apenas para histórico.
            </p>
          )}

          {!showCreate && !isFreePlan && (
            <button type="button"
              onClick={() => { setShowCreate(true); setCreateForm({ ...EMPTY_FORM }) }}
              className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-500 transition-colors">
              + Nova campanha de Trial
            </button>
          )}

          {showCreate && !isFreePlan && (
            <div className="rounded-xl bg-gray-800 border border-indigo-700 p-4 space-y-3">
              <h4 className="text-sm font-semibold text-indigo-300">Nova campanha</h4>
              <CampaignForm form={createForm} onChange={setCreateForm} />
              <div className="flex gap-2">
                <button type="button"
                  onClick={() => createMutation.mutate(toCampaignPayload(createForm, planVersionModuleId))}
                  disabled={!createForm.name.trim() || createMutation.isPending}
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

          {isLoading ? (
            <p className="text-sm text-gray-400 text-center py-4">Carregando…</p>
          ) : campaigns.length === 0 ? (
            <div className="py-6 text-center text-sm text-gray-400 rounded-xl border border-dashed border-gray-700">
              Nenhuma campanha de Trial cadastrada para este módulo.
            </div>
          ) : (
            <div className="space-y-2">
              {campaigns.map((c) => {
                const esgotado = c.usedSlots >= c.maxSlots
                const isEditing = editingId === c.id
                return (
                  <div key={c.id} className="rounded-xl border border-gray-600 bg-gray-800/50 p-4">
                    {isEditing ? (
                      <div className="space-y-3">
                        <CampaignForm form={editForm} onChange={setEditForm} />
                        <p className="text-xs text-gray-500">
                          Dias e vagas só são alterados enquanto a campanha ainda não tiver participantes — depois disso, crie uma nova campanha.
                        </p>
                        <div className="flex gap-2">
                          <button type="button"
                            onClick={() => updateMutation.mutate({ id: c.id, payload: toCampaignPayload(editForm) })}
                            disabled={updateMutation.isPending}
                            className="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors">
                            Salvar
                          </button>
                          <button type="button" onClick={() => setEditingId(null)}
                            className="px-3 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                            Cancelar
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div>
                        <div className="flex items-center justify-between mb-2">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium text-white">{c.name}</span>
                            <StatusBadge status={c.status} esgotado={esgotado} />
                            {c.priority > 0 && (
                              <span className="text-xs text-gray-500">prioridade {c.priority}</span>
                            )}
                          </div>
                          <div className="flex items-center gap-1.5">
                            <button type="button" onClick={() => startEdit(c)}
                              className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                              Editar
                            </button>
                            {c.status !== 'CLOSED' && c.status !== 'CANCELLED' && (
                              <button type="button"
                                onClick={() => { if (confirm(`Encerrar a campanha "${c.name}"?`)) closeMutation.mutate(c.id) }}
                                className="px-2.5 py-1 rounded-md bg-red-900/60 text-red-300 text-xs hover:bg-red-800/60 transition-colors">
                                Encerrar
                              </button>
                            )}
                          </div>
                        </div>
                        <div className="grid grid-cols-4 gap-3 text-xs">
                          <div>
                            <span className="text-gray-500">Vagas</span>
                            <p className="text-gray-200 font-medium">{c.usedSlots} / {c.maxSlots}</p>
                          </div>
                          <div>
                            <span className="text-gray-500">Dias</span>
                            <p className="text-gray-200 font-medium">{c.days}</p>
                          </div>
                          <div>
                            <span className="text-gray-500">Início</span>
                            <p className="text-gray-200 font-medium">{fmtDate(c.startDate)}</p>
                          </div>
                          <div>
                            <span className="text-gray-500">Fim</span>
                            <p className="text-gray-200 font-medium">{fmtDate(c.endDate)}</p>
                          </div>
                        </div>
                        {c.notes && <p className="mt-2 text-xs text-gray-500">{c.notes}</p>}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export function CampaignForm({ form, onChange }: { form: FormState; onChange: (f: FormState) => void }) {
  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    onChange({ ...form, [key]: value })
  }
  return (
    <div className="space-y-3">
      <div>
        <label className="block text-xs text-gray-400 mb-1">Nome *</label>
        <input value={form.name} onChange={(e) => set('name', e.target.value)}
          placeholder="Ex: Trial Julho, Trial Black Friday"
          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500" />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-gray-400 mb-1">Status</label>
          <select value={form.status} onChange={(e) => set('status', e.target.value as TrialCampaignStatus)}
            disabled={form.status === 'CANCELLED'}
            className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 disabled:opacity-50">
            <option value="ACTIVE">Ativo</option>
            <option value="SCHEDULED">Programado</option>
            <option value="CLOSED">Encerrado</option>
            {form.status === 'CANCELLED' && <option value="CANCELLED">Cancelado</option>}
          </select>
          {form.status === 'CANCELLED' && (
            <p className="mt-1 text-xs text-gray-500">Cancelamento é feito pela ação dedicada "Cancelar" — não pode ser revertido por aqui.</p>
          )}
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Prioridade</label>
          <input type="number" value={form.priority} onChange={(e) => set('priority', e.target.value)}
            className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Dias de Trial *</label>
          <input type="number" min="1" max="365" value={form.days} onChange={(e) => set('days', e.target.value)}
            className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Máx. de usuários *</label>
          <input type="number" min="1" value={form.maxSlots} onChange={(e) => set('maxSlots', e.target.value)}
            className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Data inicial</label>
          <input type="date" value={form.startDate} onChange={(e) => set('startDate', e.target.value)}
            className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Data final</label>
          <input type="date" value={form.endDate} onChange={(e) => set('endDate', e.target.value)}
            className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500" />
        </div>
      </div>
      <div>
        <label className="block text-xs text-gray-400 mb-1">Observações</label>
        <textarea value={form.notes} onChange={(e) => set('notes', e.target.value)} rows={2}
          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 resize-none" />
      </div>
    </div>
  )
}
