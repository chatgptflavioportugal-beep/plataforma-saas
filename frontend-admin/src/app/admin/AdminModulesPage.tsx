import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/shared/services/adminApi'
import { useAuth } from '@/core/auth/AuthContext'

// ─── tipos ───────────────────────────────────────────────────────────────────

interface PlatformModule {
  id: string
  name: string
  slug: string
  description: string | null
  module_url: string
  icon_path: string | null
  is_active: boolean
  sort_order: number
  service_count: number
  created_at: string
  updated_at: string
}

interface ServiceGroup {
  id: string
  module_id: string
  name: string
  slug: string
  description: string | null
  icon_path: string | null
  sort_order: number
  status: 'ACTIVE' | 'INACTIVE'
  service_count: number
  created_at: string
  updated_at: string
}

interface ModuleService {
  id: string
  module_id: string
  name: string
  slug: string
  description: string | null
  icon_path: string | null
  is_active: boolean
  sort_order: number
  service_group_id: string | null
  service_group_name: string | null
  service_group_slug: string | null
  route_key: string
  created_at: string
  updated_at: string
}

interface ModuleFormData {
  name: string
  slug: string
  slugManuallyEdited: boolean
  description: string
  module_url: string
  icon_path: string
  is_active: boolean
  sort_order: string
}

interface ServiceFormData {
  name: string
  slug: string
  slugManuallyEdited: boolean
  description: string
  icon_path: string
  is_active: boolean
  sort_order: string
  service_group_id: string
}

interface GroupFormData {
  name: string
  slug: string
  slugManuallyEdited: boolean
  description: string
  icon_path: string
  sort_order: string
}

// ─── helpers ──────────────────────────────────────────────────────────────────

function normalizeSlug(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s_-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function serviceCode(moduleSlug: string, serviceSlug: string, groupSlug?: string | null): string {
  if (!moduleSlug || !serviceSlug) return ''
  if (groupSlug) return `${moduleSlug}.${groupSlug}.${serviceSlug}`
  return `${moduleSlug}.${serviceSlug}`
}

function generateRouteKey(permissionKey: string): string {
  return permissionKey
    .toLowerCase()
    .replace(/[._\s]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
}

const EMPTY_MODULE: ModuleFormData = {
  name: '', slug: '', slugManuallyEdited: false, description: '', module_url: '', icon_path: '', is_active: true, sort_order: '99',
}

const EMPTY_SERVICE: ServiceFormData = {
  name: '', slug: '', slugManuallyEdited: false, description: '', icon_path: '', is_active: true, sort_order: '99', service_group_id: '',
}

const EMPTY_GROUP: GroupFormData = {
  name: '', slug: '', slugManuallyEdited: false, description: '', icon_path: '', sort_order: '99',
}

function moduleToForm(m: PlatformModule): ModuleFormData {
  return {
    name: m.name, slug: m.slug, slugManuallyEdited: true,
    description: m.description ?? '',
    module_url: m.module_url, icon_path: m.icon_path ?? '',
    is_active: m.is_active, sort_order: String(m.sort_order),
  }
}

function serviceToForm(s: ModuleService): ServiceFormData {
  return {
    name: s.name, slug: s.slug, slugManuallyEdited: true,
    description: s.description ?? '',
    icon_path: s.icon_path ?? '', is_active: s.is_active,
    sort_order: String(s.sort_order), service_group_id: s.service_group_id ?? '',
  }
}

function groupToForm(g: ServiceGroup): GroupFormData {
  return {
    name: g.name, slug: g.slug, slugManuallyEdited: true,
    description: g.description ?? '',
    icon_path: g.icon_path ?? '', sort_order: String(g.sort_order),
  }
}

// ─── sub-componentes ──────────────────────────────────────────────────────────

function Badge({ label, variant }: { label: string; variant: 'green' | 'gray' | 'blue' | 'orange' }) {
  const cls = {
    green:  'bg-green-900/50 text-green-300 border border-green-700',
    gray:   'bg-gray-700 text-gray-400 border border-gray-600',
    blue:   'bg-blue-900/50 text-blue-300 border border-blue-700',
    orange: 'bg-orange-900/50 text-orange-300 border border-orange-700',
  }
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${cls[variant]}`}>{label}</span>
}

function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onChange}
      disabled={disabled}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none disabled:opacity-40 ${checked ? 'bg-green-600' : 'bg-gray-600'}`}
    >
      <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-5' : 'translate-x-1'}`} />
    </button>
  )
}

