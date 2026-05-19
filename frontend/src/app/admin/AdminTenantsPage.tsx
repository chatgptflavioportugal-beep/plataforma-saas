import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { AdminTenantDetailModal } from './AdminTenantDetailModal'

interface AdminTenant {
  id: string
  name: string
  slug: string
  status: 'trial' | 'active' | 'suspended' | 'cancelled'
  created_at: string
  trial_ends_at: string | null
  plan_name: string | null
  plan_code: string | null
  subscription_status: string | null
  owner_name: string | null
  owner_email: string | null
  member_count: number
  pending_invitations_count: number
}

function fmt(date: string | null | undefined) {
  if (!date) return '—'
  return new Date(date).toLocaleDateString('pt-BR')
}

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}

const STATUS_COLOR: Record<string, string> = {
  trial: 'bg-blue-900 text-blue-200',
  active: 'bg-green-900 text-green-200',
  suspended: 'bg-red-900 text-red-200',
  cancelled: 'bg-gray-800 text-gray-400',
}

const STATUS_LABEL: Record<string, string> = {
  trial: 'Trial',
  active: 'Ativa',
  suspended: 'Suspensa',
  cancelled: 'Cancelada',
}

export function AdminTenantsPage() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [hasExtraMembers, setHasExtraMembers] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const debouncedSearch = useDebounce(search, 350)

  const params = new URLSearchParams()
  if (debouncedSearch) params.set('search', debouncedSearch)
  if (status) params.set('status', status)
  if (hasExtraMembers) params.set('has_extra_members', hasExtraMembers)

  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ['admin-tenants', params.toString()],
    queryFn: async () => {
      const url = `/api/v1/admin/tenants${params.size ? `?${params}` : ''}`
      const { data } = await api.get<AdminTenant[]>(url)
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  const hasFilters = search || status || hasExtraMembers

  function resetFilters() {
    setSearch('')
    setStatus('')
    setHasExtraMembers('')
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Empresas</h1>
        <p className="mt-1 text-sm text-gray-400">
          Tenants de negócio cadastrados na plataforma — planos, membros e proprietários.
        </p>
      </div>

      {/* Filtros */}
      <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <div className="lg:col-span-2">
            <input
              type="text"
              placeholder="Buscar por nome, slug, proprietário ou e-mail..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <FilterSelect
            label="Status"
            value={status}
            onChange={setStatus}
            options={[
              { value: '', label: 'Todos os status' },
              { value: 'trial', label: 'Trial' },
              { value: 'active', label: 'Ativa' },
              { value: 'suspended', label: 'Suspensa' },
              { value: 'cancelled', label: 'Cancelada' },
            ]}
          />
        </div>

        <div className="flex flex-wrap items-center gap-2 pt-1">
          <span className="text-xs text-gray-500 mr-1">Membros:</span>
          {[
            { value: '', label: 'Todos' },
            { value: 'true', label: 'Com membros além do dono' },
            { value: 'false', label: 'Sem membros extras' },
          ].map((opt) => (
            <button
              key={opt.value}
              onClick={() => setHasExtraMembers(opt.value)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                hasExtraMembers === opt.value
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
              }`}
            >
              {opt.label}
            </button>
          ))}

          {hasFilters && (
            <button
              onClick={resetFilters}
              className="ml-auto text-xs text-gray-400 hover:text-white transition-colors"
            >
              Limpar filtros
            </button>
          )}
        </div>
      </div>

      {/* Tabela */}
      {isLoading ? (
        <p className="text-gray-400 text-sm">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700">
            <span className="text-sm text-gray-400">
              {tenants.length} empresa{tenants.length !== 1 ? 's' : ''}
              {hasFilters ? ' (filtradas)' : ''}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-700">
              <thead>
                <tr>
                  {['Nome', 'Slug', 'Proprietário', 'Plano', 'Status', 'Membros', 'Convites', 'Criado em', ''].map((h) => (
                    <th
                      key={h}
                      className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase whitespace-nowrap"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700">
                {tenants.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="px-4 py-10 text-center text-sm text-gray-500">
                      Nenhuma empresa encontrada.
                    </td>
                  </tr>
                ) : (
                  tenants.map((t) => (
                    <tr key={t.id} className="hover:bg-gray-750 transition-colors">
                      <td className="px-4 py-3 text-sm text-white font-medium whitespace-nowrap">
                        {t.name}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400 whitespace-nowrap">
                        {t.slug}
                      </td>
                      <td className="px-4 py-3 text-sm whitespace-nowrap">
                        <div className="text-white">{t.owner_name ?? '—'}</div>
                        {t.owner_email && (
                          <div className="text-gray-500 text-xs">{t.owner_email}</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-300 whitespace-nowrap">
                        {t.plan_name ?? '—'}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLOR[t.status] ?? 'bg-gray-700 text-gray-300'}`}>
                          {STATUS_LABEL[t.status] ?? t.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-center">
                        {t.member_count > 0 ? (
                          <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-purple-900 text-purple-200 text-xs font-semibold">
                            {t.member_count}
                          </span>
                        ) : (
                          <span className="text-gray-600">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-center">
                        {t.pending_invitations_count > 0 ? (
                          <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-yellow-900 text-yellow-200 text-xs font-semibold">
                            {t.pending_invitations_count}
                          </span>
                        ) : (
                          <span className="text-gray-600">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400 whitespace-nowrap">
                        {fmt(t.created_at)}
                      </td>
                      <td className="px-4 py-3 text-right whitespace-nowrap">
                        <button
                          onClick={() => setSelectedId(t.id)}
                          className="text-xs text-blue-400 hover:text-blue-300 transition-colors"
                        >
                          Ver detalhe
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <AdminTenantDetailModal
        tenantId={selectedId}
        onClose={() => setSelectedId(null)}
      />
    </div>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-lg bg-gray-700 border border-gray-600 text-sm text-white px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
      aria-label={label}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
}
