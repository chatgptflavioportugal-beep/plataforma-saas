import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Plan } from '@/types'

// ─── helpers ────────────────────────────────────────────────────────────────

function brl(value: number | null | undefined) {
  if (value == null) return '—'
  if (value === 0) return 'Grátis'
  return `R$ ${value.toFixed(2).replace('.', ',')}`
}

function userLabel(n: number) {
  return n === -1 ? 'Ilimitado' : String(n)
}

const BILLING_LABELS: Record<string, string> = {
  both: 'Mensal e Anual',
  monthly: 'Apenas Mensal',
  annual: 'Apenas Anual',
}

const DEFAULT_FEATURES: Record<string, boolean | number> = {
  'pdf.merge': true,
  'reports.view': true,
  'reports.export': false,
  'ai.agents': false,
  'api.access': false,
  white_label: false,
  priority_support: false,
  max_users: 5,
  max_ai_requests_month: 100,
  max_pdf_merges_month: 50,
}

// ─── tipos de formulário ─────────────────────────────────────────────────────

interface PlanFormData {
  name: string
  code: string
  description: string
  price_monthly: string
  price_annual: string
  discount_annual_percent: string
  max_users: string
  max_ai_requests_month: string
  billing_type: 'monthly' | 'annual' | 'both'
  sort_order: string
  features: Record<string, boolean | number>
}

const EMPTY_FORM: PlanFormData = {
  name: '',
  code: '',
  description: '',
  price_monthly: '',
  price_annual: '',
  discount_annual_percent: '20',
  max_users: '5',
  max_ai_requests_month: '100',
  billing_type: 'both',
  sort_order: '99',
  features: { ...DEFAULT_FEATURES },
}

// ─── componentes auxiliares ──────────────────────────────────────────────────

