import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/shared/services/adminApi'

interface AdminStats {
  total_tenants: number
  active_tenants: number
  trial_tenants: number
  suspended_tenants: number
  total_users: number
  total_pdf_jobs: number
  users_with_individual_profile: number
  users_without_individual_profile: number
  total_member_links: number
  users_in_multiple_companies: number
  companies_without_extra_members: number
  companies_with_invited_members: number
}

function StatCard({ label, value, sub }: { label: string; value: number | string; sub?: string }) {
  return (
    <div className="rounded-xl bg-gray-800 border border-gray-700 p-5">
      <p className="text-sm text-gray-400">{label}</p>
      <p className="mt-2 text-3xl font-bold text-white">{value}</p>
      {sub && <p className="mt-1 text-xs text-gray-500">{sub}</p>}
    </div>
  )
}

export function AdminDashboardPage() {
  const { data: stats, isError } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: async () => {
      const { data } = await adminApi.get<AdminStats>('/api/v1/admin/stats')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  const s = stats

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold text-white">Dashboard Admin</h1>

      {isError && (
        <p className="text-sm text-red-400">Erro ao carregar estatísticas. Verifique se o backend está rodando.</p>
      )}

      {/* Empresas */}
      <section>
        <p className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">Empresas</p>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <StatCard label="Total de empresas" value={s?.total_tenants ?? '—'} />
          <StatCard label="Empresas ativas" value={s?.active_tenants ?? '—'} />
          <StatCard label="Em trial" value={s?.trial_tenants ?? '—'} />
          <StatCard label="Suspensas" value={s?.suspended_tenants ?? '—'} />
        </div>
      </section>

      {/* Usuários / Perfis */}
      <section>
        <p className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">Usuários e Perfis</p>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          <StatCard label="Total de usuários" value={s?.total_users ?? '—'} />
          <StatCard
            label="Com Perfil Individual"
            value={s?.users_with_individual_profile ?? '—'}
            sub="criaram perfil individual"
          />
          <StatCard
            label="Sem Perfil Individual"
            value={s?.users_without_individual_profile ?? '—'}
            sub="ainda não criaram"
          />
        </div>
      </section>

      {/* Membros e perfis */}
      <section>
        <p className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">Membros e Perfis</p>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          <StatCard
            label="Vínculos de membros"
            value={s?.total_member_links ?? '—'}
            sub="membros em empresas (não-donos)"
          />
          <StatCard
            label="Usuários em múltiplas empresas"
            value={s?.users_in_multiple_companies ?? '—'}
            sub="participam de mais de 1 empresa"
          />
          <StatCard
            label="Empresas com membros convidados"
            value={s?.companies_with_invited_members ?? '—'}
            sub="tiveram convites aceitos"
          />
          <StatCard
            label="Empresas sem membros extras"
            value={s?.companies_without_extra_members ?? '—'}
            sub="somente o dono"
          />
          <StatCard label="Jobs de PDF" value={s?.total_pdf_jobs ?? '—'} />
        </div>
      </section>
    </div>
  )
}
