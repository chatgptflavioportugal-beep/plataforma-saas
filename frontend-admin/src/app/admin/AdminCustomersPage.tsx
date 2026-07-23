import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/shared/services/adminApi'
import { AdminCustomerDetailModal } from './AdminCustomerDetailModal'

interface Customer {
  id: string
  email: string
  full_name: string | null
  is_active: boolean
  created_at: string
  last_sign_in_at: string | null
  has_individual_profile: boolean
  owned_companies_count: number
  member_companies_count: number
  total_profiles: number
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

type BoolFilter = '' | 'true' | 'false'

export function AdminCustomersPage() {
  const [search, setSearch] = useState('')
  const [hasIndividual, setHasIndividual] = useState<BoolFilter>('')
  const [hasOwnedCompany, setHasOwnedCompany] = useState<BoolFilter>('')
  const [isMember, setIsMember] = useState<BoolFilter>('')
  const [isActive, setIsActive] = useState<BoolFilter>('')
  const [profileType, setProfileType] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const debouncedSearch = useDebounce(search, 350)

  const params = new URLSearchParams()
  if (debouncedSearch) params.set('search', debouncedSearch)
  if (hasIndividual)   params.set('has_individual', hasIndividual)
  if (hasOwnedCompany) params.set('has_owned_company', hasOwnedCompany)
  if (isMember)        params.set('is_member', isMember)
  if (isActive)        params.set('is_active', isActive)
  if (profileType)     params.set('profile_type', profileType)

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['admin-customers', params.toString()],
    queryFn: async () => {
      const url = `/api/v1/admin/customers${params.size ? `?${params}` : ''}`
      const { data } = await adminApi.get<Customer[]>(url)
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  function resetFilters() {
    setSearch('')
    setHasIndividual('')
    setHasOwnedCompany('')
    setIsMember('')
    setIsActive('')
    setProfileType('')
  }

  const hasFilters = search || hasIndividual || hasOwnedCompany || isMember || isActive || profileType

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Clientes</h1>
        <p className="mt-1 text-sm text-gray-400">
          Usuários cadastrados na plataforma — perfis, empresas e membros.
        </p>
      </div>

      {/* Filtros */}
      <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {/* Busca */}
          <div className="lg:col-span-2">
            <input
              type="text"
              placeholder="Buscar por nome ou e-mail..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white placeholder-gray-500 text-sm px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Status */}
          <FilterSelect
            label="Status"
            value={isActive}
            onChange={setIsActive}
            options={[
              { value: '', label: 'Todos os status' },
              { value: 'true', label: 'Ativos' },
              { value: 'false', label: 'Inativos' },
            ]}
          />

          {/* Perfil Individual */}
          <FilterSelect
            label="Perfil Individual"
            value={hasIndividual}
            onChange={setHasIndividual}
            options={[
              { value: '', label: 'Todos' },
              { value: 'true', label: 'Com Perfil Individual' },
              { value: 'false', label: 'Sem Perfil Individual' },
            ]}
          />

          {/* Empresa criada */}
          <FilterSelect
            label="Empresa criada"
            value={hasOwnedCompany}
            onChange={setHasOwnedCompany}
            options={[
              { value: '', label: 'Todos' },
              { value: 'true', label: 'Criou empresa' },
              { value: 'false', label: 'Não criou empresa' },
            ]}
          />

          {/* Membro */}
          <FilterSelect
            label="É membro"
            value={isMember}
            onChange={setIsMember}
            options={[
              { value: '', label: 'Todos' },
              { value: 'true', label: 'É membro de empresa' },
              { value: 'false', label: 'Não é membro' },
            ]}
          />
        </div>

        {/* Filtro por tipo de perfil */}
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <span className="text-xs text-gray-500 mr-1">Perfil:</span>
          {[
            { value: '', label: 'Todos' },
            { value: 'individual', label: 'Individual' },
            { value: 'owned_company', label: 'Empresa criada' },
            { value: 'member_company', label: 'Empresa como membro' },
          ].map((opt) => (
            <button
              key={opt.value}
              onClick={() => setProfileType(opt.value)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                profileType === opt.value
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
              {customers.length} cliente{customers.length !== 1 ? 's' : ''}
              {hasFilters ? ' (filtrados)' : ''}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-700">
              <thead>
                <tr>
                  {[
                    'Nome',
                    'E-mail',
                    'Status',
                    'Perfil Individual',
                    'Emp. Criadas',
                    'Membro em',
                    'Perfis',
                    'Cadastro',
                    'Último acesso',
                    '',
                  ].map((h) => (
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
                {customers.length === 0 ? (
                  <tr>
                    <td colSpan={10} className="px-4 py-10 text-center text-sm text-gray-500">
                      Nenhum cliente encontrado.
                    </td>
                  </tr>
                ) : (
                  customers.map((c) => (
                    <tr key={c.id} className="hover:bg-gray-750 transition-colors">
                      <td className="px-4 py-3 text-sm text-white font-medium whitespace-nowrap">
                        {c.full_name ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400 whitespace-nowrap">
                        {c.email}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${c.is_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'}`}>
                          {c.is_active ? 'Ativo' : 'Inativo'}
                        </span>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {c.has_individual_profile ? (
                          <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-blue-900 text-blue-200">
                            Sim
                          </span>
                        ) : (
                          <span className="text-sm text-gray-600">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-center">
                        {c.owned_companies_count > 0 ? (
                          <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-purple-900 text-purple-200 text-xs font-semibold">
                            {c.owned_companies_count}
                          </span>
                        ) : (
                          <span className="text-gray-600">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-center">
                        {c.member_companies_count > 0 ? (
                          <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-orange-900 text-orange-200 text-xs font-semibold">
                            {c.member_companies_count}
                          </span>
                        ) : (
                          <span className="text-gray-600">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-center text-gray-300 font-medium">
                        {c.total_profiles}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400 whitespace-nowrap">
                        {fmt(c.created_at)}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400 whitespace-nowrap">
                        {fmt(c.last_sign_in_at)}
                      </td>
                      <td className="px-4 py-3 text-right whitespace-nowrap">
                        <button
                          onClick={() => setSelectedId(c.id)}
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

      <AdminCustomerDetailModal
        customerId={selectedId}
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
  onChange: (v: BoolFilter) => void
  options: { value: string; label: string }[]
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as BoolFilter)}
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
