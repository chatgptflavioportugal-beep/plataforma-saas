import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

interface AdminStats {
  total_tenants: number
  active_tenants: number
  trial_tenants: number
  suspended_tenants: number
  total_users: number
  total_pdf_jobs: number
}

export function AdminDashboardPage() {
  const { data: stats, isError } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: async () => {
      const { data } = await api.get<AdminStats>('/api/v1/admin/stats')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  const cards = [
    { label: 'Total de empresas', value: stats?.total_tenants ?? '—' },
    { label: 'Empresas ativas', value: stats?.active_tenants ?? '—' },
    { label: 'Em trial', value: stats?.trial_tenants ?? '—' },
    { label: 'Suspensas', value: stats?.suspended_tenants ?? '—' },
    { label: 'Total de usuários', value: stats?.total_users ?? '—' },
    { label: 'Jobs de PDF', value: stats?.total_pdf_jobs ?? '—' },
  ]

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold text-white">Dashboard Admin</h1>

      {isError && (
        <p className="text-sm text-red-400">Erro ao carregar estatísticas. Verifique se o backend está rodando.</p>
      )}

      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        {cards.map((card) => (
          <div key={card.label} className="rounded-xl bg-gray-800 border border-gray-700 p-5">
            <p className="text-sm text-gray-400">{card.label}</p>
            <p className="mt-2 text-3xl font-bold text-white">{card.value}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
