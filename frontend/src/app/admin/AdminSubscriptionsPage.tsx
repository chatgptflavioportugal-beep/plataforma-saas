import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'

interface AdminSubscription {
  id: string
  tenant_name: string
  slug: string
  plan_name: string
  plan_code: string
  status: string
  billing_type: string
  plan_version: number
  trial_start: string | null
  trial_end: string | null
  current_period_start: string | null
  current_period_end: string | null
  contracted_price_monthly: number | null
  contracted_price_annual: number | null
  created_at: string
}

const STATUS_COLORS: Record<string, string> = {
  trial: 'bg-blue-900 text-blue-200',
  active: 'bg-green-900 text-green-200',
  past_due: 'bg-yellow-900 text-yellow-200',
  suspended: 'bg-red-900 text-red-200',
  cancelled: 'bg-gray-800 text-gray-400',
}

const STATUS_LABELS: Record<string, string> = {
  trial: 'Trial',
  active: 'Ativo',
  past_due: 'Em atraso',
  suspended: 'Suspenso',
  cancelled: 'Cancelado',
}

function brl(v: number | null) {
  if (v == null) return '—'
  if (v === 0) return 'Grátis'
  return `R$ ${v.toFixed(2).replace('.', ',')}`
}

export function AdminSubscriptionsPage() {
  const { data: subs = [], isLoading } = useQuery({
    queryKey: ['admin-subscriptions'],
    queryFn: async () => {
      const { data } = await api.get<AdminSubscription[]>('/api/v1/admin/subscriptions')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  const totals = {
    active: subs.filter((s) => s.status === 'active').length,
    trial: subs.filter((s) => s.status === 'trial').length,
    past_due: subs.filter((s) => s.status === 'past_due').length,
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Assinaturas</h1>
        <p className="mt-1 text-sm text-gray-400">Todas as assinaturas ativas e históricas da plataforma.</p>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {[
          { label: 'Ativas', value: totals.active, color: 'text-green-400' },
          { label: 'Em Trial', value: totals.trial, color: 'text-blue-400' },
          { label: 'Em Atraso', value: totals.past_due, color: 'text-yellow-400' },
        ].map((c) => (
          <div key={c.label} className="rounded-xl bg-gray-800 border border-gray-700 px-5 py-4">
            <p className="text-xs text-gray-400">{c.label}</p>
            <p className={`mt-1 text-2xl font-bold ${c.color}`}>{c.value}</p>
          </div>
        ))}
      </div>

      {isLoading ? (
        <p className="text-gray-400">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700">
            <span className="text-sm text-gray-400">
              {subs.length} assinatura{subs.length !== 1 ? 's' : ''}
            </span>
          </div>
          <table className="min-w-full divide-y divide-gray-700">
            <thead>
              <tr>
                {['Empresa', 'Plano', 'Status', 'Faturamento', 'Preço Contratado', 'Vencimento', 'Criado em'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700">
              {subs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-10 text-center text-sm text-gray-500">
                    Nenhuma assinatura encontrada.
                  </td>
                </tr>
              ) : (
                subs.map((s) => {
                  const price = s.billing_type === 'annual'
                    ? s.contracted_price_annual
                    : s.contracted_price_monthly
                  const periodEnd = s.status === 'trial' ? s.trial_end : s.current_period_end
                  return (
                    <tr key={s.id} className="hover:bg-gray-750">
                      <td className="px-4 py-3">
                        <p className="text-sm text-white font-medium">{s.tenant_name}</p>
                        <p className="text-xs text-gray-500">{s.slug}</p>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-sm text-gray-300">{s.plan_name}</p>
                        <p className="text-xs text-gray-500">v{s.plan_version}</p>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                          STATUS_COLORS[s.status] ?? 'bg-gray-700 text-gray-300'
                        }`}>
                          {STATUS_LABELS[s.status] ?? s.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400">
                        {s.billing_type === 'annual' ? 'Anual' : 'Mensal'}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400">{brl(price)}</td>
                      <td className="px-4 py-3 text-sm text-gray-400">
                        {periodEnd ? new Date(periodEnd).toLocaleDateString('pt-BR') : '—'}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400">
                        {new Date(s.created_at).toLocaleDateString('pt-BR')}
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
