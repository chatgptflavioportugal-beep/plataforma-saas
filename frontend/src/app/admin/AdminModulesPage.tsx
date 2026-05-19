import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'

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

interface ModuleService {
  id: string
  module_id: string
  name: string
  slug: string
  description: string | null
  icon_path: string | null
  is_active: boolean
  sort_order: number
  created_at: string
  updated_at: string
}

interface ModuleFormData {
  name: string
  slug: string
  description: string
  module_url: string
  icon_path: string
  is_active: boolean
  sort_order: string
}

interface ServiceFormData {
  name: string
  slug: string
  description: string
  icon_path: string
  is_active: boolean
  sort_order: string
}

// ─── helpers ──────────────────────────────────────────────────────────────────

function toSlug(value: string) {
  return value.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '')
}

const EMPTY_MODULE: ModuleFormData = {
  name: '', slug: '', description: '', module_url: '', icon_path: '', is_active: true, sort_order: '99',
}

const EMPTY_SERVICE: ServiceFormData = {
  name: '', slug: '', description: '', icon_path: '', is_active: true, sort_order: '99',
}

function moduleToForm(m: PlatformModule): ModuleFormData {
  return {
    name: m.name,
    slug: m.slug,
    description: m.description ?? '',
    module_url: m.module_url,
    icon_path: m.icon_path ?? '',
    is_active: m.is_active,
    sort_order: String(m.sort_order),
  }
}

function serviceToForm(s: ModuleService): ServiceFormData {
  return {
    name: s.name,
    slug: s.slug,
    description: s.description ?? '',
    icon_path: s.icon_path ?? '',
    is_active: s.is_active,
    sort_order: String(s.sort_order),
  }
}

// ─── sub-componentes ──────────────────────────────────────────────────────────