function ModuleIcon({ iconPath, size = 8 }: { iconPath: string | null; size?: number }) {
  const px = size * 4
  if (!iconPath) {
    return (
      <span
        className="inline-flex items-center justify-center rounded bg-gray-700 text-gray-500 text-xs"
        style={{ width: px, height: px }}
      >
        —
      </span>
    )
  }
  return <img src={iconPath} alt="" style={{ width: px, height: px }} className="object-contain flex-shrink-0" />
}

function IconPathField({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  const isValid = !value || value.startsWith('/icons/')
  return (
    <div className="space-y-1">
      <label className="block text-xs font-medium text-gray-400">Icon Path</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`w-full rounded-lg bg-gray-800 border px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500 ${isValid ? 'border-gray-600' : 'border-red-600'}`}
        placeholder={placeholder}
      />
      {!isValid && <p className="text-xs text-red-400">O caminho deve começar com /icons/</p>}
      {value && isValid && (
        <div className="flex items-center gap-2 mt-1">
          <ModuleIcon iconPath={value} size={8} />
          <span className="text-xs text-gray-500">Pré-visualização</span>
        </div>
      )}
    </div>
  )
}

// ─── caixa de código gerado ────────────────────────────────────────────────────

function CodeBox({ label, code }: { label: string; code: string }) {
  const [copied, setCopied] = useState(false)
  function handleCopy() {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }
  return (
    <div className="rounded-lg bg-gray-950 border border-gray-700 px-3 py-2.5 space-y-1">
      <p className="text-xs text-gray-500">{label}</p>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-mono text-blue-400 break-all">{code || <span className="text-gray-600 italic">—</span>}</p>
        {code && (
          <button
            type="button"
            onClick={handleCopy}
            className="shrink-0 text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-300 hover:bg-gray-600 transition-colors"
          >
            {copied ? 'Copiado!' : 'Copiar'}
          </button>
        )}
      </div>
    </div>
  )
}

// ─── formulário de módulo ─────────────────────────────────────────────────────

