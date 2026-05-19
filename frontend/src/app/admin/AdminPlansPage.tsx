import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import type { Plan, PlanVersionModule, PlanVersionModuleLimit, PlatformModule } from '@/shared/types'

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

const BOOL_FEATURES = [
  'pdf.merge', 'reports.view', 'reports.export',
  'ai.agents', 'api.access', 'white_label', 'priority_support',
]

const DEFAULT_FEATURES: Record<string, boolean | number> = {
  'pdf.merge': true, 'reports.view': true, 'reports.export': false,
  'ai.agents': false, 'api.access': false, white_label: false, priority_support: false,
  max_users: 5, max_ai_requests_month: 100, max_pdf_merges_month: 50,
}

function parseLimits(limitsJson: string | undefined): PlanVersionModuleLimit[] {
  if (!limitsJson) return []
  try { return JSON.parse(limitsJson) ?? [] } catch { return [] }
}

// ─── tipos locais ────────────────────────────────────────────────────────────

interface FormData {
  name: string
  code: string
  description: string
  max_users: string
  max_ai_requests_month: string
  billing_type: 'monthly' | 'annual' | 'both'
  plan_type: 'individual' | 'business'
  sort_order: string
  features: Record<string, boolean | number>
}

function planToForm(p: Plan): FormData {
  return {
    name: p.name,
    code: p.code,
    description: p.description ?? '',
    max_users: String(p.max_users),
    max_ai_requests_month: String(p.max_ai_requests_month),
    billing_type: p.billing_type,
    plan_type: p.plan_type ?? 'business',
    sort_order: String(p.sort_order),
    features: { ...(p.features as Record<string, boolean | number>) },
  }
}

const EMPTY_FORM: FormData = {
  name: '', code: '', description: '',
  max_users: '5', max_ai_requests_month: '100',
  billing_type: 'both', plan_type: 'business', sort_order: '99',
  features: { ...DEFAULT_FEATURES },
}

interface ModuleForm {
  moduleId: string
  monthlyPrice: string
  annualPrice: string
  status: 'active' | 'inactive'
  sortOrder: string
}

const EMPTY_MODULE_FORM: ModuleForm = {
  moduleId: '', monthlyPrice: '0', annualPrice: '0', status: 'active', sortOrder: '99',
}

interface LimitForm {
  title: string
  description: string
  limitKey: string
  limitValue: string
  unit: string
  sortOrder: string
}

const EMPTY_LIMIT_FORM: LimitForm = {
  title: '', description: '', limitKey: '', limitValue: '', unit: '', sortOrder: '99',
}

// ─── componentes base ─────────────────────────────────────────────────────────

