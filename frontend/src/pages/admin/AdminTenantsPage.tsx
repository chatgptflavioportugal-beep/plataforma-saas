import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Tenant } from '@/types'

interface AdminTenant extends Tenant {
  plan_name: string
  subscription_status: string
  user_count: number
}

export function AdminTenantsPage() {
  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ['admin-tenants'],
    queryFn: async () => {
      const { data } = await api.get<AdminTenant[]>('/api/v1/admin/tenants')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  const statusColor: Record<string, string> = {
    trial: 'bg-blue-900 text-blue-200',
    active: 'bg-green-900 text-green-200',
    suspended: 'bg-red-900 text-red-200',
    cancelled: 'bg-gray-800 text-gray-400',
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Empresas</h1>

      {isLoading ? (
        <p className="text-gray-400">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <table className="min-w-full divide-y divide-gray-700">
            <thead>
              <tr>
                {['Nome', 'Slug', 'Plano', 'Status', 'Usuários', 'Criado em'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700">
              {tenants.map((t) => (
                <tr key={t.id} className="hover:bg-gray-750">
                  <td className="px-4 py-3 text-sm text-white font-medium">{t.name}</td>
                  <td className="px-4 py-3 text-sm text-gray-400">{t.slug}</td>
                  <td className="px-4 py-3 text-sm text-gray-300">{t.plan_name}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${statusColor[t.status] ?? 'bg-gray-700 text-gray-300'}`}>
                      {t.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-400">{t.user_count}</td>
                  <td className="px-4 py-3 text-sm text-gray-400">
                    {new Date(t.created_at).toLocaleDateString('pt-BR')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