function Badge({ label, variant }: { label: string; variant: 'green' | 'gray' | 'blue' | 'yellow' }) {
  const colors = {
    green:  'bg-green-900/60 text-green-300 border border-green-700',
    gray:   'bg-gray-700 text-gray-400 border border-gray-600',
    blue:   'bg-blue-900/60 text-blue-300 border border-blue-700',
    yellow: 'bg-yellow-900/60 text-yellow-300 border border-yellow-700',
  }
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${colors[variant]}`}>
      {label}
    </span>
  )
}

function ToggleSwitch({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button
      type="button"
      onClick={onChange}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${
        checked ? 'bg-green-600' : 'bg-gray-600'
      }`}
    >
      <span
        className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-5' : 'translate-x-1'
        }`}
      />
    </button>
  )
}

// ─── modal de formulário ─────────────────────────────────────────────────────

interface PlanModalProps {
  initial?: Plan | null
  onClose: () => void
  onSaved: () => void
}

function PlanModal({ initial, onClose, onSaved }: PlanModalProps) {
  const [form, setForm] = useState<PlanFormData>(
    initial
      ? {
          name: initial.name,
          code: initial.code,
          description: initial.description ?? '',
          price_monthly: String(initial.price_monthly),
          price_annual: String(initial.price_annual ?? ''),
          discount_annual_percent: String(initial.discount_annual_percent),
          max_users: String(initial.max_users),
          max_ai_requests_month: String(initial.max_ai_requests_month),
          billing_type: initial.billing_type,
          sort_order: String(initial.sort_order),
          features: { ...(initial.features as Record<string, boolean | number>) },
        }
      : { ...EMPTY_FORM, features: { ...DEFAULT_FEATURES } }
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isEdit = !!initial

  const priceChanged =
    isEdit &&
    (parseFloat(form.price_monthly) !== initial!.price_monthly ||
      parseFloat(form.price_annual || '0') !== (initial!.price_annual ?? 0) ||
      parseInt(form.max_users) !== initial!.max_users ||
      parseInt(form.max_ai_requests_month) !== initial!.max_ai_requests_month)

  function field(key: keyof PlanFormData, value: string) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function toggleFeatureBool(key: string) {
    setForm((f) => ({
      ...f,
      features: { ...f.features, [key]: !f.features[key] },
    }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        name: form.name,
        code: form.code,
        description: form.description || null,
        price_monthly: parseFloat(form.price_monthly),
        price_annual: form.price_annual ? parseFloat(form.price_annual) : null,
        discount_annual_percent: parseInt(form.discount_annual_percent) || 0,
        max_users: parseInt(form.max_users),
        max_ai_requests_month: parseInt(form.max_ai_requests_month),
        billing_type: form.billing_type,
        sort_order: parseInt(form.sort_order) || 99,
        features: {
          ...form.features,
          max_users: parseInt(form.max_users),
          max_ai_requests_month: parseInt(form.max_ai_requests_month),
        },
      }
      if (isEdit) {
        await api.put(`/api/v1/admin/plans/${initial!.id}`, payload)
      } else {
        await api.post('/api/v1/admin/plans', payload)
      }
      onSaved()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar plano')
    } finally {
      setSaving(false)
    }
  }

  const boolFeatures = ['pdf.merge', 'reports.view', 'reports.export', 'ai.agents', 'api.access', 'white_label', 'priority_support']

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">
            {isEdit ? `Editar: ${initial!.name} v${initial!.version}` : 'Novo Plano'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        {isEdit && priceChanged && (
          <div className="mx-6 mt-4 rounded-lg bg-yellow-900/30 border border-yellow-700 px-4 py-3 text-sm text-yellow-300">
            <strong>Nova versão será criada</strong> — você alterou preço ou limites. Clientes existentes
            permanecem na versão atual até renovação.
          </div>
        )}

        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-5">
          {/* Nome e Código */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input
                required
                value={form.name}
                onChange={(e) => field('name', e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: Starter"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Código *</label>
              <input
                required
                value={form.code}
                onChange={(e) => field('code', e.target.value.toLowerCase().replace(/\s+/g, '_'))}
                disabled={isEdit}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500 disabled:opacity-50"
                placeholder="Ex: starter"
              />
              {isEdit && <p className="mt-1 text-xs text-gray-500">Código não pode ser alterado</p>}
            </div>
          </div>

          {/* Descrição */}
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
            <textarea
              value={form.description}
              onChange={(e) => field('description', e.target.value)}
              rows={2}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 resize-none"
              placeholder="Descrição curta do plano..."
            />
          </div>

          {/* Preços */}
          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Preços</h3>
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Preço Mensal (R$) *</label>
                <input
                  required
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.price_monthly}
                  onChange={(e) => field('price_monthly', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Preço Anual/mês (R$)</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.price_annual}
                  onChange={(e) => field('price_annual', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Desconto Anual (%)</label>
                <input
                  type="number"
                  min="0"
                  max="100"
                  value={form.discount_annual_percent}
                  onChange={(e) => field('discount_annual_percent', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  placeholder="0"
                />
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Tipo de Cobrança</label>
              <select
                value={form.billing_type}
                onChange={(e) => field('billing_type', e.target.value as PlanFormData['billing_type'])}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
              >
                <option value="both">Mensal e Anual</option>
                <option value="monthly">Apenas Mensal</option>
                <option value="annual">Apenas Anual</option>
              </select>
            </div>
          </div>

          {/* Limites */}
          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Limites</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Máx. Usuários (-1 = ilimitado)</label>
                <input
                  type="number"
                  min="-1"
                  value={form.max_users}
                  onChange={(e) => field('max_users', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Máx. IA/mês (-1 = ilimitado)</label>
                <input
                  type="number"
                  min="-1"
                  value={form.max_ai_requests_month}
                  onChange={(e) => field('max_ai_requests_month', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>
          </div>

          {/* Recursos booleanos */}
          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Recursos inclusos</h3>
            <div className="grid grid-cols-2 gap-2">
              {boolFeatures.map((key) => (
                <label key={key} className="flex items-center gap-2 cursor-pointer">
                  <ToggleSwitch
                    checked={!!form.features[key]}
                    onChange={() => toggleFeatureBool(key)}
                  />
                  <span className="text-sm text-gray-300 font-mono">{key}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Ordenação */}
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Ordem de exibição</label>
            <input
              type="number"
              min="0"
              value={form.sort_order}
              onChange={(e) => field('sort_order', e.target.value)}
              className="w-32 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
            />
          </div>

          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">
              {error}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2 pb-2">
            <button type="button" onClick={onClose} className="px-4 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-5 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 disabled:opacity-60 transition-colors"
            >
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Criar plano'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── modal de histórico de versões ───────────────────────────────────────────

function VersionHistoryModal({ planCode, planName, onClose }: { planCode: string; planName: string; onClose: () => void }) {
  const { data: versions = [], isLoading } = useQuery({
    queryKey: ['plan-versions', planCode],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>(`/api/v1/admin/plans/${planCode}/versions`)
      return data
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[80vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <div>
            <h2 className="text-lg font-semibold text-white">Histórico de versões</h2>
            <p className="text-xs text-gray-400 mt-0.5">{planName}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <div className="p-6 space-y-3">
          {isLoading ? (
            <p className="text-sm text-gray-400">Carregando…</p>
          ) : versions.length === 0 ? (
            <p className="text-sm text-gray-400">Nenhuma versão encontrada.</p>
          ) : (
            versions.map((v) => (
              <div key={v.id} className={`rounded-xl border p-4 ${v.is_current_version ? 'border-blue-600 bg-blue-900/10' : 'border-gray-700 bg-gray-800/40'}`}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-white font-semibold">v{v.version}</span>
                    {v.is_current_version && <Badge label="Versão atual" variant="blue" />}
                  </div>
                  <div className="flex items-center gap-3 text-sm text-gray-400">
                    <span>{v.subscriber_count ?? 0} assinante(s)</span>
                    <span>{v.created_at ? new Date(v.created_at).toLocaleDateString('pt-BR') : '—'}</span>
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-3 text-sm">
                  <div>
                    <span className="text-gray-500 text-xs">Mensal</span>
                    <p className="text-white font-medium">{brl(v.price_monthly)}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-xs">Anual/mês</span>
                    <p className="text-white font-medium">{brl(v.price_annual)}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-xs">Desconto anual</span>
                    <p className="text-white font-medium">{v.discount_annual_percent}%</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-xs">Máx. usuários</span>
                    <p className="text-white font-medium">{userLabel(v.max_users)}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-xs">Máx. IA/mês</span>
                    <p className="text-white font-medium">{userLabel(v.max_ai_requests_month)}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-xs">Status</span>
                    <p>
                      <Badge label={v.is_active ? 'Ativo' : 'Inativo'} variant={v.is_active ? 'green' : 'gray'} />
                    </p>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}

// ─── página principal ────────────────────────────────────────────────────────

export function AdminPlansPage() {
  const queryClient = useQueryClient()

  const [showForm, setShowForm]       = useState(false)
  const [editPlan, setEditPlan]       = useState<Plan | null>(null)
  const [historyCode, setHistoryCode] = useState<{ code: string; name: string } | null>(null)
  const [filterCurrent, setFilterCurrent] = useState(true)

  const { data: plans = [], isLoading } = useQuery({
    queryKey: ['admin-plans'],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>('/api/v1/admin/plans')
      return data
    },
    staleTime: 15_000,
    retry: false,
  })

  const toggleStatus = useMutation({
    mutationFn: (id: string) => api.patch(`/api/v1/admin/plans/${id}/status`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-plans'] }),
  })

  function handleSaved() {
    setShowForm(false)
    setEditPlan(null)
    queryClient.invalidateQueries({ queryKey: ['admin-plans'] })
  }

  const displayed = filterCurrent ? plans.filter((p) => p.is_current_version) : plans

  // Agrupa por code para mostrar quantas versões há
  const versionCountByCode: Record<string, number> = {}
  plans.forEach((p) => {
    versionCountByCode[p.code] = (versionCountByCode[p.code] ?? 0) + 1
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Planos</h1>
          <p className="text-sm text-gray-400 mt-0.5">Cadastro, versões e controle comercial</p>
        </div>
        <button
          onClick={() => { setEditPlan(null); setShowForm(true) }}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors"
        >
          <span>+</span> Novo Plano
        </button>
      </div>

      {/* Filtro */}
      <div className="flex items-center gap-3">
        <label className="flex items-center gap-2 cursor-pointer">
          <ToggleSwitch checked={filterCurrent} onChange={() => setFilterCurrent((v) => !v)} />
          <span className="text-sm text-gray-300">Mostrar apenas versões atuais</span>
        </label>
        <span className="text-xs text-gray-500">
          ({displayed.length} plano{displayed.length !== 1 ? 's' : ''} exibido{displayed.length !== 1 ? 's' : ''})
        </span>
      </div>

      {/* Tabela */}
      <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-hidden">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Carregando planos…</div>
        ) : displayed.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">Nenhum plano cadastrado.</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-700 text-sm">
            <thead>
              <tr className="bg-gray-800/80">
                {['Plano', 'Código', 'Versão', 'Mensal', 'Anual/mês', 'Desc. %', 'Usuários', 'IA/mês', 'Cobrança', 'Assinantes', 'Status', 'Ações'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/60">
              {displayed.map((plan) => (
                <tr key={plan.id} className="hover:bg-gray-700/30 transition-colors">
                  <td className="px-4 py-3 text-white font-medium whitespace-nowrap">
                    {plan.name}
                  </td>
                  <td className="px-4 py-3 font-mono text-gray-400 text-xs">{plan.code}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5">
                      <span className="text-gray-300">v{plan.version}</span>
                      {plan.is_current_version && <Badge label="atual" variant="blue" />}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-300 whitespace-nowrap">{brl(plan.price_monthly)}</td>
                  <td className="px-4 py-3 text-gray-300 whitespace-nowrap">{brl(plan.price_annual)}</td>
                  <td className="px-4 py-3 text-gray-400">
                    {plan.discount_annual_percent > 0 ? (
                      <Badge label={`${plan.discount_annual_percent}%`} variant="green" />
                    ) : '—'}
                  </td>
                  <td className="px-4 py-3 text-gray-400">{userLabel(plan.max_users)}</td>
                  <td className="px-4 py-3 text-gray-400">{userLabel(plan.max_ai_requests_month)}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">
                    {BILLING_LABELS[plan.billing_type] ?? plan.billing_type}
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-gray-300 font-semibold">{plan.subscriber_count ?? 0}</span>
                    {versionCountByCode[plan.code] > 1 && (
                      <span className="text-xs text-gray-500 ml-1">
                        ({versionCountByCode[plan.code]} versões)
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <ToggleSwitch
                        checked={plan.is_active}
                        onChange={() => toggleStatus.mutate(plan.id)}
                      />
                      <Badge label={plan.is_active ? 'Ativo' : 'Inativo'} variant={plan.is_active ? 'green' : 'gray'} />
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => { setEditPlan(plan); setShowForm(true) }}
                        className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors"
                      >
                        Editar
                      </button>
                      {versionCountByCode[plan.code] > 1 && (
                        <button
                          onClick={() => setHistoryCode({ code: plan.code, name: plan.name })}
                          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors"
                        >
                          Versões
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Modals */}
      {showForm && (
        <PlanModal
          initial={editPlan}
          onClose={() => { setShowForm(false); setEditPlan(null) }}
          onSaved={handleSaved}
        />
      )}

      {historyCode && (
        <VersionHistoryModal
          planCode={historyCode.code}
          planName={historyCode.name}
          onClose={() => setHistoryCode(null)}
        />
      )}
    </div>
  )
}