function Badge({ label, variant }: { label: string; variant: 'green' | 'gray' | 'blue' | 'yellow' | 'red' }) {
  const cls = {
    green:  'bg-green-900/50 text-green-300 border border-green-700',
    gray:   'bg-gray-700 text-gray-400 border border-gray-600',
    blue:   'bg-blue-900/50 text-blue-300 border border-blue-700',
    yellow: 'bg-yellow-900/50 text-yellow-300 border border-yellow-700',
    red:    'bg-red-900/50 text-red-300 border border-red-700',
  }
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${cls[variant]}`}>{label}</span>
}

function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onChange}
      disabled={disabled}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none disabled:opacity-40 ${
        checked ? 'bg-green-600' : 'bg-gray-600'
      }`}
    >
      <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${
        checked ? 'translate-x-5' : 'translate-x-1'
      }`} />
    </button>
  )
}

function StarButton({ active, disabled, onClick }: { active: boolean; disabled?: boolean; onClick: () => void }) {
  const isClickable = !disabled && !active
  const title = active
    ? 'Já é o Mais Popular'
    : disabled
    ? 'Plano inativo não pode ser definido como Mais Popular'
    : 'Marcar como Mais Popular'
  return (
    <button
      type="button"
      title={title}
      disabled={disabled || active}
      onClick={onClick}
      className={`text-lg transition-colors ${
        active ? 'text-yellow-400 cursor-default'
        : isClickable ? 'text-gray-500 hover:text-yellow-400 hover:scale-110'
        : 'text-gray-600 opacity-30 cursor-not-allowed'
      }`}
    >
      {active ? '★' : '☆'}
    </button>
  )
}

// ─── formulário de plano (criar / nova versão) ───────────────────────────────

interface PlanFormProps {
  mode: 'create' | 'new-version'
  sourcePlan?: Plan
  onClose: () => void
  onSaved: (planId: string) => void
}

function PlanForm({ mode, sourcePlan, onClose, onSaved }: PlanFormProps) {
  const [form, setForm] = useState<FormData>(
    mode === 'new-version' && sourcePlan ? planToForm(sourcePlan) : { ...EMPTY_FORM, features: { ...DEFAULT_FEATURES } }
  )
  const [saving, setSaving] = useState(false)
  const [error, setError]   = useState<string | null>(null)

  const nextVersion = sourcePlan ? sourcePlan.version + 1 : 1

  function field(key: keyof FormData, value: string) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function toggleFeature(key: string) {
    setForm((f) => ({ ...f, features: { ...f.features, [key]: !f.features[key] } }))
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
        max_users: parseInt(form.max_users),
        max_ai_requests_month: parseInt(form.max_ai_requests_month),
        billing_type: form.billing_type,
        plan_type: form.plan_type,
        sort_order: parseInt(form.sort_order) || 99,
        features: {
          ...form.features,
          max_users: parseInt(form.max_users),
          max_ai_requests_month: parseInt(form.max_ai_requests_month),
        },
      }

      let result: { id: string }
      if (mode === 'new-version' && sourcePlan) {
        const { data } = await api.post<{ id: string }>(`/api/v1/admin/plans/${sourcePlan.id}/new-version`, payload)
        result = data
      } else {
        const { data } = await api.post<{ id: string }>('/api/v1/admin/plans', payload)
        result = data
      }
      onSaved(result.id)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar plano')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[92vh] overflow-y-auto">

        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <div>
            <h2 className="text-lg font-semibold text-white">
              {mode === 'new-version' ? `Nova versão: ${sourcePlan!.name}` : 'Novo Plano'}
            </h2>
            {mode === 'new-version' && (
              <p className="text-xs text-gray-400 mt-0.5">
                v{sourcePlan!.version} → <span className="text-blue-400 font-semibold">v{nextVersion}</span>
              </p>
            )}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        {mode === 'new-version' && (
          <div className="mx-6 mt-4 rounded-lg bg-blue-900/30 border border-blue-700 px-4 py-3 text-sm text-blue-200">
            <strong>Geração de nova versão</strong> — a versão{' '}
            <span className="font-semibold">v{sourcePlan!.version}</span> será preservada e os módulos serão copiados.
            Clientes existentes continuam vinculados à versão anterior.
          </div>
        )}

        <div className="mx-6 mt-4 rounded-lg bg-indigo-900/20 border border-indigo-700 px-4 py-3 text-sm text-indigo-200">
          Os preços do plano são calculados automaticamente pela soma dos módulos adicionados.
          Após salvar, acesse <strong>"Módulos"</strong> para configurar os módulos e seus preços.
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-5">
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
                disabled={mode === 'new-version'}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500 disabled:opacity-50"
                placeholder="Ex: starter"
              />
              {mode === 'new-version' && (
                <p className="mt-1 text-xs text-gray-500">Código imutável — vinculado ao grupo do plano</p>
              )}
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
            <textarea
              value={form.description}
              onChange={(e) => field('description', e.target.value)}
              rows={2}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 resize-none"
            />
          </div>

          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Configurações de cobrança</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Tipos de cobrança disponíveis</label>
                <select
                  value={form.billing_type}
                  onChange={(e) => field('billing_type', e.target.value as FormData['billing_type'])}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                >
                  <option value="both">Mensal e Anual</option>
                  <option value="monthly">Apenas Mensal</option>
                  <option value="annual">Apenas Anual</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Tipo de plano *</label>
                <select
                  value={form.plan_type}
                  onChange={(e) => field('plan_type', e.target.value as FormData['plan_type'])}
                  disabled={mode === 'new-version'}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 disabled:opacity-50"
                >
                  <option value="business">Empresarial</option>
                  <option value="individual">Individual</option>
                </select>
                {mode === 'new-version' && <p className="mt-1 text-xs text-gray-500">Imutável na nova versão</p>}
              </div>
            </div>
          </div>

          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Limites globais</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Máx. usuários (-1 = ilimitado)</label>
                <input
                  type="number" min="-1"
                  value={form.max_users}
                  onChange={(e) => field('max_users', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Máx. IA/mês (-1 = ilimitado)</label>
                <input
                  type="number" min="-1"
                  value={form.max_ai_requests_month}
                  onChange={(e) => field('max_ai_requests_month', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>
          </div>

          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Recursos (flags)</h3>
            <div className="grid grid-cols-2 gap-2">
              {BOOL_FEATURES.map((key) => (
                <label key={key} className="flex items-center gap-2 cursor-pointer">
                  <Toggle checked={!!form.features[key]} onChange={() => toggleFeature(key)} />
                  <span className="text-sm text-gray-300 font-mono">{key}</span>
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Ordem de exibição</label>
            <input
              type="number" min="0"
              value={form.sort_order}
              onChange={(e) => field('sort_order', e.target.value)}
              className="w-32 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
            />
          </div>

          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}

          <div className="flex justify-end gap-3 pt-2 pb-2">
            <button type="button" onClick={onClose}
              className="px-4 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
            <button type="submit" disabled={saving}
              className={`px-5 py-2 rounded-lg text-white text-sm font-medium disabled:opacity-60 transition-colors ${
                mode === 'new-version' ? 'bg-indigo-600 hover:bg-indigo-500' : 'bg-blue-600 hover:bg-blue-500'
              }`}>
              {saving ? 'Salvando…' : mode === 'new-version' ? `Criar v${nextVersion}` : 'Criar plano'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── modal histórico de versões ───────────────────────────────────────────────

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
          ) : versions.map((v) => (
            <div key={v.id}
              className={`rounded-xl border p-4 ${v.is_current_version ? 'border-blue-600 bg-blue-900/10' : 'border-gray-700 bg-gray-800/40'}`}>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <span className="text-white font-semibold">v{v.version}</span>
                  {v.is_current_version && <Badge label="versão atual" variant="blue" />}
                  {v.is_most_popular && <Badge label="★ mais popular" variant="yellow" />}
                </div>
                <div className="flex items-center gap-3 text-sm text-gray-400">
                  <span>{v.subscriber_count ?? 0} assinante(s)</span>
                  <span>{v.created_at ? new Date(v.created_at).toLocaleDateString('pt-BR') : '—'}</span>
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3 text-sm">
                <div>
                  <span className="text-gray-500 text-xs">Total Mensal</span>
                  <p className="text-white font-medium">{brl(v.total_monthly_price ?? v.price_monthly)}</p>
                  {(v.module_count ?? 0) > 0 && (
                    <p className="text-xs text-indigo-400">{v.module_count} módulo(s)</p>
                  )}
                </div>
                <div>
                  <span className="text-gray-500 text-xs">Total Anual/mês</span>
                  <p className="text-white font-medium">{brl(v.total_annual_price ?? v.price_annual)}</p>
                </div>
                <div>
                  <span className="text-gray-500 text-xs">Usuários</span>
                  <p className="text-white font-medium">{userLabel(v.max_users)}</p>
                </div>
                <div>
                  <span className="text-gray-500 text-xs">IA/mês</span>
                  <p className="text-white font-medium">{userLabel(v.max_ai_requests_month)}</p>
                </div>
                <div>
                  <span className="text-gray-500 text-xs">Cobrança</span>
                  <p className="text-gray-300 text-xs">{BILLING_LABELS[v.billing_type] ?? v.billing_type}</p>
                </div>
                <div>
                  <span className="text-gray-500 text-xs">Status</span>
                  <p><Badge label={v.is_active ? 'Ativo' : 'Inativo'} variant={v.is_active ? 'green' : 'gray'} /></p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ─── modal de módulos do plano ────────────────────────────────────────────────

interface PlanModulesModalProps {
  plan: Plan
  onClose: () => void
}

function PlanModulesModal({ plan, onClose }: PlanModulesModalProps) {
  const qc = useQueryClient()

  const [showAddModule, setShowAddModule] = useState(false)
  const [addModuleForm, setAddModuleForm] = useState<ModuleForm>({ ...EMPTY_MODULE_FORM })
  const [editModuleId, setEditModuleId]   = useState<string | null>(null)
  const [editModuleForm, setEditModuleForm] = useState<ModuleForm>({ ...EMPTY_MODULE_FORM })
  const [expandedModuleId, setExpandedModuleId] = useState<string | null>(null)
  const [addingLimitPvmId, setAddingLimitPvmId] = useState<string | null>(null)
  const [addLimitForm, setAddLimitForm] = useState<LimitForm>({ ...EMPTY_LIMIT_FORM })
  const [editLimitId, setEditLimitId]   = useState<string | null>(null)
  const [editLimitForm, setEditLimitForm] = useState<LimitForm>({ ...EMPTY_LIMIT_FORM })
  const [mutErr, setMutErr] = useState<string | null>(null)

  const { data: modules = [], isLoading } = useQuery({
    queryKey: ['plan-modules', plan.id],
    queryFn: async () => {
      const { data } = await api.get<PlanVersionModule[]>(`/api/v1/admin/plans/${plan.id}/modules`)
      return data
    },
  })

  const { data: allModules = [] } = useQuery({
    queryKey: ['platform-modules-active'],
    queryFn: async () => {
      const { data } = await api.get<PlatformModule[]>('/api/v1/admin/modules?is_active=true')
      return data
    },
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['plan-modules', plan.id] })
    qc.invalidateQueries({ queryKey: ['admin-plans'] })
  }

  const addModule = useMutation({
    mutationFn: (form: ModuleForm) => api.post(`/api/v1/admin/plans/${plan.id}/modules`, {
      module_id: form.moduleId,
      monthly_price: parseFloat(form.monthlyPrice) || 0,
      annual_price: parseFloat(form.annualPrice) || 0,
      status: form.status,
      sort_order: parseInt(form.sortOrder) || 99,
    }),
    onSuccess: () => { invalidate(); setShowAddModule(false); setAddModuleForm({ ...EMPTY_MODULE_FORM }); setMutErr(null) },
    onError: (e: unknown) => setMutErr((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao adicionar módulo'),
  })

  const updateModule = useMutation({
    mutationFn: ({ pvmId, form }: { pvmId: string; form: ModuleForm }) =>
      api.patch(`/api/v1/admin/plans/${plan.id}/modules/${pvmId}`, {
        monthly_price: parseFloat(form.monthlyPrice) || 0,
        annual_price: parseFloat(form.annualPrice) || 0,
        status: form.status,
        sort_order: parseInt(form.sortOrder) || 99,
      }),
    onSuccess: () => { invalidate(); setEditModuleId(null); setMutErr(null) },
    onError: (e: unknown) => setMutErr((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao atualizar módulo'),
  })

  const removeModule = useMutation({
    mutationFn: (pvmId: string) => api.delete(`/api/v1/admin/plans/${plan.id}/modules/${pvmId}`),
    onSuccess: () => { invalidate(); setMutErr(null) },
    onError: (e: unknown) => setMutErr((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao remover módulo'),
  })

  const addLimit = useMutation({
    mutationFn: ({ pvmId, form }: { pvmId: string; form: LimitForm }) =>
      api.post(`/api/v1/admin/plans/${plan.id}/modules/${pvmId}/limits`, {
        title: form.title,
        description: form.description || null,
        limit_key: form.limitKey || null,
        limit_value: form.limitValue || null,
        unit: form.unit || null,
        sort_order: parseInt(form.sortOrder) || 99,
      }),
    onSuccess: () => { invalidate(); setAddingLimitPvmId(null); setAddLimitForm({ ...EMPTY_LIMIT_FORM }); setMutErr(null) },
    onError: (e: unknown) => setMutErr((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao adicionar limitação'),
  })

  const updateLimit = useMutation({
    mutationFn: ({ pvmId, limitId, form }: { pvmId: string; limitId: string; form: LimitForm }) =>
      api.patch(`/api/v1/admin/plans/${plan.id}/modules/${pvmId}/limits/${limitId}`, {
        title: form.title,
        description: form.description || null,
        limit_key: form.limitKey || null,
        limit_value: form.limitValue || null,
        unit: form.unit || null,
        sort_order: parseInt(form.sortOrder) || 99,
      }),
    onSuccess: () => { invalidate(); setEditLimitId(null); setMutErr(null) },
    onError: (e: unknown) => setMutErr((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao atualizar limitação'),
  })

  const removeLimit = useMutation({
    mutationFn: ({ pvmId, limitId }: { pvmId: string; limitId: string }) =>
      api.delete(`/api/v1/admin/plans/${plan.id}/modules/${pvmId}/limits/${limitId}`),
    onSuccess: () => { invalidate(); setMutErr(null) },
    onError: (e: unknown) => setMutErr((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao remover limitação'),
  })

  const usedModuleIds = new Set(modules.map((m) => m.module_id))
  const availableToAdd = allModules.filter((m) => !usedModuleIds.has(m.id))

  const totalMonthly = modules.filter((m) => m.status === 'active').reduce((s, m) => s + (m.monthly_price ?? 0), 0)
  const totalAnnual  = modules.filter((m) => m.status === 'active').reduce((s, m) => s + (m.annual_price ?? 0), 0)

  function startEditModule(pvm: PlanVersionModule) {
    setEditModuleId(pvm.id)
    setEditModuleForm({
      moduleId: pvm.module_id,
      monthlyPrice: String(pvm.monthly_price),
      annualPrice: String(pvm.annual_price),
      status: pvm.status,
      sortOrder: String(pvm.sort_order),
    })
  }

  function startEditLimit(limit: PlanVersionModuleLimit) {
    setEditLimitId(limit.id)
    setEditLimitForm({
      title: limit.title,
      description: limit.description ?? '',
      limitKey: limit.limit_key ?? '',
      limitValue: limit.limit_value ?? '',
      unit: limit.unit ?? '',
      sortOrder: String(limit.sort_order),
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-3xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[92vh] flex flex-col">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700 shrink-0">
          <div>
            <h2 className="text-lg font-semibold text-white">Módulos do Plano</h2>
            <p className="text-xs text-gray-400 mt-0.5">{plan.name} · v{plan.version}</p>
          </div>
          <div className="flex items-center gap-3">
            {!showAddModule && availableToAdd.length > 0 && (
              <button
                onClick={() => setShowAddModule(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-500 transition-colors"
              >
                + Adicionar módulo
              </button>
            )}
            <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
          </div>
        </div>

        {/* Erro global */}
        {mutErr && (
          <div className="mx-6 mt-3 flex items-center justify-between rounded-lg bg-red-900/30 border border-red-700 px-4 py-2.5 text-sm text-red-300 shrink-0">
            <span>{mutErr}</span>
            <button onClick={() => setMutErr(null)} className="ml-4 text-red-400 hover:text-red-200 leading-none">✕</button>
          </div>
        )}

        {/* Formulário adicionar módulo */}
        {showAddModule && (
          <div className="mx-6 mt-3 rounded-xl bg-gray-800 border border-indigo-700 p-4 shrink-0">
            <h4 className="text-sm font-semibold text-indigo-300 mb-3">Adicionar módulo</h4>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <div className="col-span-2">
                <label className="block text-xs text-gray-400 mb-1">Módulo *</label>
                <select
                  value={addModuleForm.moduleId}
                  onChange={(e) => setAddModuleForm((f) => ({ ...f, moduleId: e.target.value }))}
                  className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                >
                  <option value="">Selecione um módulo…</option>
                  {availableToAdd.map((m) => (
                    <option key={m.id} value={m.id}>{m.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Preço Mensal (R$)</label>
                <input type="number" min="0" step="0.01"
                  value={addModuleForm.monthlyPrice}
                  onChange={(e) => setAddModuleForm((f) => ({ ...f, monthlyPrice: e.target.value }))}
                  className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Preço Anual/mês (R$)</label>
                <input type="number" min="0" step="0.01"
                  value={addModuleForm.annualPrice}
                  onChange={(e) => setAddModuleForm((f) => ({ ...f, annualPrice: e.target.value }))}
                  className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Status</label>
                <select
                  value={addModuleForm.status}
                  onChange={(e) => setAddModuleForm((f) => ({ ...f, status: e.target.value as 'active' | 'inactive' }))}
                  className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                >
                  <option value="active">Ativo</option>
                  <option value="inactive">Inativo</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Ordem</label>
                <input type="number" min="0"
                  value={addModuleForm.sortOrder}
                  onChange={(e) => setAddModuleForm((f) => ({ ...f, sortOrder: e.target.value }))}
                  className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => { if (!addModuleForm.moduleId) return; addModule.mutate(addModuleForm) }}
                disabled={!addModuleForm.moduleId || addModule.isPending}
                className="px-4 py-1.5 rounded-lg bg-indigo-600 text-white text-sm hover:bg-indigo-500 disabled:opacity-50 transition-colors"
              >
                {addModule.isPending ? 'Salvando…' : 'Salvar módulo'}
              </button>
              <button
                onClick={() => { setShowAddModule(false); setAddModuleForm({ ...EMPTY_MODULE_FORM }) }}
                className="px-4 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-sm hover:bg-gray-600 transition-colors"
              >
                Cancelar
              </button>
            </div>
          </div>
        )}

        {/* Lista de módulos */}
        <div className="flex-1 overflow-y-auto px-6 py-3 space-y-2">
          {isLoading ? (
            <p className="text-sm text-gray-400 py-8 text-center">Carregando módulos…</p>
          ) : modules.length === 0 ? (
            <div className="py-10 text-center text-sm text-gray-400">
              <p className="mb-2">Nenhum módulo adicionado.</p>
              <p className="text-xs text-gray-500">Clique em "Adicionar módulo" para começar.</p>
            </div>
          ) : modules.map((pvm) => {
            const limits = parseLimits(pvm.limits_json)
            const isExpanded = expandedModuleId === pvm.id
            const isEditing  = editModuleId === pvm.id

            return (
              <div key={pvm.id} className={`rounded-xl border ${pvm.status === 'active' ? 'border-gray-600 bg-gray-800/50' : 'border-gray-700 bg-gray-800/20 opacity-70'}`}>

                {/* Linha do módulo */}
                {isEditing ? (
                  <div className="p-4 space-y-3">
                    <p className="text-sm font-medium text-white">{pvm.module_name}</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs text-gray-400 mb-1">Preço Mensal (R$)</label>
                        <input type="number" min="0" step="0.01"
                          value={editModuleForm.monthlyPrice}
                          onChange={(e) => setEditModuleForm((f) => ({ ...f, monthlyPrice: e.target.value }))}
                          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-400 mb-1">Preço Anual/mês (R$)</label>
                        <input type="number" min="0" step="0.01"
                          value={editModuleForm.annualPrice}
                          onChange={(e) => setEditModuleForm((f) => ({ ...f, annualPrice: e.target.value }))}
                          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-400 mb-1">Status</label>
                        <select
                          value={editModuleForm.status}
                          onChange={(e) => setEditModuleForm((f) => ({ ...f, status: e.target.value as 'active' | 'inactive' }))}
                          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                        >
                          <option value="active">Ativo</option>
                          <option value="inactive">Inativo</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs text-gray-400 mb-1">Ordem</label>
                        <input type="number" min="0"
                          value={editModuleForm.sortOrder}
                          onChange={(e) => setEditModuleForm((f) => ({ ...f, sortOrder: e.target.value }))}
                          className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                        />
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => updateModule.mutate({ pvmId: pvm.id, form: editModuleForm })}
                        disabled={updateModule.isPending}
                        className="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors"
                      >
                        {updateModule.isPending ? 'Salvando…' : 'Salvar'}
                      </button>
                      <button
                        onClick={() => setEditModuleId(null)}
                        className="px-3 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors"
                      >
                        Cancelar
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center justify-between px-4 py-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <span className="text-white font-medium text-sm truncate">{pvm.module_name}</span>
                      <Badge label={pvm.status === 'active' ? 'Ativo' : 'Inativo'} variant={pvm.status === 'active' ? 'green' : 'gray'} />
                    </div>
                    <div className="flex items-center gap-4 ml-4 shrink-0">
                      <div className="text-right">
                        <p className="text-xs text-gray-500">Mensal</p>
                        <p className="text-sm text-white font-medium">{brl(pvm.monthly_price)}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-xs text-gray-500">Anual/mês</p>
                        <p className="text-sm text-white font-medium">{brl(pvm.annual_price)}</p>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => setExpandedModuleId(isExpanded ? null : pvm.id)}
                          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors whitespace-nowrap"
                          title={isExpanded ? 'Ocultar limitações' : 'Ver limitações'}
                        >
                          {isExpanded ? '▲' : '▼'} Limitações {limits.length > 0 && `(${limits.length})`}
                        </button>
                        <button
                          onClick={() => startEditModule(pvm)}
                          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors"
                        >
                          Editar
                        </button>
                        <button
                          onClick={() => { if (confirm(`Remover "${pvm.module_name}" deste plano?`)) removeModule.mutate(pvm.id) }}
                          disabled={removeModule.isPending}
                          className="px-2.5 py-1 rounded-md bg-red-900/60 text-red-300 text-xs hover:bg-red-800/60 disabled:opacity-50 transition-colors"
                        >
                          Remover
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* Seção de limitações (expandida) */}
                {isExpanded && !isEditing && (
                  <div className="border-t border-gray-700 px-4 py-3">
                    <div className="flex items-center justify-between mb-2">
                      <h5 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Limitações</h5>
                      {addingLimitPvmId !== pvm.id && (
                        <button
                          onClick={() => { setAddingLimitPvmId(pvm.id); setAddLimitForm({ ...EMPTY_LIMIT_FORM }); setEditLimitId(null) }}
                          className="px-2 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors"
                        >
                          + Adicionar
                        </button>
                      )}
                    </div>

                    {/* Formulário adicionar limitação */}
                    {addingLimitPvmId === pvm.id && (
                      <div className="mb-3 rounded-lg bg-gray-700/50 border border-gray-600 p-3">
                        <div className="grid grid-cols-2 gap-2 mb-2">
                          <div className="col-span-2">
                            <input
                              placeholder="Título *"
                              value={addLimitForm.title}
                              onChange={(e) => setAddLimitForm((f) => ({ ...f, title: e.target.value }))}
                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                            />
                          </div>
                          <div className="col-span-2">
                            <input
                              placeholder="Descrição"
                              value={addLimitForm.description}
                              onChange={(e) => setAddLimitForm((f) => ({ ...f, description: e.target.value }))}
                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                            />
                          </div>
                          <div>
                            <input
                              placeholder="Valor (ex: 5, 100)"
                              value={addLimitForm.limitValue}
                              onChange={(e) => setAddLimitForm((f) => ({ ...f, limitValue: e.target.value }))}
                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                            />
                          </div>
                          <div>
                            <input
                              placeholder="Unidade (ex: MB, op/mês)"
                              value={addLimitForm.unit}
                              onChange={(e) => setAddLimitForm((f) => ({ ...f, unit: e.target.value }))}
                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                            />
                          </div>
                          <div>
                            <input
                              placeholder="Chave técnica (opcional)"
                              value={addLimitForm.limitKey}
                              onChange={(e) => setAddLimitForm((f) => ({ ...f, limitKey: e.target.value }))}
                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                            />
                          </div>
                          <div>
                            <input
                              type="number" placeholder="Ordem"
                              value={addLimitForm.sortOrder}
                              onChange={(e) => setAddLimitForm((f) => ({ ...f, sortOrder: e.target.value }))}
                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                            />
                          </div>
                        </div>
                        <div className="flex gap-2">
                          <button
                            onClick={() => { if (!addLimitForm.title.trim()) return; addLimit.mutate({ pvmId: pvm.id, form: addLimitForm }) }}
                            disabled={!addLimitForm.title.trim() || addLimit.isPending}
                            className="px-3 py-1 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors"
                          >
                            {addLimit.isPending ? 'Salvando…' : 'Salvar'}
                          </button>
                          <button
                            onClick={() => setAddingLimitPvmId(null)}
                            className="px-3 py-1 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors"
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    )}

                    {limits.length === 0 && addingLimitPvmId !== pvm.id ? (
                      <p className="text-xs text-gray-500 py-2">Nenhuma limitação cadastrada.</p>
                    ) : (
                      <table className="min-w-full text-xs">
                        <thead>
                          <tr className="text-gray-500">
                            <th className="text-left py-1 pr-3">Título</th>
                            <th className="text-left py-1 pr-3">Descrição</th>
                            <th className="text-right py-1 pr-3">Valor</th>
                            <th className="text-left py-1 pr-3">Unidade</th>
                            <th className="text-right py-1">Ações</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-700/50">
                          {limits.map((limit) => (
                            editLimitId === limit.id ? (
                              <tr key={limit.id}>
                                <td colSpan={5} className="py-2">
                                  <div className="grid grid-cols-2 gap-2 mb-2">
                                    <div className="col-span-2">
                                      <input
                                        placeholder="Título *"
                                        value={editLimitForm.title}
                                        onChange={(e) => setEditLimitForm((f) => ({ ...f, title: e.target.value }))}
                                        className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                      />
                                    </div>
                                    <div className="col-span-2">
                                      <input
                                        placeholder="Descrição"
                                        value={editLimitForm.description}
                                        onChange={(e) => setEditLimitForm((f) => ({ ...f, description: e.target.value }))}
                                        className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                      />
                                    </div>
                                    <div>
                                      <input
                                        placeholder="Valor"
                                        value={editLimitForm.limitValue}
                                        onChange={(e) => setEditLimitForm((f) => ({ ...f, limitValue: e.target.value }))}
                                        className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                      />
                                    </div>
                                    <div>
                                      <input
                                        placeholder="Unidade"
                                        value={editLimitForm.unit}
                                        onChange={(e) => setEditLimitForm((f) => ({ ...f, unit: e.target.value }))}
                                        className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                      />
                                    </div>
                                    <div>
                                      <input
                                        placeholder="Chave técnica"
                                        value={editLimitForm.limitKey}
                                        onChange={(e) => setEditLimitForm((f) => ({ ...f, limitKey: e.target.value }))}
                                        className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                      />
                                    </div>
                                    <div>
                                      <input
                                        type="number" placeholder="Ordem"
                                        value={editLimitForm.sortOrder}
                                        onChange={(e) => setEditLimitForm((f) => ({ ...f, sortOrder: e.target.value }))}
                                        className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                      />
                                    </div>
                                  </div>
                                  <div className="flex gap-2">
                                    <button
                                      onClick={() => { if (!editLimitForm.title.trim()) return; updateLimit.mutate({ pvmId: pvm.id, limitId: limit.id, form: editLimitForm }) }}
                                      disabled={!editLimitForm.title.trim() || updateLimit.isPending}
                                      className="px-3 py-1 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors"
                                    >
                                      {updateLimit.isPending ? 'Salvando…' : 'Salvar'}
                                    </button>
                                    <button
                                      onClick={() => setEditLimitId(null)}
                                      className="px-3 py-1 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors"
                                    >
                                      Cancelar
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            ) : (
                              <tr key={limit.id} className="hover:bg-gray-700/20">
                                <td className="py-1.5 pr-3 text-white font-medium">{limit.title}</td>
                                <td className="py-1.5 pr-3 text-gray-400 max-w-[200px] truncate">{limit.description ?? '—'}</td>
                                <td className="py-1.5 pr-3 text-gray-300 text-right font-mono">{limit.limit_value ?? '—'}</td>
                                <td className="py-1.5 pr-3 text-gray-400">{limit.unit ?? '—'}</td>
                                <td className="py-1.5 text-right whitespace-nowrap">
                                  <button
                                    onClick={() => startEditLimit(limit)}
                                    className="px-2 py-0.5 rounded bg-gray-700 text-gray-300 hover:bg-gray-600 transition-colors mr-1"
                                  >
                                    Editar
                                  </button>
                                  <button
                                    onClick={() => { if (confirm('Excluir esta limitação?')) removeLimit.mutate({ pvmId: pvm.id, limitId: limit.id }) }}
                                    disabled={removeLimit.isPending}
                                    className="px-2 py-0.5 rounded bg-red-900/50 text-red-300 hover:bg-red-800/50 disabled:opacity-50 transition-colors"
                                  >
                                    Excluir
                                  </button>
                                </td>
                              </tr>
                            )
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>

        {/* Footer — totais */}
        <div className="border-t border-gray-700 px-6 py-3 flex items-center justify-between bg-gray-900/80 rounded-b-2xl shrink-0">
          <span className="text-xs text-gray-500">{modules.filter((m) => m.status === 'active').length} módulo(s) ativo(s)</span>
          <div className="flex items-center gap-6">
            <div className="text-right">
              <p className="text-xs text-gray-500">Total Mensal</p>
              <p className="text-white font-semibold">{brl(totalMonthly)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-gray-500">Total Anual/mês</p>
              <p className="text-white font-semibold">{brl(totalAnnual)}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── página principal ─────────────────────────────────────────────────────────

export function AdminPlansPage() {
  const qc = useQueryClient()

  const [showCreate, setShowCreate]       = useState(false)
  const [newVersionOf, setNewVersionOf]   = useState<Plan | null>(null)
  const [historyPlan, setHistoryPlan]     = useState<{ code: string; name: string } | null>(null)
  const [modulePlan, setModulePlan]       = useState<Plan | null>(null)
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
    mutationFn: (id: string) => api.patch(`/api/v1/admin/plans/${id}/status`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-plans'] }),
  })

  const [popularError, setPopularError] = useState<string | null>(null)
  const setPopular = useMutation({
    mutationFn: (id: string) => api.patch(`/api/v1/admin/plans/${id}/popular`, {}),
    onSuccess: () => { setPopularError(null); qc.invalidateQueries({ queryKey: ['admin-plans'] }) },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setPopularError(msg ?? 'Erro ao definir Mais Popular')
    },
  })

  function handleSaved(planId: string) {
    setShowCreate(false)
    setNewVersionOf(null)
    qc.invalidateQueries({ queryKey: ['admin-plans'] })
    const plan = plans.find((p) => p.id === planId)
    if (plan) setModulePlan(plan)
  }

  const displayed = filterCurrent ? plans.filter((p) => p.is_current_version) : plans

  const versionCount: Record<string, number> = {}
  plans.forEach((p) => { versionCount[p.code] = (versionCount[p.code] ?? 0) + 1 })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Planos</h1>
          <p className="text-sm text-gray-400 mt-0.5">Cadastro, versões, módulos e preços</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors"
        >
          <span>+</span> Novo Plano
        </button>
      </div>

      {popularError && (
        <div className="flex items-center justify-between rounded-lg bg-red-900/30 border border-red-700 px-4 py-2.5 text-sm text-red-300">
          <span>{popularError}</span>
          <button onClick={() => setPopularError(null)} className="ml-4 text-red-400 hover:text-red-200 text-lg leading-none">✕</button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-4 text-xs text-gray-500">
        <span className="flex items-center gap-1.5"><span className="text-yellow-400 text-base">★</span> Mais Popular</span>
        <span className="flex items-center gap-1.5"><span className="inline-block w-3.5 h-3.5 rounded-full bg-green-600" /> Toggle ativo/inativo</span>
        <span>Preços calculados pelos <strong className="text-gray-300">módulos ativos</strong></span>
        <span>Versionamento via <strong className="text-gray-300">"Nova Versão"</strong></span>
      </div>

      <div className="flex items-center gap-3">
        <label className="flex items-center gap-2 cursor-pointer">
          <Toggle checked={filterCurrent} onChange={() => setFilterCurrent((v) => !v)} />
          <span className="text-sm text-gray-300">Apenas versões atuais</span>
        </label>
        <span className="text-xs text-gray-500">({displayed.length} plano{displayed.length !== 1 ? 's' : ''})</span>
      </div>

      {/* Tabela */}
      <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-x-auto">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Carregando…</div>
        ) : displayed.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">Nenhum plano cadastrado.</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-700 text-sm">
            <thead>
              <tr className="bg-gray-800/80">
                {['Pop.', 'Plano', 'Código', 'Tipo', 'Versão', 'Módulos', 'Total Mensal', 'Total Anual', 'Cobrança', 'Assin.', 'Status', 'Ações'].map((h) => (
                  <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/60">
              {displayed.map((plan) => (
                <tr key={plan.id} className={`transition-colors ${plan.is_most_popular ? 'bg-yellow-900/10 hover:bg-yellow-900/20' : 'hover:bg-gray-700/30'}`}>

                  <td className="px-3 py-3 text-center">
                    <StarButton
                      active={plan.is_most_popular}
                      disabled={!plan.is_active || !plan.is_current_version || setPopular.isPending}
                      onClick={() => setPopular.mutate(plan.id)}
                    />
                  </td>

                  <td className="px-3 py-3 text-white font-medium whitespace-nowrap">
                    <div className="flex items-center gap-1.5">
                      {plan.name}
                      {plan.is_most_popular && <Badge label="Mais Popular" variant="yellow" />}
                    </div>
                  </td>

                  <td className="px-3 py-3 font-mono text-gray-400 text-xs">{plan.code}</td>

                  <td className="px-3 py-3">
                    <Badge
                      label={plan.plan_type === 'individual' ? 'Individual' : 'Empresarial'}
                      variant={plan.plan_type === 'individual' ? 'blue' : 'green'}
                    />
                  </td>

                  <td className="px-3 py-3">
                    <div className="flex items-center gap-1.5">
                      <span className="text-gray-300">v{plan.version}</span>
                      {plan.is_current_version && <Badge label="atual" variant="blue" />}
                    </div>
                  </td>

                  <td className="px-3 py-3 text-center">
                    {(plan.module_count ?? 0) > 0 ? (
                      <span className="inline-flex items-center justify-center rounded-full bg-indigo-900/50 border border-indigo-700 text-indigo-300 text-xs font-medium px-2 py-0.5">
                        {plan.module_count}
                      </span>
                    ) : (
                      <span className="text-gray-600 text-xs">—</span>
                    )}
                  </td>

                  <td className="px-3 py-3 text-gray-300 whitespace-nowrap font-medium">
                    {brl(plan.total_monthly_price ?? plan.price_monthly)}
                    {(plan.module_count ?? 0) > 0 && (
                      <span className="block text-xs text-indigo-400 font-normal">por módulos</span>
                    )}
                  </td>

                  <td className="px-3 py-3 text-gray-300 whitespace-nowrap">
                    {brl(plan.total_annual_price ?? plan.price_annual)}
                  </td>

                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap">
                    {BILLING_LABELS[plan.billing_type] ?? plan.billing_type}
                  </td>

                  <td className="px-3 py-3">
                    <span className="text-gray-300 font-semibold">{plan.subscriber_count ?? 0}</span>
                    {versionCount[plan.code] > 1 && (
                      <span className="text-xs text-gray-600 ml-1">({versionCount[plan.code]}v)</span>
                    )}
                  </td>

                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <Toggle
                        checked={plan.is_active}
                        onChange={() => toggleStatus.mutate(plan.id)}
                        disabled={toggleStatus.isPending}
                      />
                      <Badge label={plan.is_active ? 'Ativo' : 'Inativo'} variant={plan.is_active ? 'green' : 'gray'} />
                    </div>
                  </td>

                  <td className="px-3 py-3">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <button
                        onClick={() => setModulePlan(plan)}
                        className="px-2.5 py-1 rounded-md bg-indigo-900/60 text-indigo-300 text-xs hover:bg-indigo-800/60 transition-colors whitespace-nowrap"
                      >
                        Módulos
                      </button>
                      {plan.is_current_version && (
                        <button
                          onClick={() => setNewVersionOf(plan)}
                          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors whitespace-nowrap"
                        >
                          Nova versão
                        </button>
                      )}
                      {versionCount[plan.code] > 1 && (
                        <button
                          onClick={() => setHistoryPlan({ code: plan.code, name: plan.name })}
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

      {/* Modais */}
      {showCreate && (
        <PlanForm mode="create" onClose={() => setShowCreate(false)} onSaved={handleSaved} />
      )}

      {newVersionOf && (
        <PlanForm
          mode="new-version"
          sourcePlan={newVersionOf}
          onClose={() => setNewVersionOf(null)}
          onSaved={handleSaved}
        />
      )}

      {historyPlan && (
        <VersionHistoryModal
          planCode={historyPlan.code}
          planName={historyPlan.name}
          onClose={() => setHistoryPlan(null)}
        />
      )}

      {modulePlan && (
        <PlanModulesModal
          plan={modulePlan}
          onClose={() => setModulePlan(null)}
        />
      )}
    </div>
  )
}
