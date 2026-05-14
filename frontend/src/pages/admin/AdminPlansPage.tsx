import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Plan } from '@/types'

export function AdminPlansPage() {
  const { data: plans = [] } = useQuery({
    queryKey: ['admin-plans'],
    queryFn: async () => {
      const { data } = await api.get<Plan[]>('/api/v1/admin/plans')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Planos</h1>

      <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
        <table className="min-w-full divide-y divide-gray-700">
          <thead>
            <tr>
              {['Nome', 'Código', 'Preço/mês', 'Max. usuários', 'Max. IA/mês', 'Ativo'].map((h) => (
                <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-700">
            {plans.map((plan) => (
              <tr key={plan.id}>
                <td className="px-4 py-3 text-sm text-white font-medium">{plan.name}</td>
                <td className="px-4 py-3 text-sm text-gray-400 font-mono">{plan.code}</td>
                <td className="px-4 py-3 text-sm text-gray-300">
                  {plan.price_monthly === 0 ? 'Grátis' : `R$ ${plan.price_monthly.toFixed(2)}`}
                </td>
                <td className="px-4 py-3 text-sm text-gray-400">
                  {plan.max_users === -1 ? 'Ilimitado' : plan.max_users}
                </td>
                <td className="px-4 py-3 text-sm text-gray-400">
                  {plan.max_ai_requests_month === -1 ? 'Ilimitado' : plan.max_ai_requests_month}
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${plan.is_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'}`}>
                    {plan.is_active ? 'Ativo' : 'Inativo'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
