import { useState, useEffect, useId } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
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
  }
}

const EMPTY_FORM: FormData = {
  name: '', code: '', description: '',
  max_users: '5', max_ai_requests_month: '100',
  billing_type: 'both', plan_type: 'business', sort_order: '99',
}

// Tipos para estado local dos módulos no modal de edição
interface LocalLimit {
  tempId: string
  title: string
  description: string
  limitKey: string
  limitValue: string
  unit: string
  sortOrder: string
}

interface LocalModule {
  tempId: string
  moduleId: string
  moduleName: string
  monthlyPrice: string
  annualMonthlyPrice: string
  status: 'active' | 'inactive'
  sortOrder: string
  limits: LocalLimit[]
}

function pvmToLocalModule(pvm: PlanVersionModule): LocalModule {
  const limits = parseLimits(pvm.limits_json)
  return {
    tempId: pvm.id,
    moduleId: pvm.module_id,
    moduleName: pvm.module_name,
    monthlyPrice: String(pvm.monthly_price ?? 0),
    annualMonthlyPrice: String(pvm.annual_monthly_price ?? 0),
    status: pvm.status as 'active' | 'inactive',
    sortOrder: String(pvm.sort_order ?? 99),
    limits: limits.map((l) => ({
      tempId: l.id,
      title: l.title,
      description: l.description ?? '',
      limitKey: l.limit_key ?? '',
      limitValue: l.limit_value ?? '',
      unit: l.unit ?? '',
      sortOrder: String(l.sort_order ?? 99),
    })),
  }
}

