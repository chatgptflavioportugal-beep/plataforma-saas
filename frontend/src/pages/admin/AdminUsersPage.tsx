import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

interface AdminUser {
  id: string
  email: string
  full_name: string | null
  system_role: string
  is_active: boolean
  tenant_count: number
  created_at: string
}

export function AdminUsersPage() {
  const { data: users = [] } = useQuery({
    queryKey: ['admin-users'],
    queryFn: async () => {
      const { data } = await api.get<AdminUser[]>('/api/v1/admin/users')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Usuários</h1>

      <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
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
            {users.map((u) => (
              <tr key={u.id}>
                <td className="px-4 py-3 text-sm text-white font-medium">{u.full_name ?? '—'}</td>
                <td className="px-4 py-3 text-sm text-gray-400">{u.email}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${u.system_role === 'SUPER_ADMIN' ? 'bg-red-900 text-red-200' : 'bg-gray-700 text-gray-400'}`}>
                    {u.system_role}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm text-gray-400">{u.tenant_count}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${u.is_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'}`}>
                    {u.is_active ? 'Ativo' : 'Inativo'}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm text-gray-400">
                  {new Date(u.created_at).toLocaleDateString('pt-BR')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