function Badge({ label, variant }: { label: string; variant: 'green' | 'gray' | 'blue' }) {
  const cls = {
    green: 'bg-green-900/50 text-green-300 border border-green-700',
    gray: 'bg-gray-700 text-gray-400 border border-gray-600',
    blue: 'bg-blue-900/50 text-blue-300 border border-blue-700',
  }
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${cls[variant]}`}>{label}</span>
}

function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onChange}
      disabled={disabled}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none disabled:opacity-40 ${checked ? 'bg-green-600' : 'bg-gray-600'
        }`}
    >
      <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-5' : 'translate-x-1'
        }`} />
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
  return (
    <img
      src={iconPath}
      alt=""
      style={{ width: px, height: px }}
      className="object-contain flex-shrink-0"
    />
  )
}

// ─── campo icon path ──────────────────────────────────────────────────────────

function IconPathField({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  const isValid = !value || value.startsWith('/icons/')
  return (
    <div className="space-y-1">
      <label className="block text-xs font-medium text-gray-400">Icon Path</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`w-full rounded-lg bg-gray-800 border px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500 ${isValid ? 'border-gray-600' : 'border-red-600'
          }`}
        placeholder={placeholder}
      />
      {!isValid && (
        <p className="text-xs text-red-400">O caminho deve começar com /icons/</p>
      )}
      {value && isValid && (
        <div className="flex items-center gap-2 mt-1">
          <ModuleIcon iconPath={value} size={8} />
          <span className="text-xs text-gray-500">Pré-visualização</span>
        </div>
      )}
    </div>
  )
}

// ─── formulário de módulo ─────────────────────────────────────────────────────

interface ModuleFormProps {
  module?: PlatformModule
  onClose: () => void
  onSaved: () => void
}

function ModuleForm({ module, onClose, onSaved }: ModuleFormProps) {
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
      slug: module ? f.slug : toSlug(value),
    }))
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
        module_url: form.module_url,
        icon_path: form.icon_path || null,
        is_active: form.is_active,
        sort_order: parseInt(form.sort_order) || 99,
      }
      if (module) {
        await api.patch(`/api/v1/admin/modules/${module.id}`, payload)
      } else {
        await api.post('/api/v1/admin/modules', payload)
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
          <h2 className="text-lg font-semibold text-white">
            {module ? 'Editar Módulo' : 'Novo Módulo'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input
                required
                value={form.name}
                onChange={(e) => handleNameChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: PDF"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Slug *</label>
              <input
                required
                value={form.slug}
                onChange={(e) => field('slug', toSlug(e.target.value))}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                placeholder="Ex: pdf"
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
              placeholder="Descrição curta do módulo"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Link / Rota *</label>
            <input
              required
              value={form.module_url}
              onChange={(e) => field('module_url', e.target.value)}
              className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
              placeholder="Ex: /app/pdf"
            />
          </div>

          <IconPathField
            value={form.icon_path}
            onChange={(v) => field('icon_path', v)}
            placeholder="/icons/modules/pdf.svg"
          />

          <div className="flex items-center gap-6">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Ordem</label>
              <input
                type="number" min="0"
                value={form.sort_order}
                onChange={(e) => field('sort_order', e.target.value)}
                className="w-24 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>
            <div className="flex items-center gap-2 mt-4">
              <Toggle checked={form.is_active} onChange={() => field('is_active', !form.is_active)} />
              <span className="text-sm text-gray-300">{form.is_active ? 'Ativo' : 'Inativo'}</span>
            </div>
          </div>

          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">
              {error}
            </div>
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

// ─── formulário de serviço ────────────────────────────────────────────────────

interface ServiceFormProps {
  moduleId: string
  service?: ModuleService
  onClose: () => void
  onSaved: () => void
}

function ServiceForm({ moduleId, service, onClose, onSaved }: ServiceFormProps) {
  const [form, setForm] = useState<ServiceFormData>(service ? serviceToForm(service) : { ...EMPTY_SERVICE })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function field<K extends keyof ServiceFormData>(key: K, value: ServiceFormData[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function handleNameChange(value: string) {
    setForm((f) => ({
      ...f,
      name: value,
      slug: service ? f.slug : toSlug(value),
    }))
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
      }
      if (service) {
        await api.patch(`/api/v1/admin/modules/${moduleId}/services/${service.id}`, payload)
      } else {
        await api.post(`/api/v1/admin/modules/${moduleId}/services`, payload)
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
          <h2 className="text-lg font-semibold text-white">
            {service ? 'Editar Serviço' : 'Novo Serviço'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">✕</button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
              <input
                required
                value={form.name}
                onChange={(e) => handleNameChange(e.target.value)}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                placeholder="Ex: PDF Merge"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Slug *</label>
              <input
                required
                value={form.slug}
                onChange={(e) => field('slug', toSlug(e.target.value))}
                className="w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                placeholder="Ex: pdf-merge"
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
              placeholder="Descrição curta do serviço"
            />
          </div>

          <IconPathField
            value={form.icon_path}
            onChange={(v) => field('icon_path', v)}
            placeholder="/icons/services/pdf-merge.svg"
          />

          <div className="flex items-center gap-6">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Ordem</label>
              <input
                type="number" min="0"
                value={form.sort_order}
                onChange={(e) => field('sort_order', e.target.value)}
                className="w-24 rounded-lg bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>
            <div className="flex items-center gap-2 mt-4">
              <Toggle checked={form.is_active} onChange={() => field('is_active', !form.is_active)} />
              <span className="text-sm text-gray-300">{form.is_active ? 'Ativo' : 'Inativo'}</span>
            </div>
          </div>

          {error && (
            <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">
              {error}
            </div>
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

interface ServicesPanelProps {
  module: PlatformModule
  onBack: () => void
}

function ServicesPanel({ module, onBack }: ServicesPanelProps) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editService, setEditService] = useState<ModuleService | null>(null)

  const { data: services = [], isLoading } = useQuery({
    queryKey: ['admin-module-services', module.id],
    queryFn: async () => {
      const { data } = await api.get<ModuleService[]>(`/api/v1/admin/modules/${module.id}/services`)
      return data
    },
    staleTime: 15_000,
  })

  const toggleStatus = useMutation({
    mutationFn: (serviceId: string) =>
      api.patch(`/api/v1/admin/modules/${module.id}/services/${serviceId}/status`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-module-services', module.id] }),
  })

  function handleSaved() {
    setShowCreate(false)
    setEditService(null)
    qc.invalidateQueries({ queryKey: ['admin-module-services', module.id] })
    qc.invalidateQueries({ queryKey: ['admin-modules'] })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
          className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors"
        >
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

      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white">Serviços do módulo</h2>
          <p className="text-sm text-gray-400 mt-0.5 font-mono">{module.name}</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors"
        >
          <span>+</span> Novo Serviço
        </button>
      </div>

      <div className="rounded-xl bg-gray-800/60 border border-gray-700 overflow-x-auto">
        {isLoading ? (
          <div className="py-10 text-center text-sm text-gray-400">Carregando…</div>
        ) : services.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-400">
            Nenhum serviço cadastrado neste módulo.
          </div>
        ) : (
          <table className="min-w-full divide-y divide-gray-700 text-sm">
            <thead>
              <tr className="bg-gray-800/80">
                {['Ícone', 'Nome', 'Slug', 'Ordem', 'Status', 'Ações'].map((h) => (
                  <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">
                    {h}
                  </th>
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
                  <td className="px-3 py-3 font-mono text-gray-400 text-xs">{svc.slug}</td>
                  <td className="px-3 py-3 text-gray-400">{svc.sort_order}</td>
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <Toggle
                        checked={svc.is_active}
                        onChange={() => toggleStatus.mutate(svc.id)}
                        disabled={toggleStatus.isPending}
                      />
                      <Badge label={svc.is_active ? 'Ativo' : 'Inativo'} variant={svc.is_active ? 'green' : 'gray'} />
                    </div>
                  </td>
                  <td className="px-3 py-3">
                    <button
                      onClick={() => setEditService(svc)}
                      className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors"
                    >
                      Editar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showCreate && (
        <ServiceForm moduleId={module.id} onClose={() => setShowCreate(false)} onSaved={handleSaved} />
      )}
      {editService && (
        <ServiceForm moduleId={module.id} service={editService} onClose={() => setEditService(null)} onSaved={handleSaved} />
      )}
    </div>
  )
}

// ─── página principal ─────────────────────────────────────────────────────────

export function AdminModulesPage() {
  const qc = useQueryClient()

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
      const { data } = await api.get<PlatformModule[]>('/api/v1/admin/modules', { params })
      return data
    },
    staleTime: 15_000,
    retry: false,
  })

  const toggleStatus = useMutation({
    mutationFn: (id: string) => api.patch(`/api/v1/admin/modules/${id}/status`, {}),
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
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 transition-colors"
        >
          <span>+</span> Novo Módulo
        </button>
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
                  <th key={h} className="px-3 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wide whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700/60">
              {modules.map((mod) => (
                <tr key={mod.id} className="hover:bg-gray-700/30 transition-colors">
                  <td className="px-3 py-3">
                    <ModuleIcon iconPath={mod.icon_path} size={8} />
                  </td>
                  <td className="px-3 py-3 text-white font-medium whitespace-nowrap">{mod.name}</td>
                  <td className="px-3 py-3 font-mono text-gray-400 text-xs">{mod.slug}</td>
                  <td className="px-3 py-3 text-gray-400 text-xs whitespace-nowrap max-w-[160px] truncate">{mod.module_url}</td>
                  <td className="px-3 py-3">
                    <span className="text-gray-300 font-semibold">{mod.service_count}</span>
                    <span className="text-xs text-gray-600 ml-1">serviço{mod.service_count !== 1 ? 's' : ''}</span>
                  </td>
                  <td className="px-3 py-3 text-gray-400">{mod.sort_order}</td>
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <Toggle
                        checked={mod.is_active}
                        onChange={() => toggleStatus.mutate(mod.id)}
                        disabled={toggleStatus.isPending}
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
                      <button
                        onClick={() => setEditModule(mod)}
                        className="px-2.5 py-1 rounded-md bg-gray-700 text-gray-200 text-xs hover:bg-gray-600 transition-colors"
                      >
                        Editar
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showCreate && (
        <ModuleForm onClose={() => setShowCreate(false)} onSaved={handleSaved} />
      )}
      {editModule && (
        <ModuleForm module={editModule} onClose={() => setEditModule(null)} onSaved={handleSaved} />
      )}
    </div>
  )
}