const EMPTY_LOCAL_LIMIT: LocalLimit = {
  tempId: '', title: '', description: '', limitKey: '', limitValue: '', unit: '', sortOrder: '99',
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

// ─── formulário de criação de plano (apenas criar) ────────────────────────────

interface PlanCreateFormProps {
  onClose: () => void
  onSaved: () => void
}

function PlanCreateForm({ onClose, onSaved }: PlanCreateFormProps) {
  const [form, setForm] = useState<FormData>({ ...EMPTY_FORM })
  const [saving, setSaving] = useState(false)
  const [error, setError]   = useState<string | null>(null)

  function field(key: keyof FormData, value: string) {
    setForm((f) => ({ ...f, [key]: value }))
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
      }
      await api.post('/api/v1/admin/plans', payload)
      onSaved()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao criar plano')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[92vh] overflow-y-auto">

        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">Novo Plano</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <div className="mx-6 mt-4 rounded-lg bg-indigo-900/20 border border-indigo-700 px-4 py-3 text-sm text-indigo-200">
          Após criar o plano, clique em <strong>"Editar"</strong> para configurar os módulos e preços.
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-5">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input
                required value={form.name}
                onChange={(e) => field('name', e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: Starter"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Código *</label>
              <input
                required value={form.code}
                onChange={(e) => field('code', e.target.value.toLowerCase().replace(/\s+/g, '_'))}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                placeholder="Ex: starter"
              />
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
                <label className="block text-xs font-medium text-gray-400 mb-1">Tipos de cobrança</label>
                <select value={form.billing_type}
                  onChange={(e) => field('billing_type', e.target.value as FormData['billing_type'])}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500">
                  <option value="both">Mensal e Anual</option>
                  <option value="monthly">Apenas Mensal</option>
                  <option value="annual">Apenas Anual</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Tipo de plano *</label>
                <select value={form.plan_type}
                  onChange={(e) => field('plan_type', e.target.value as FormData['plan_type'])}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500">
                  <option value="business">Empresarial</option>
                  <option value="individual">Individual</option>
                </select>
              </div>
            </div>
          </div>

          <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Limites globais</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Máx. usuários (-1 = ilimitado)</label>
                <input type="number" min="-1" value={form.max_users}
                  onChange={(e) => field('max_users', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Máx. IA/mês (-1 = ilimitado)</label>
                <input type="number" min="-1" value={form.max_ai_requests_month}
                  onChange={(e) => field('max_ai_requests_month', e.target.value)}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500" />
              </div>
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Ordem de exibição</label>
            <input type="number" min="0" value={form.sort_order}
              onChange={(e) => field('sort_order', e.target.value)}
              className="w-32 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500" />
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
              className="px-5 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 disabled:opacity-60 transition-colors">
              {saving ? 'Criando…' : 'Criar plano'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── tipos internos do histórico de versões ──────────────────────────────────

interface HistoryModule {
  id: string
  module_id: string
  module_name: string
  module_slug: string
  module_icon_path: string | null
  monthly_price: number
  annual_monthly_price: number
  status: 'active' | 'inactive'
  sort_order: number
  limits: PlanVersionModuleLimit[]
}

function parseHistoryModules(modulesJson: string | undefined): HistoryModule[] {
  if (!modulesJson) return []
  try { return JSON.parse(modulesJson) ?? [] } catch { return [] }
}

function buildVersionChanges(current: Plan, prev: Plan): string[] {
  const currMods = parseHistoryModules(current.modules_json)
  const prevMods = parseHistoryModules(prev.modules_json)
  const changes: string[] = []

  for (const mod of currMods) {
    const old = prevMods.find(p => p.module_id === mod.module_id)
    if (!old) {
      changes.push(`Módulo adicionado: ${mod.module_name}`)
    } else {
      if (Number(old.monthly_price) !== Number(mod.monthly_price))
        changes.push(`${mod.module_name} — mensal: ${brl(old.monthly_price)} → ${brl(mod.monthly_price)}`)
      if (Number(old.annual_monthly_price) !== Number(mod.annual_monthly_price))
        changes.push(`${mod.module_name} — anual/mês: ${brl(old.annual_monthly_price)} → ${brl(mod.annual_monthly_price)}`)
      if (old.status !== mod.status)
        changes.push(`${mod.module_name} — status: ${old.status} → ${mod.status}`)
    }
  }

  for (const mod of prevMods) {
    if (!currMods.find(c => c.module_id === mod.module_id))
      changes.push(`Módulo removido: ${mod.module_name}`)
  }

  return changes
}

// ─── card de módulo no histórico ──────────────────────────────────────────────

function HistoryModuleCard({ mod }: { mod: HistoryModule }) {
  const annualTotal = Number(mod.annual_monthly_price) * 12
  const isActive = mod.status === 'active'

  return (
    <div className={`rounded-lg border p-3 ${isActive ? 'border-gray-600 bg-gray-800/60' : 'border-gray-700/50 bg-gray-800/20 opacity-60'}`}>
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          {mod.module_icon_path && (
            <img src={mod.module_icon_path} alt="" className="w-4 h-4 object-contain opacity-80" />
          )}
          <span className="text-sm font-medium text-white">{mod.module_name}</span>
        </div>
        <Badge label={isActive ? 'Ativo' : 'Inativo'} variant={isActive ? 'green' : 'gray'} />
      </div>

      <div className="grid grid-cols-2 gap-2 text-xs">
        <div>
          <span className="text-gray-500">Mensal</span>
          <p className="text-gray-200 font-medium">{brl(mod.monthly_price)}</p>
        </div>
        <div>
          <span className="text-gray-500">Anual</span>
          <p className="text-gray-200 font-medium">{brl(annualTotal)}/ano</p>
          {Number(mod.annual_monthly_price) > 0 && (
            <p className="text-gray-500">em 12x de {brl(mod.annual_monthly_price)}</p>
          )}
        </div>
      </div>

      {mod.limits && mod.limits.length > 0 && (
        <div className="mt-2 pt-2 border-t border-gray-700/60">
          <p className="text-xs text-gray-500 mb-1">Limitações:</p>
          <ul className="space-y-0.5">
            {mod.limits.map((l) => (
              <li key={l.id} className="text-xs text-gray-400">
                {'• '}
                {l.title}
                {l.limit_value ? `: ${l.limit_value}${l.unit ? ` ${l.unit}` : ''}` : ''}
                {l.description ? <span className="text-gray-500"> — {l.description}</span> : null}
              </li>
            ))}
          </ul>
        </div>
      )}
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

  const [expandedId, setExpandedId] = useState<string | null>(null)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-2xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[85vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <div>
            <h2 className="text-lg font-semibold text-white">Histórico de versões</h2>
            <p className="text-xs text-gray-400 mt-0.5">{planName}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <div className="p-6 space-y-4">
          {isLoading ? (
            <p className="text-sm text-gray-400">Carregando…</p>
          ) : versions.map((v, idx) => {
            const modules = parseHistoryModules(v.modules_json)
            const prevVersion = versions[idx + 1]
            const changes = prevVersion ? buildVersionChanges(v, prevVersion) : []
            const isExpanded = expandedId === v.id
            const activeCount = modules.filter(m => m.status === 'active').length

            return (
              <div key={v.id}
                className={`rounded-xl border p-4 ${v.is_current_version ? 'border-blue-600 bg-blue-900/10' : 'border-gray-700 bg-gray-800/40'}`}>

                {/* cabeçalho da versão */}
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-white font-semibold">v{v.version} — {v.name}</span>
                    {v.is_current_version && <Badge label="versão atual" variant="blue" />}
                    {v.is_most_popular && <Badge label="★ mais popular" variant="yellow" />}
                    <Badge label={v.is_active ? 'Ativo' : 'Inativo'} variant={v.is_active ? 'green' : 'gray'} />
                  </div>
                  <div className="text-right text-xs text-gray-400 shrink-0">
                    <p>{v.created_at ? new Date(v.created_at).toLocaleDateString('pt-BR') : '—'}</p>
                    <p>{v.subscriber_count ?? 0} assinante(s)</p>
                  </div>
                </div>

                {/* totais */}
                <div className="grid grid-cols-2 gap-3 text-sm mb-3">
                  <div>
                    <span className="text-gray-500 text-xs">Total Mensal</span>
                    <p className="text-white font-medium">{brl(v.total_monthly_price)}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-xs">Total Anual</span>
                    <p className="text-white font-medium">{brl(v.total_annual_price)}/ano</p>
                    {(v.total_annual_monthly_price ?? 0) > 0 && (
                      <p className="text-xs text-gray-500">em 12x de {brl(v.total_annual_monthly_price)}</p>
                    )}
                  </div>
                </div>

                {/* alterações em relação à versão anterior */}
                {changes.length > 0 && (
                  <div className="mb-3 rounded-lg bg-amber-900/20 border border-amber-800/40 p-3">
                    <p className="text-xs font-medium text-amber-400 mb-1.5">Alterações em relação à v{prevVersion!.version}:</p>
                    <ul className="space-y-0.5">
                      {changes.map((c, i) => (
                        <li key={i} className="text-xs text-amber-300/80">• {c}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* módulos */}
                {modules.length > 0 ? (
                  <div>
                    <button
                      type="button"
                      onClick={() => setExpandedId(isExpanded ? null : v.id)}
                      className="flex items-center gap-1.5 text-xs text-indigo-400 hover:text-indigo-300 transition-colors"
                    >
                      <span className="text-[10px]">{isExpanded ? '▲' : '▼'}</span>
                      {isExpanded ? 'Ocultar' : 'Ver'} módulos desta versão
                      <span className="text-gray-500">({activeCount} ativo{activeCount !== 1 ? 's' : ''}{modules.length > activeCount ? `, ${modules.length - activeCount} inativo${modules.length - activeCount !== 1 ? 's' : ''}` : ''})</span>
                    </button>

                    {isExpanded && (
                      <div className="mt-3 space-y-2">
                        {modules.map((mod) => (
                          <HistoryModuleCard key={mod.id} mod={mod} />
                        ))}
                      </div>
                    )}
                  </div>
                ) : (
                  <p className="text-xs text-amber-500/70">Nenhum módulo nesta versão</p>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// ─── modal de edição unificada (dados + módulos + limitações → nova versão) ───

interface EditPlanModalProps {
  plan: Plan
  onClose: () => void
  onSaved: () => void
}

function EditPlanModal({ plan, onClose, onSaved }: EditPlanModalProps) {
  const uid = useId()
  const [form, setForm] = useState<FormData>(planToForm(plan))
  const [localModules, setLocalModules] = useState<LocalModule[]>([])
  const [modulesReady, setModulesReady] = useState(false)
  const [saving, setSaving]             = useState(false)
  const [error, setError]               = useState<string | null>(null)

  // Adicionar módulo — estado do formulário de adição
  const [showAddModule, setShowAddModule] = useState(false)
  const [addModuleId, setAddModuleId]     = useState('')

  // Expandir limitações por tempId do módulo
  const [expandedId, setExpandedId] = useState<string | null>(null)
  // Editar módulo inline
  const [editingId, setEditingId]   = useState<string | null>(null)
  const [editModuleForm, setEditModuleForm] = useState<Omit<LocalModule, 'tempId' | 'moduleId' | 'moduleName' | 'limits'>>({
    monthlyPrice: '0', annualMonthlyPrice: '0', status: 'active', sortOrder: '99',
  })

  // Adicionar limitação — por tempId do módulo
  const [addingLimitFor, setAddingLimitFor]   = useState<string | null>(null)
  const [addLimitForm, setAddLimitForm]       = useState<LocalLimit>({ ...EMPTY_LOCAL_LIMIT })
  // Editar limitação inline
  const [editingLimitId, setEditingLimitId]   = useState<string | null>(null)
  const [editLimitForm, setEditLimitForm]     = useState<LocalLimit>({ ...EMPTY_LOCAL_LIMIT })

  // Busca módulos atuais da versão
  const { data: fetchedModules, isLoading: loadingModules } = useQuery({
    queryKey: ['plan-modules', plan.id],
    queryFn: async () => {
      const { data } = await api.get<PlanVersionModule[]>(`/api/v1/admin/plans/${plan.id}/modules`)
      return data
    },
    staleTime: 0,
  })

  useEffect(() => {
    if (fetchedModules && !modulesReady) {
      setLocalModules(fetchedModules.map(pvmToLocalModule))
      setModulesReady(true)
    }
  }, [fetchedModules, modulesReady])

  // Busca todos os módulos da plataforma disponíveis
  const { data: allPlatformModules = [] } = useQuery({
    queryKey: ['platform-modules-active'],
    queryFn: async () => {
      const { data } = await api.get<PlatformModule[]>('/api/v1/admin/modules?is_active=true')
      return data
    },
  })

  const usedModuleIds = new Set(localModules.map((m) => m.moduleId))
  const availableToAdd = allPlatformModules.filter((m) => !usedModuleIds.has(m.id))

  const nextVersion = plan.version + 1

  function field(key: keyof FormData, value: string) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  // ── operações de módulos (apenas local state) ──────────────────────────

  function handleAddModule() {
    if (!addModuleId) return
    const pm = allPlatformModules.find((m) => m.id === addModuleId)
    if (!pm) return
    const newMod: LocalModule = {
      tempId: `new-${Date.now()}`,
      moduleId: pm.id,
      moduleName: pm.name,
      monthlyPrice: '0',
      annualMonthlyPrice: '0',
      status: 'active',
      sortOrder: '99',
      limits: [],
    }
    setLocalModules((prev) => [...prev, newMod])
    setAddModuleId('')
    setShowAddModule(false)
  }

  function handleRemoveModule(tempId: string) {
    setLocalModules((prev) => prev.filter((m) => m.tempId !== tempId))
    if (expandedId === tempId) setExpandedId(null)
    if (editingId === tempId) setEditingId(null)
  }

  function startEditModule(mod: LocalModule) {
    setEditingId(mod.tempId)
    setEditModuleForm({
      monthlyPrice: mod.monthlyPrice,
      annualMonthlyPrice: mod.annualMonthlyPrice,
      status: mod.status,
      sortOrder: mod.sortOrder,
    })
  }

  function saveEditModule(tempId: string) {
    setLocalModules((prev) =>
      prev.map((m) => m.tempId === tempId ? { ...m, ...editModuleForm } : m)
    )
    setEditingId(null)
  }

  function toggleModuleStatus(tempId: string) {
    setLocalModules((prev) =>
      prev.map((m) => m.tempId === tempId
        ? { ...m, status: m.status === 'active' ? 'inactive' : 'active' }
        : m
      )
    )
  }

  // ── operações de limitações (apenas local state) ───────────────────────

  function handleAddLimit(moduleTempId: string) {
    if (!addLimitForm.title.trim()) return
    const newLimit: LocalLimit = {
      ...addLimitForm,
      tempId: `new-limit-${Date.now()}`,
    }
    setLocalModules((prev) =>
      prev.map((m) => m.tempId === moduleTempId
        ? { ...m, limits: [...m.limits, newLimit] }
        : m
      )
    )
    setAddingLimitFor(null)
    setAddLimitForm({ ...EMPTY_LOCAL_LIMIT })
  }

  function handleRemoveLimit(moduleTempId: string, limitTempId: string) {
    setLocalModules((prev) =>
      prev.map((m) => m.tempId === moduleTempId
        ? { ...m, limits: m.limits.filter((l) => l.tempId !== limitTempId) }
        : m
      )
    )
  }

  function startEditLimit(limit: LocalLimit) {
    setEditingLimitId(limit.tempId)
    setEditLimitForm({ ...limit })
  }

  function saveEditLimit(moduleTempId: string) {
    setLocalModules((prev) =>
      prev.map((m) => m.tempId === moduleTempId
        ? { ...m, limits: m.limits.map((l) => l.tempId === editingLimitId ? { ...editLimitForm } : l) }
        : m
      )
    )
    setEditingLimitId(null)
  }

  // ── totais calculados ──────────────────────────────────────────────────

  const activeModules = localModules.filter((m) => m.status === 'active')
  const totalMonthly       = activeModules.reduce((s, m) => s + (parseFloat(m.monthlyPrice) || 0), 0)
  const totalAnnualMonthly = activeModules.reduce((s, m) => s + (parseFloat(m.annualMonthlyPrice) || 0), 0)
  const totalAnnual        = totalAnnualMonthly * 12

  // ── salvar: cria nova versão com tudo ─────────────────────────────────

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        name: form.name,
        description: form.description || null,
        max_users: parseInt(form.max_users),
        max_ai_requests_month: parseInt(form.max_ai_requests_month),
        billing_type: form.billing_type,
        sort_order: parseInt(form.sort_order) || 99,
        modules: localModules.map((m) => ({
          module_id: m.moduleId,
          monthly_price: parseFloat(m.monthlyPrice) || 0,
          annual_monthly_price: parseFloat(m.annualMonthlyPrice) || 0,
          status: m.status,
          sort_order: parseInt(m.sortOrder) || 99,
          limits: m.limits.map((l) => ({
            title: l.title,
            description: l.description || null,
            limit_key: l.limitKey || null,
            limit_value: l.limitValue || null,
            unit: l.unit || null,
            sort_order: parseInt(l.sortOrder) || 99,
          })),
        })),
      }
      await api.post(`/api/v1/admin/plans/${plan.id}/edit`, payload)
      onSaved()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar plano')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-3xl bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[94vh] flex flex-col">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700 shrink-0">
          <div>
            <h2 className="text-lg font-semibold text-white">Editar plano</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {plan.name} · v{plan.version} →{' '}
              <span className="text-blue-400 font-semibold">v{nextVersion}</span>
              <span className="ml-2 text-gray-500">(nova versão será criada ao salvar)</span>
            </p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        {/* Aviso de versionamento */}
        <div className="mx-6 mt-4 shrink-0 rounded-lg bg-blue-900/30 border border-blue-700 px-4 py-3 text-sm text-blue-200">
          A versão <strong>v{plan.version}</strong> será preservada intacta. Todas as alterações serão aplicadas na nova <strong>v{nextVersion}</strong>.
        </div>

        {/* Corpo com scroll */}
        <form id={uid} onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-6 py-4 space-y-6">

          {/* ── Seção: Dados gerais ── */}
          <div>
            <h3 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
              <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-blue-600 text-white text-xs">1</span>
              Dados gerais
            </h3>

            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
                  <input required value={form.name}
                    onChange={(e) => field('name', e.target.value)}
                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Código</label>
                  <input value={form.code} disabled
                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono opacity-50 cursor-not-allowed"
                  />
                  <p className="mt-1 text-xs text-gray-500">Código imutável — vinculado ao grupo</p>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
                <textarea value={form.description}
                  onChange={(e) => field('description', e.target.value)}
                  rows={2}
                  className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 resize-none"
                />
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Cobrança</label>
                  <select value={form.billing_type}
                    onChange={(e) => field('billing_type', e.target.value as FormData['billing_type'])}
                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500">
                    <option value="both">Mensal e Anual</option>
                    <option value="monthly">Apenas Mensal</option>
                    <option value="annual">Apenas Anual</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Tipo de plano</label>
                  <input value={plan.plan_type === 'individual' ? 'Individual' : 'Empresarial'} disabled
                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white opacity-50 cursor-not-allowed"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Ordem</label>
                  <input type="number" min="0" value={form.sort_order}
                    onChange={(e) => field('sort_order', e.target.value)}
                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div className="rounded-lg bg-gray-800/50 border border-gray-700 p-4">
                <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">Limites globais</h4>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">Máx. usuários (-1 = ilimitado)</label>
                    <input type="number" min="-1" value={form.max_users}
                      onChange={(e) => field('max_users', e.target.value)}
                      className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">Máx. IA/mês (-1 = ilimitado)</label>
                    <input type="number" min="-1" value={form.max_ai_requests_month}
                      onChange={(e) => field('max_ai_requests_month', e.target.value)}
                      className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                    />
                  </div>
                </div>
              </div>

            </div>
          </div>

          {/* ── Seção: Módulos ── */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-white flex items-center gap-2">
                <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-indigo-600 text-white text-xs">2</span>
                Módulos do plano
              </h3>
              {!showAddModule && availableToAdd.length > 0 && (
                <button type="button"
                  onClick={() => setShowAddModule(true)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-500 transition-colors">
                  + Adicionar módulo
                </button>
              )}
            </div>

            {/* Formulário de adição de módulo */}
            {showAddModule && (
              <div className="mb-3 rounded-xl bg-gray-800 border border-indigo-700 p-4">
                <h4 className="text-sm font-semibold text-indigo-300 mb-3">Adicionar módulo</h4>
                <div className="flex gap-3 items-end">
                  <div className="flex-1">
                    <label className="block text-xs text-gray-400 mb-1">Módulo *</label>
                    <select value={addModuleId} onChange={(e) => setAddModuleId(e.target.value)}
                      className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500">
                      <option value="">Selecione…</option>
                      {availableToAdd.map((m) => (
                        <option key={m.id} value={m.id}>{m.name}</option>
                      ))}
                    </select>
                  </div>
                  <button type="button"
                    onClick={handleAddModule}
                    disabled={!addModuleId}
                    className="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm hover:bg-indigo-500 disabled:opacity-50 transition-colors">
                    Adicionar
                  </button>
                  <button type="button"
                    onClick={() => { setShowAddModule(false); setAddModuleId('') }}
                    className="px-4 py-2 rounded-lg bg-gray-700 text-gray-300 text-sm hover:bg-gray-600 transition-colors">
                    Cancelar
                  </button>
                </div>
              </div>
            )}

            {loadingModules && !modulesReady ? (
              <p className="text-sm text-gray-400 py-4 text-center">Carregando módulos…</p>
            ) : localModules.length === 0 ? (
              <div className="py-6 text-center text-sm text-gray-400 rounded-xl border border-dashed border-gray-700">
                <p>Nenhum módulo adicionado.</p>
                <p className="text-xs text-gray-500 mt-1">Clique em "Adicionar módulo" para começar.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {localModules.map((mod) => {
                  const isEditing  = editingId === mod.tempId
                  const isExpanded = expandedId === mod.tempId
                  return (
                    <div key={mod.tempId}
                      className={`rounded-xl border ${mod.status === 'active' ? 'border-gray-600 bg-gray-800/50' : 'border-gray-700 bg-gray-800/20 opacity-70'}`}>

                      {isEditing ? (
                        <div className="p-4 space-y-3">
                          <p className="text-sm font-medium text-white">{mod.moduleName}</p>
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
                                value={editModuleForm.annualMonthlyPrice}
                                onChange={(e) => setEditModuleForm((f) => ({ ...f, annualMonthlyPrice: e.target.value }))}
                                className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                              />
                            </div>
                            <div>
                              <label className="block text-xs text-gray-400 mb-1">Status</label>
                              <select value={editModuleForm.status}
                                onChange={(e) => setEditModuleForm((f) => ({ ...f, status: e.target.value as 'active' | 'inactive' }))}
                                className="w-full rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500">
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
                            <button type="button" onClick={() => saveEditModule(mod.tempId)}
                              className="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 transition-colors">
                              Confirmar
                            </button>
                            <button type="button" onClick={() => setEditingId(null)}
                              className="px-3 py-1.5 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                              Cancelar
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center justify-between px-4 py-3">
                          <div className="flex items-center gap-2 min-w-0">
                            <Toggle
                              checked={mod.status === 'active'}
                              onChange={() => toggleModuleStatus(mod.tempId)}
                            />
                            <span className="text-white font-medium text-sm truncate">{mod.moduleName}</span>
                            <Badge label={mod.status === 'active' ? 'Ativo' : 'Inativo'} variant={mod.status === 'active' ? 'green' : 'gray'} />
                          </div>
                          <div className="flex items-center gap-4 ml-4 shrink-0">
                            <div className="text-right">
                              <p className="text-xs text-gray-500">Mensal</p>
                              <p className="text-sm text-white font-medium">{brl(parseFloat(mod.monthlyPrice))}</p>
                            </div>
                            <div className="text-right">
                              <p className="text-xs text-gray-500">Anual/mês</p>
                              <p className="text-sm text-white font-medium">{brl(parseFloat(mod.annualMonthlyPrice))}</p>
                            </div>
                            <div className="flex items-center gap-1.5">
                              <button type="button"
                                onClick={() => setExpandedId(isExpanded ? null : mod.tempId)}
                                className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors whitespace-nowrap">
                                {isExpanded ? '▲' : '▼'} Limitações {mod.limits.length > 0 && `(${mod.limits.length})`}
                              </button>
                              <button type="button" onClick={() => startEditModule(mod)}
                                className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                                Editar
                              </button>
                              <button type="button"
                                onClick={() => { if (confirm(`Remover "${mod.moduleName}" deste plano?`)) handleRemoveModule(mod.tempId) }}
                                className="px-2.5 py-1 rounded-md bg-red-900/60 text-red-300 text-xs hover:bg-red-800/60 transition-colors">
                                Remover
                              </button>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Seção de limitações */}
                      {isExpanded && !isEditing && (
                        <div className="border-t border-gray-700 px-4 py-3">
                          <div className="flex items-center justify-between mb-2">
                            <h5 className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Limitações</h5>
                            {addingLimitFor !== mod.tempId && (
                              <button type="button"
                                onClick={() => { setAddingLimitFor(mod.tempId); setAddLimitForm({ ...EMPTY_LOCAL_LIMIT }) }}
                                className="px-2 py-1 rounded-md bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                                + Adicionar
                              </button>
                            )}
                          </div>

                          {/* Formulário adicionar limitação */}
                          {addingLimitFor === mod.tempId && (
                            <div className="mb-3 rounded-lg bg-gray-700/50 border border-gray-600 p-3">
                              <div className="grid grid-cols-2 gap-2 mb-2">
                                <div className="col-span-2">
                                  <input placeholder="Título *" value={addLimitForm.title}
                                    onChange={(e) => setAddLimitForm((f) => ({ ...f, title: e.target.value }))}
                                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                  />
                                </div>
                                <div className="col-span-2">
                                  <input placeholder="Descrição" value={addLimitForm.description}
                                    onChange={(e) => setAddLimitForm((f) => ({ ...f, description: e.target.value }))}
                                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                  />
                                </div>
                                <div>
                                  <input placeholder="Valor (ex: 5, 100)" value={addLimitForm.limitValue}
                                    onChange={(e) => setAddLimitForm((f) => ({ ...f, limitValue: e.target.value }))}
                                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                  />
                                </div>
                                <div>
                                  <input placeholder="Unidade (ex: MB, op/mês)" value={addLimitForm.unit}
                                    onChange={(e) => setAddLimitForm((f) => ({ ...f, unit: e.target.value }))}
                                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                  />
                                </div>
                                <div>
                                  <input placeholder="Chave técnica (opcional)" value={addLimitForm.limitKey}
                                    onChange={(e) => setAddLimitForm((f) => ({ ...f, limitKey: e.target.value }))}
                                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                  />
                                </div>
                                <div>
                                  <input type="number" placeholder="Ordem" value={addLimitForm.sortOrder}
                                    onChange={(e) => setAddLimitForm((f) => ({ ...f, sortOrder: e.target.value }))}
                                    className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                  />
                                </div>
                              </div>
                              <div className="flex gap-2">
                                <button type="button"
                                  onClick={() => handleAddLimit(mod.tempId)}
                                  disabled={!addLimitForm.title.trim()}
                                  className="px-3 py-1 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors">
                                  Salvar
                                </button>
                                <button type="button" onClick={() => setAddingLimitFor(null)}
                                  className="px-3 py-1 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                                  Cancelar
                                </button>
                              </div>
                            </div>
                          )}

                          {mod.limits.length === 0 && addingLimitFor !== mod.tempId ? (
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
                                {mod.limits.map((limit) => (
                                  editingLimitId === limit.tempId ? (
                                    <tr key={limit.tempId}>
                                      <td colSpan={5} className="py-2">
                                        <div className="grid grid-cols-2 gap-2 mb-2">
                                          <div className="col-span-2">
                                            <input placeholder="Título *" value={editLimitForm.title}
                                              onChange={(e) => setEditLimitForm((f) => ({ ...f, title: e.target.value }))}
                                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                            />
                                          </div>
                                          <div className="col-span-2">
                                            <input placeholder="Descrição" value={editLimitForm.description}
                                              onChange={(e) => setEditLimitForm((f) => ({ ...f, description: e.target.value }))}
                                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                            />
                                          </div>
                                          <div>
                                            <input placeholder="Valor" value={editLimitForm.limitValue}
                                              onChange={(e) => setEditLimitForm((f) => ({ ...f, limitValue: e.target.value }))}
                                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                            />
                                          </div>
                                          <div>
                                            <input placeholder="Unidade" value={editLimitForm.unit}
                                              onChange={(e) => setEditLimitForm((f) => ({ ...f, unit: e.target.value }))}
                                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                            />
                                          </div>
                                          <div>
                                            <input placeholder="Chave técnica" value={editLimitForm.limitKey}
                                              onChange={(e) => setEditLimitForm((f) => ({ ...f, limitKey: e.target.value }))}
                                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                            />
                                          </div>
                                          <div>
                                            <input type="number" placeholder="Ordem" value={editLimitForm.sortOrder}
                                              onChange={(e) => setEditLimitForm((f) => ({ ...f, sortOrder: e.target.value }))}
                                              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500"
                                            />
                                          </div>
                                        </div>
                                        <div className="flex gap-2">
                                          <button type="button"
                                            onClick={() => saveEditLimit(mod.tempId)}
                                            disabled={!editLimitForm.title.trim()}
                                            className="px-3 py-1 rounded-lg bg-blue-600 text-white text-xs hover:bg-blue-500 disabled:opacity-50 transition-colors">
                                            Salvar
                                          </button>
                                          <button type="button" onClick={() => setEditingLimitId(null)}
                                            className="px-3 py-1 rounded-lg bg-gray-700 text-gray-300 text-xs hover:bg-gray-600 transition-colors">
                                            Cancelar
                                          </button>
                                        </div>
                                      </td>
                                    </tr>
                                  ) : (
                                    <tr key={limit.tempId} className="hover:bg-gray-700/20">
                                      <td className="py-1.5 pr-3 text-white font-medium">{limit.title}</td>
                                      <td className="py-1.5 pr-3 text-gray-400 max-w-[200px] truncate">{limit.description || '—'}</td>
                                      <td className="py-1.5 pr-3 text-gray-300 text-right font-mono">{limit.limitValue || '—'}</td>
                                      <td className="py-1.5 pr-3 text-gray-400">{limit.unit || '—'}</td>
                                      <td className="py-1.5 text-right whitespace-nowrap">
                                        <button type="button" onClick={() => startEditLimit(limit)}
                                          className="px-2 py-0.5 rounded bg-gray-700 text-gray-300 hover:bg-gray-600 transition-colors mr-1">
                                          Editar
                                        </button>
                                        <button type="button"
                                          onClick={() => { if (confirm('Excluir esta limitação?')) handleRemoveLimit(mod.tempId, limit.tempId) }}
                                          className="px-2 py-0.5 rounded bg-red-900/50 text-red-300 hover:bg-red-800/50 transition-colors">
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
            )}

            {/* Totais calculados pelos módulos ativos */}
            {localModules.length > 0 && (
              <div className="mt-3 flex items-center justify-between rounded-lg bg-gray-800/60 border border-gray-700 px-4 py-3">
                <span className="text-xs text-gray-500">{activeModules.length} módulo(s) ativo(s)</span>
                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <p className="text-xs text-gray-500">Total Mensal</p>
                    <p className="text-white font-semibold">{brl(totalMonthly)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-gray-500">Anual/mês</p>
                    <p className="text-white font-semibold">{brl(totalAnnualMonthly)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-gray-500">Total Anual</p>
                    <p className="text-white font-semibold">{brl(totalAnnual)}</p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}
        </form>

        {/* Footer fixo */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-gray-700 bg-gray-900/80 shrink-0">
          <p className="text-xs text-gray-500">
            Ao salvar, a versão <strong className="text-gray-300">v{plan.version}</strong> será preservada e a{' '}
            <strong className="text-blue-400">v{nextVersion}</strong> será criada com todas as alterações.
          </p>
          <div className="flex gap-3 shrink-0">
            <button type="button" onClick={onClose}
              className="px-4 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
            <button type="submit" form={uid} disabled={saving || !modulesReady}
              className="px-5 py-2 rounded-lg bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-500 disabled:opacity-60 transition-colors">
              {saving ? 'Salvando…' : `Criar v${nextVersion}`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── página principal ─────────────────────────────────────────────────────────

export function AdminPlansPage() {
  const qc = useQueryClient()
  const { hasAdminPermission } = useAuth()

  const [showCreate, setShowCreate]   = useState(false)
  const [editPlan, setEditPlan]       = useState<Plan | null>(null)
  const [historyPlan, setHistoryPlan] = useState<{ code: string; name: string } | null>(null)
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

  function handleCreated() {
    setShowCreate(false)
    qc.invalidateQueries({ queryKey: ['admin-plans'] })
  }

  function handleEdited() {
    setEditPlan(null)
    qc.invalidateQueries({ queryKey: ['admin-plans'] })
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
        {hasAdminPermission('admin.plans.create') && (
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors">
            <span>+</span> Novo Plano
          </button>
        )}
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
        <span><strong className="text-gray-300">"Editar"</strong> abre o formulário unificado e cria nova versão ao salvar</span>
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
              {displayed.map((plan) => {
                const hasModules = (plan.module_count ?? 0) > 0
                return (
                  <tr key={plan.id} className={`transition-colors ${plan.is_most_popular ? 'bg-yellow-900/10 hover:bg-yellow-900/20' : 'hover:bg-gray-700/30'}`}>

                    <td className="px-3 py-3 text-center">
                      <StarButton
                        active={plan.is_most_popular}
                        disabled={!plan.is_active || !plan.is_current_version || setPopular.isPending || !hasAdminPermission('admin.plans.edit')}
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
                      {hasModules ? (
                        <span className="inline-flex items-center justify-center rounded-full bg-indigo-900/50 border border-indigo-700 text-indigo-300 text-xs font-medium px-2 py-0.5">
                          {plan.module_count}
                        </span>
                      ) : (
                        <span className="text-gray-600 text-xs">—</span>
                      )}
                    </td>

                    <td className="px-3 py-3 whitespace-nowrap">
                      <p className="text-gray-300 font-medium">{brl(plan.total_monthly_price)}</p>
                      {hasModules
                        ? <p className="text-xs text-indigo-400 font-normal">por módulos</p>
                        : <p className="text-xs text-amber-500/70 font-normal">Nenhum módulo</p>}
                    </td>

                    <td className="px-3 py-3 whitespace-nowrap">
                      {hasModules ? (
                        <div>
                          <p className="text-gray-300 font-medium">{brl(plan.total_annual_price)}</p>
                          <p className="text-xs text-gray-500">{brl(plan.total_annual_monthly_price)}/mês no anual</p>
                        </div>
                      ) : (
                        <span className="text-gray-600 text-xs">—</span>
                      )}
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
                          disabled={toggleStatus.isPending || !hasAdminPermission('admin.plans.activate')}
                        />
                        <Badge label={plan.is_active ? 'Ativo' : 'Inativo'} variant={plan.is_active ? 'green' : 'gray'} />
                      </div>
                    </td>

                    <td className="px-3 py-3">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        {plan.is_current_version && hasAdminPermission('admin.plans.edit') && (
                          <button
                            onClick={() => setEditPlan(plan)}
                            className="px-2.5 py-1 rounded-md bg-indigo-900/60 text-indigo-300 text-xs hover:bg-indigo-800/60 transition-colors whitespace-nowrap">
                            Editar
                          </button>
                        )}
                        {versionCount[plan.code] > 1 && hasAdminPermission('admin.plans.version_history') && (
                          <button
                            onClick={() => setHistoryPlan({ code: plan.code, name: plan.name })}
                            className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors">
                            Versões
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Modais */}
      {showCreate && (
        <PlanCreateForm onClose={() => setShowCreate(false)} onSaved={handleCreated} />
      )}

      {editPlan && (
        <EditPlanModal
          plan={editPlan}
          onClose={() => setEditPlan(null)}
          onSaved={handleEdited}
        />
      )}

      {historyPlan && (
        <VersionHistoryModal
          planCode={historyPlan.code}
          planName={historyPlan.name}
          onClose={() => setHistoryPlan(null)}
        />
      )}
    </div>
  )
}