function ModuleForm({ module, onClose, onSaved }: { module?: PlatformModule; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<ModuleFormData>(module ? moduleToForm(module) : { ...EMPTY_MODULE })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function field<K extends keyof ModuleFormData>(key: K, value: ModuleFormData[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function handleNameChange(value: string) {
    setForm((f) => ({
      ...f,
      name: value,
      slug: f.slugManuallyEdited ? f.slug : normalizeSlug(value),
    }))
  }

  function handleSlugChange(value: string) {
    setForm((f) => ({ ...f, slug: normalizeSlug(value), slugManuallyEdited: true }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        name: form.name, slug: form.slug, description: form.description || null,
        module_url: form.module_url, icon_path: form.icon_path || null,
        is_active: form.is_active, sort_order: parseInt(form.sort_order) || 99,
      }
      if (module) {
        await adminApi.patch(`/api/v1/admin/modules/${module.id}`, payload)
      } else {
        await adminApi.post('/api/v1/admin/modules', payload)
      }
      onSaved()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar módulo')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">{module ? 'Editar Módulo' : 'Novo Módulo'}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input required value={form.name} onChange={(e) => handleNameChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: PDF" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Slug *</label>
              <input required value={form.slug} onChange={(e) => handleSlugChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                placeholder="Ex: pdf" />
            </div>
          </div>
          {form.slug && (
            <CodeBox label="Código do módulo" code={form.slug} />
          )}
          {module && form.slugManuallyEdited && form.slug !== module.slug && (
            <div className="rounded-lg bg-yellow-900/30 border border-yellow-700 px-3 py-2 text-xs text-yellow-300">
              Atenção: alterar o slug pode impactar permissões já configuradas para este módulo.
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
            <textarea value={form.description} onChange={(e) => field('description', e.target.value)} rows={2}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 resize-none"
              placeholder="Descrição curta do módulo" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Link / Rota *</label>
            <input required value={form.module_url} onChange={(e) => field('module_url', e.target.value)}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
              placeholder="Ex: /app/pdf" />
          </div>
          <IconPathField value={form.icon_path} onChange={(v) => field('icon_path', v)} placeholder="/icons/modules/pdf.svg" />
          <div className="flex items-center gap-6">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Ordem</label>
              <input type="number" min="0" value={form.sort_order} onChange={(e) => field('sort_order', e.target.value)}
                className="w-24 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500" />
            </div>
            <div className="flex items-center gap-2 mt-4">
              <Toggle checked={form.is_active} onChange={() => field('is_active', !form.is_active)} />
              <span className="text-sm text-gray-300">{form.is_active ? 'Ativo' : 'Inativo'}</span>
            </div>
          </div>
          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}
          <div className="flex justify-end gap-3 pt-1 pb-1">
            <button type="button" onClick={onClose}
              className="px-4 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
            <button type="submit" disabled={saving}
              className="px-5 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 disabled:opacity-60 transition-colors">
              {saving ? 'Salvando…' : module ? 'Salvar' : 'Criar módulo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── formulário de grupo de serviço ──────────────────────────────────────────

function GroupForm({
  moduleId,
  group,
  onClose,
  onSaved,
}: {
  moduleId: string
  group?: ServiceGroup
  onClose: () => void
  onSaved: () => void
}) {
  const [form, setForm] = useState<GroupFormData>(group ? groupToForm(group) : { ...EMPTY_GROUP })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function field<K extends keyof GroupFormData>(key: K, value: GroupFormData[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function handleNameChange(value: string) {
    setForm((f) => ({
      ...f,
      name: value,
      slug: f.slugManuallyEdited ? f.slug : normalizeSlug(value),
    }))
  }

  function handleSlugChange(value: string) {
    setForm((f) => ({ ...f, slug: normalizeSlug(value), slugManuallyEdited: true }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        name: form.name,
        slug: form.slug,
        description: form.description || null,
        icon_path: form.icon_path || null,
        sort_order: parseInt(form.sort_order) || 99,
      }
      if (group) {
        await adminApi.patch(`/api/v1/admin/modules/${moduleId}/service-groups/${group.id}`, payload)
      } else {
        await adminApi.post(`/api/v1/admin/modules/${moduleId}/service-groups`, payload)
      }
      onSaved()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar grupo')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">{group ? 'Editar Grupo' : 'Novo Grupo de Serviços'}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input required value={form.name} onChange={(e) => handleNameChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: Imóvel" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Slug *</label>
              <input required value={form.slug} onChange={(e) => handleSlugChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                placeholder="Ex: imovel" />
            </div>
          </div>
          {group && form.slugManuallyEdited && form.slug !== group.slug && (
            <div className="rounded-lg bg-yellow-900/30 border border-yellow-700 px-3 py-2 text-xs text-yellow-300">
              Atenção: alterar o slug recalcula os códigos dos serviços vinculados a este grupo.
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
            <textarea value={form.description} onChange={(e) => field('description', e.target.value)} rows={2}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 resize-none"
              placeholder="Descrição do grupo" />
          </div>
          <IconPathField value={form.icon_path} onChange={(v) => field('icon_path', v)} placeholder="/icons/groups/imoveis.svg" />
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Ordem</label>
            <input type="number" min="0" value={form.sort_order} onChange={(e) => field('sort_order', e.target.value)}
              className="w-24 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500" />
          </div>
          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}
          <div className="flex justify-end gap-3 pt-1 pb-1">
            <button type="button" onClick={onClose}
              className="px-4 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
            <button type="submit" disabled={saving}
              className="px-5 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 disabled:opacity-60 transition-colors">
              {saving ? 'Salvando…' : group ? 'Salvar' : 'Criar grupo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── formulário de serviço ────────────────────────────────────────────────────

function ServiceForm({
  moduleId,
  moduleSlug,
  service,
  groups,
  onClose,
  onSaved,
}: {
  moduleId: string
  moduleSlug: string
  service?: ModuleService
  groups: ServiceGroup[]
  onClose: () => void
  onSaved: () => void
}) {
  const [form, setForm] = useState<ServiceFormData>(service ? serviceToForm(service) : { ...EMPTY_SERVICE })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const activeGroups = groups.filter((g) => g.status === 'ACTIVE')

  const selectedGroup = activeGroups.find((g) => g.id === form.service_group_id) ?? null
  const generatedCode = serviceCode(moduleSlug, form.slug, selectedGroup?.slug)

  function field<K extends keyof ServiceFormData>(key: K, value: ServiceFormData[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function handleNameChange(value: string) {
    setForm((f) => ({
      ...f,
      name: value,
      slug: f.slugManuallyEdited ? f.slug : normalizeSlug(value),
    }))
  }

  function handleSlugChange(value: string) {
    setForm((f) => ({ ...f, slug: normalizeSlug(value), slugManuallyEdited: true }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        name: form.name,
        slug: form.slug,
        description: form.description || null,
        icon_path: form.icon_path || null,
        is_active: form.is_active,
        sort_order: parseInt(form.sort_order) || 99,
        service_group_id: form.service_group_id || null,
      }
      if (service) {
        await adminApi.patch(`/api/v1/admin/modules/${moduleId}/services/${service.id}`, payload)
      } else {
        await adminApi.post(`/api/v1/admin/modules/${moduleId}/services`, payload)
      }
      onSaved()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar serviço')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-gray-900 border border-gray-700 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">{service ? 'Editar Serviço' : 'Novo Serviço'}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input required value={form.name} onChange={(e) => handleNameChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: Criar" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Slug *</label>
              <input required value={form.slug} onChange={(e) => handleSlugChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                placeholder="Ex: criar" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Grupo de Serviços</label>
            <select
              value={form.service_group_id}
              onChange={(e) => field('service_group_id', e.target.value)}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
            >
              <option value="">Sem grupo</option>
              {activeGroups.map((g) => (
                <option key={g.id} value={g.id}>{g.name}</option>
              ))}
            </select>
            {activeGroups.length === 0 && (
              <p className="text-xs text-gray-500 mt-1">Nenhum grupo ativo neste módulo.</p>
            )}
          </div>
          {form.slug && (
            <div className="space-y-2">
              <CodeBox label="Código Técnico" code={generatedCode} />
              <CodeBox label="Route Key" code={generateRouteKey(generatedCode)} />
            </div>
          )}
          {service && form.slugManuallyEdited && form.slug !== service.slug && (
            <div className="rounded-lg bg-yellow-900/30 border border-yellow-700 px-3 py-2 text-xs text-yellow-300">
              Atenção: alterar o slug pode impactar permissões já configuradas para este serviço.
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
            <textarea value={form.description} onChange={(e) => field('description', e.target.value)} rows={2}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 resize-none"
              placeholder="Descrição curta do serviço" />
          </div>
          <IconPathField value={form.icon_path} onChange={(v) => field('icon_path', v)} placeholder="/icons/services/criar.svg" />
          <div className="flex items-center gap-6">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Ordem</label>
              <input type="number" min="0" value={form.sort_order} onChange={(e) => field('sort_order', e.target.value)}
                className="w-24 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500" />
            </div>
            <div className="flex items-center gap-2 mt-4">
              <Toggle checked={form.is_active} onChange={() => field('is_active', !form.is_active)} />
              <span className="text-sm text-gray-300">{form.is_active ? 'Ativo' : 'Inativo'}</span>
            </div>
          </div>
          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
          )}
          <div className="flex justify-end gap-3 pt-1 pb-1">
            <button type="button" onClick={onClose}
              className="px-4 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors">
              Cancelar
            </button>
            <button type="submit" disabled={saving}
              className="px-5 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 disabled:opacity-60 transition-colors">
              {saving ? 'Salvando…' : service ? 'Salvar' : 'Criar serviço'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── painel de serviços ───────────────────────────────────────────────────────

function ServicesPanel({ module, onBack }: { module: PlatformModule; onBack: () => void }) {
  const qc = useQueryClient()
  const { hasAdminPermission } = useAuth()
  const [showCreateService, setShowCreateService] = useState(false)
  const [editService, setEditService] = useState<ModuleService | null>(null)
  const [showCreateGroup, setShowCreateGroup] = useState(false)
  const [editGroup, setEditGroup] = useState<ServiceGroup | null>(null)
  const [groupStatusError, setGroupStatusError] = useState<string | null>(null)

  const { data: groups = [], isLoading: groupsLoading } = useQuery({
    queryKey: ['admin-module-service-groups', module.id],
    queryFn: async () => {
      const { data } = await adminApi.get<ServiceGroup[]>(`/api/v1/admin/modules/${module.id}/service-groups`)
      return data
    },
    staleTime: 15_000,
  })

  const { data: services = [], isLoading: servicesLoading } = useQuery({
    queryKey: ['admin-module-services', module.id],
    queryFn: async () => {
      const { data } = await adminApi.get<ModuleService[]>(`/api/v1/admin/modules/${module.id}/services`)
      return data
    },
    staleTime: 15_000,
  })

  const toggleServiceStatus = useMutation({
    mutationFn: (serviceId: string) =>
      adminApi.patch(`/api/v1/admin/modules/${module.id}/services/${serviceId}/status`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-module-services', module.id] }),
  })

  const toggleGroupStatus = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: 'ACTIVE' | 'INACTIVE' }) => {
      await adminApi.patch(`/api/v1/admin/modules/${module.id}/service-groups/${id}/status`, { status })
    },
    onSuccess: () => {
      setGroupStatusError(null)
      qc.invalidateQueries({ queryKey: ['admin-module-service-groups', module.id] })
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setGroupStatusError(msg ?? 'Erro ao alterar status do grupo')
    },
  })

  function handleSavedService() {
    setShowCreateService(false)
    setEditService(null)
    qc.invalidateQueries({ queryKey: ['admin-module-services', module.id] })
    qc.invalidateQueries({ queryKey: ['admin-modules'] })
  }

  function handleSavedGroup() {
    setShowCreateGroup(false)
    setEditGroup(null)
    qc.invalidateQueries({ queryKey: ['admin-module-service-groups', module.id] })
    qc.invalidateQueries({ queryKey: ['admin-module-services', module.id] })
  }

  return (
    <div className="space-y-8">
      {/* Breadcrumb */}
      <div className="flex items-center gap-3">
        <button onClick={onBack} className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          Módulos
        </button>
        <span className="text-gray-600">/</span>
        <div className="flex items-center gap-2">
          <ModuleIcon iconPath={module.icon_path} size={6} />
          <span className="text-white font-semibold">{module.name}</span>
          <Badge label={module.is_active ? 'Ativo' : 'Inativo'} variant={module.is_active ? 'green' : 'gray'} />
        </div>
      </div>

      {/* ── Grupos de Serviços ─────────────────────────────────────── */}
      {hasAdminPermission('admin.services.groups.view') && (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-white">Grupos de Serviços</h2>
            <p className="text-xs text-gray-400 mt-0.5">Organizam serviços em categorias dentro do módulo</p>
          </div>
          {hasAdminPermission('admin.services.groups.create') && (
            <button
              onClick={() => setShowCreateGroup(true)}
              className="flex items-center gap-2 px-3 py-2 rounded-lg bg-gray-700 text-gray-200 text-sm hover:bg-gray-600 transition-colors"
            >
              <span>+</span> Novo Grupo
            </button>
          )}
        </div>

        {groupStatusError && (
          <div className="rounded-lg bg-orange-900/30 border border-orange-700 px-4 py-3 text-sm text-orange-300">
            {groupStatusError}
            <button onClick={() => setGroupStatusError(null)} className="ml-2 font-bold">✕</button>
          </div>
        )}

        <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-x-auto">
          {groupsLoading ? (
            <div className="py-8 text-center text-sm text-gray-400">Carregando…</div>
          ) : groups.length === 0 ? (
            <div className="py-8 text-center text-sm text-gray-400">
              Nenhum grupo cadastrado. Grupos são opcionais — módulos simples não precisam de grupos.
            </div>
          ) : (
            <table className="min-w-full divide-y divide-gray-700 text-sm">
              <thead>
                <tr className="bg-gray-800/80">
                  {['Nome', 'Slug', 'Serviços', 'Ordem', 'Status', 'Ações'].map((h) => (
                    <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700/60">
                {groups.map((grp) => (
                  <tr key={grp.id} className="hover:bg-gray-700/30 transition-colors">
                    <td className="px-3 py-3">
                      <div className="text-white font-medium">{grp.name}</div>
                      {grp.description && <div className="text-xs text-gray-500 mt-0.5">{grp.description}</div>}
                    </td>
                    <td className="px-3 py-3 font-mono text-gray-400 text-xs">{grp.slug}</td>
                    <td className="px-3 py-3 text-gray-400">{grp.service_count} serviço{grp.service_count !== 1 ? 's' : ''}</td>
                    <td className="px-3 py-3 text-gray-400">{grp.sort_order}</td>
                    <td className="px-3 py-3">
                      <Badge
                        label={grp.status === 'ACTIVE' ? 'Ativo' : 'Inativo'}
                        variant={grp.status === 'ACTIVE' ? 'green' : 'gray'}
                      />
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-2">
                        {hasAdminPermission('admin.services.groups.edit') && (
                          <button onClick={() => setEditGroup(grp)}
                            className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors">
                            Editar
                          </button>
                        )}
                        {hasAdminPermission(grp.status === 'ACTIVE' ? 'admin.services.groups.deactivate' : 'admin.services.groups.activate') && (
                          <button
                            onClick={() => toggleGroupStatus.mutate({
                              id: grp.id,
                              status: grp.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
                            })}
                            disabled={toggleGroupStatus.isPending}
                            className={`px-2.5 py-1 rounded-md text-xs transition-colors disabled:opacity-50 ${
                              grp.status === 'ACTIVE'
                                ? 'bg-orange-900/40 text-orange-300 hover:bg-orange-900/60'
                                : 'bg-green-900/40 text-green-300 hover:bg-green-900/60'
                            }`}
                          >
                            {grp.status === 'ACTIVE' ? 'Inativar' : 'Ativar'}
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
      </div>
      )}

      {/* ── Serviços ───────────────────────────────────────────────── */}
      {hasAdminPermission('admin.services.view') && (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-white">Serviços</h2>
            <p className="text-xs text-gray-400 mt-0.5">{services.length} serviço{services.length !== 1 ? 's' : ''} cadastrado{services.length !== 1 ? 's' : ''}</p>
          </div>
          {hasAdminPermission('admin.services.create') && (
            <button
              onClick={() => setShowCreateService(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors"
            >
              <span>+</span> Novo Serviço
            </button>
          )}
        </div>

        <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-x-auto">
          {servicesLoading ? (
            <div className="py-10 text-center text-sm text-gray-400">Carregando…</div>
          ) : services.length === 0 ? (
            <div className="py-10 text-center text-sm text-gray-400">Nenhum serviço cadastrado neste módulo.</div>
          ) : (
            <table className="min-w-full divide-y divide-gray-700 text-sm">
              <thead>
                <tr className="bg-gray-800/80">
                  {['Ícone', 'Nome', 'Grupo', 'Código', 'Ordem', 'Status', 'Ações'].map((h) => (
                    <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700/60">
                {services.map((svc) => (
                  <tr key={svc.id} className="hover:bg-gray-700/30 transition-colors">
                    <td className="px-3 py-3">
                      <ModuleIcon iconPath={svc.icon_path} size={8} />
                    </td>
                    <td className="px-3 py-3 text-white font-medium whitespace-nowrap">{svc.name}</td>
                    <td className="px-3 py-3">
                      {svc.service_group_name ? (
                        <span className="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium bg-blue-900/40 text-blue-300 border border-blue-800">
                          {svc.service_group_name}
                        </span>
                      ) : (
                        <span className="text-xs text-gray-600">—</span>
                      )}
                    </td>
                    <td className="px-3 py-3 font-mono text-xs">
                      <span className="text-blue-400">
                        {serviceCode(module.slug, svc.slug, svc.service_group_slug)}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-gray-400">{svc.sort_order}</td>
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-2">
                        <Toggle
                          checked={svc.is_active}
                          onChange={() => toggleServiceStatus.mutate(svc.id)}
                          disabled={toggleServiceStatus.isPending || !hasAdminPermission(svc.is_active ? 'admin.services.deactivate' : 'admin.services.activate')}
                        />
                        <Badge label={svc.is_active ? 'Ativo' : 'Inativo'} variant={svc.is_active ? 'green' : 'gray'} />
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      {hasAdminPermission('admin.services.edit') && (
                        <button onClick={() => setEditService(svc)}
                          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors">
                          Editar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
      )}

      {showCreateService && (
        <ServiceForm moduleId={module.id} moduleSlug={module.slug} groups={groups} onClose={() => setShowCreateService(false)} onSaved={handleSavedService} />
      )}
      {editService && (
        <ServiceForm moduleId={module.id} moduleSlug={module.slug} service={editService} groups={groups} onClose={() => setEditService(null)} onSaved={handleSavedService} />
      )}
      {showCreateGroup && (
        <GroupForm moduleId={module.id} onClose={() => setShowCreateGroup(false)} onSaved={handleSavedGroup} />
      )}
      {editGroup && (
        <GroupForm moduleId={module.id} group={editGroup} onClose={() => setEditGroup(null)} onSaved={handleSavedGroup} />
      )}
    </div>
  )
}

// ─── página principal ─────────────────────────────────────────────────────────

export function AdminModulesPage() {
  const qc = useQueryClient()
  const { hasAdminPermission } = useAuth()

  const [search, setSearch] = useState('')
  const [filterActive, setFilterActive] = useState<boolean | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [editModule, setEditModule] = useState<PlatformModule | null>(null)
  const [selectedModule, setSelectedModule] = useState<PlatformModule | null>(null)

  const { data: modules = [], isLoading } = useQuery({
    queryKey: ['admin-modules', search, filterActive],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (search.trim()) params.search = search.trim()
      if (filterActive !== null) params.is_active = String(filterActive)
      const { data } = await adminApi.get<PlatformModule[]>('/api/v1/admin/modules', { params })
      return data
    },
    staleTime: 15_000,
    retry: false,
  })

  const toggleStatus = useMutation({
    mutationFn: (id: string) => adminApi.patch(`/api/v1/admin/modules/${id}/status`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-modules'] }),
  })

  function handleSaved() {
    setShowCreate(false)
    setEditModule(null)
    qc.invalidateQueries({ queryKey: ['admin-modules'] })
  }

  if (selectedModule) {
    return <ServicesPanel module={selectedModule} onBack={() => setSelectedModule(null)} />
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Módulos</h1>
          <p className="text-sm text-gray-400 mt-0.5">Cadastro de módulos e serviços da plataforma</p>
        </div>
        {hasAdminPermission('admin.modules.create') && (
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors"
          >
            <span>+</span> Novo Módulo
          </button>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar por nome ou slug…"
          className="w-64 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-blue-500"
        />
        <div className="flex rounded-lg overflow-hidden border border-gray-600">
          {[
            { label: 'Todos', value: null },
            { label: 'Ativos', value: true },
            { label: 'Inativos', value: false },
          ].map(({ label, value }) => (
            <button
              key={label}
              onClick={() => setFilterActive(value)}
              className={`px-3 py-2 text-xs font-medium transition-colors ${filterActive === value
                ? 'bg-blue-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-white'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <span className="text-xs text-gray-500">
          {modules.length} módulo{modules.length !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-x-auto">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Carregando…</div>
        ) : modules.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">Nenhum módulo encontrado.</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-700 text-sm">
            <thead>
              <tr className="bg-gray-800/80">
                {['Ícone', 'Nome', 'Slug', 'Link / Rota', 'Serviços', 'Ordem', 'Status', 'Ações'].map((h) => (
                  <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/60">
              {modules.map((mod) => (
                <tr key={mod.id} className="hover:bg-gray-700/30 transition-colors">
                  <td className="px-3 py-3"><ModuleIcon iconPath={mod.icon_path} size={8} /></td>
                  <td className="px-3 py-3 text-white font-medium whitespace-nowrap">{mod.name}</td>
                  <td className="px-3 py-3 font-mono text-gray-400 text-xs">{mod.slug}</td>
                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap max-w-[160px] truncate">{mod.module_url}</td>
                  <td className="px-3 py-3">
                    {hasAdminPermission('admin.services.view') ? (
                      <>
                        <span className="text-gray-300 font-semibold">{mod.service_count}</span>
                        <span className="text-xs text-gray-600 ml-1">serviço{mod.service_count !== 1 ? 's' : ''}</span>
                      </>
                    ) : (
                      <span className="text-xs text-gray-600">—</span>
                    )}
                  </td>
                  <td className="px-3 py-3 text-gray-400">{mod.sort_order}</td>
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <Toggle
                        checked={mod.is_active}
                        onChange={() => toggleStatus.mutate(mod.id)}
                        disabled={toggleStatus.isPending || !hasAdminPermission(mod.is_active ? 'admin.modules.deactivate' : 'admin.modules.activate')}
                      />
                      <Badge label={mod.is_active ? 'Ativo' : 'Inativo'} variant={mod.is_active ? 'green' : 'gray'} />
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setSelectedModule(mod)}
                        className="px-2.5 py-1 rounded-md bg-blue-700 text-blue-200 text-xs hover:bg-blue-600 transition-colors whitespace-nowrap"
                      >
                        Serviços
                      </button>
                      {hasAdminPermission('admin.modules.edit') && (
                        <button
                          onClick={() => setEditModule(mod)}
                          className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors"
                        >
                          Editar
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

      {showCreate && <ModuleForm onClose={() => setShowCreate(false)} onSaved={handleSaved} />}
      {editModule && <ModuleForm module={editModule} onClose={() => setEditModule(null)} onSaved={handleSaved} />}
    </div>
  )
}
