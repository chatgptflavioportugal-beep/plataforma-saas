import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'

interface CompanyUser {
  id: string
  email: string
  full_name: string | null
  system_role: string
  is_active: boolean
  tenant_count: number
  created_at: string
}

export function AdminCompanyUsersPage() {
  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin-company-users'],
    queryFn: async () => {
      const { data } = await api.get<CompanyUser[]>('/api/v1/admin/company-users')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Usuários das Empresas</h1>
        <p className="mt-1 text-sm text-gray-400">
          Usuários clientes vinculados às empresas cadastradas na plataforma.
        </p>
      </div>

      {isLoading ? (
        <p className="text-gray-400">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700">
            <span className="text-sm text-gray-400">
              {users.length} usuário{users.length !== 1 ? 's' : ''}
            </span>
          </div>
          <table className="min-w-full divide-y divide-gray-700">
            <thead>
              <tr>
                {['Nome', 'Email', 'Papel', 'Empresas', 'Status', 'Criado em'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700">
              {users.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-sm text-gray-500">
                    Nenhum usuário encontrado.
                  </td>
                </tr>
              ) : (
                users.map((u) => (
                  <tr key={u.id} className="hover:bg-gray-750">
                    <td className="px-4 py-3 text-sm text-white font-medium">{u.full_name ?? '—'}</td>
                    <td className="px-4 py-3 text-sm text-gray-400">{u.email}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-gray-700 text-gray-300">
                        {u.system_role}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-400 text-center">{u.tenant_count}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                        u.is_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'
                      }`}>
                        {u.is_active ? 'Ativo' : 'Inativo'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-400">
                      {new Date(u.created_at).toLocaleDateString('pt-BR')}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
