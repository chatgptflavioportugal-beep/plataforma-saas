import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/services/api'

interface IndividualProfile {
  id: string
  name: string
  slug: string
  status: string
  created_at: string
}

interface OwnedCompany {
  id: string
  name: string
  slug: string
  status: string
  plan_name: string | null
  plan_code: string | null
  member_count: number
  created_at: string
}

interface MemberCompany {
  id: string
  name: string
  slug: string
  role: string
  link_active: boolean
  joined_at: string
  invited_by_name: string | null
}

interface CustomerDetail {
  id: string
  email: string
  full_name: string | null
  is_active: boolean
  created_at: string
  last_sign_in_at: string | null
  individual_profile: IndividualProfile | null
  owned_companies: OwnedCompany[]
  member_companies: MemberCompany[]
}

const STATUS_COLOR: Record<string, string> = {
  trial: 'bg-blue-900 text-blue-200',
  active: 'bg-green-900 text-green-200',
  suspended: 'bg-red-900 text-red-200',
  cancelled: 'bg-gray-700 text-gray-400',
}

const ROLE_LABEL: Record<string, string> = {
  owner: 'Dono',
  admin: 'Admin',
  member: 'Membro',
  finance: 'Financeiro',
}

function fmt(date: string | null | undefined) {
  if (!date) return '—'
  return new Date(date).toLocaleDateString('pt-BR')
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLOR[status] ?? 'bg-gray-700 text-gray-400'}`}>
      {status}
    </span>
  )
}

interface Props {
  customerId: string | null
  onClose: () => void
}

export function AdminCustomerDetailModal({ customerId, onClose }: Props) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-customer-detail', customerId],
    queryFn: async () => {
      const { data } = await api.get<CustomerDetail>(`/api/v1/admin/customers/${customerId}`)
      return data
    },
    enabled: !!customerId,
    retry: false,
    staleTime: 30_000,
  })

  if (!customerId) return null

  const totalContexts =
    (data?.individual_profile ? 1 : 0) +
    (data?.owned_companies.length ?? 0) +
    (data?.member_companies.length ?? 0)

  return (
    <>
      {/* Overlay */}
      <div
        className="fixed inset-0 bg-black/60 z-40"
        onClick={onClose}
      />

      {/* Panel */}
      <aside className="fixed right-0 top-0 h-full w-[560px] max-w-full bg-gray-900 border-l border-gray-700 z-50 flex flex-col shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">Detalhe do Cliente</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white transition-colors"
            aria-label="Fechar"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">
          {isLoading && (
            <p className="text-sm text-gray-400">Carregando...</p>
          )}

          {isError && (
            <p className="text-sm text-red-400">Erro ao carregar dados do cliente.</p>
          )}

          {data && (
            <>
              {/* Dados principais */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Dados Principais
                </h3>
                <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-2 text-sm">
                  <Row label="Nome" value={data.full_name ?? '—'} />
                  <Row label="E-mail" value={data.email} />
                  <Row
                    label="Status"
                    value={
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${data.is_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'}`}>
                        {data.is_active ? 'Ativo' : 'Inativo'}
                      </span>
                    }
                  />
                  <Row label="Cadastro" value={fmt(data.created_at)} />
                  <Row label="Último acesso" value={fmt(data.last_sign_in_at)} />
                  <Row
                    label="Total de contextos"
                    value={
                      <span className="font-semibold text-white">{totalContexts}</span>
                    }
                  />
                </div>
              </section>

              {/* Perfil Individual */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Perfil Individual
                </h3>
                {data.individual_profile ? (
                  <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-2 text-sm">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-blue-900 text-blue-200">Individual</span>
                    </div>
                    <Row label="Nome" value={data.individual_profile.name} />
                    <Row label="Slug" value={data.individual_profile.slug} />
                    <Row label="Status" value={<StatusBadge status={data.individual_profile.status} />} />
                    <Row label="Criado em" value={fmt(data.individual_profile.created_at)} />
                  </div>
                ) : (
                  <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm text-gray-500 italic">
                    Usuário ainda não criou Perfil Individual.
                  </div>
                )}
              </section>

              {/* Empresas criadas */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Empresas Criadas ({data.owned_companies.length})
                </h3>
                {data.owned_companies.length === 0 ? (
                  <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm text-gray-500 italic">
                    Nenhuma empresa criada.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {data.owned_companies.map((c) => (
                      <div key={c.id} className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-white">{c.name}</span>
                          <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-purple-900 text-purple-200">
                            Dono
                          </span>
                          <StatusBadge status={c.status} />
                        </div>
                        <Row label="Slug" value={c.slug} />
                        <Row label="Plano" value={c.plan_name ?? '—'} />
                        <Row label="Membros" value={String(c.member_count)} />
                        <Row label="Criada em" value={fmt(c.created_at)} />
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* Empresas como membro */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Empresas como Membro ({data.member_companies.length})
                </h3>
                {data.member_companies.length === 0 ? (
                  <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm text-gray-500 italic">
                    Não participa de nenhuma empresa como membro.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {data.member_companies.map((c) => (
                      <div key={c.id} className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-white">{c.name}</span>
                          <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-orange-900 text-orange-200">
                            {ROLE_LABEL[c.role] ?? c.role}
                          </span>
                          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${c.link_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'}`}>
                            {c.link_active ? 'Ativo' : 'Inativo'}
                          </span>
                        </div>
                        <Row label="Slug" value={c.slug} />
                        <Row label="Entrou em" value={fmt(c.joined_at)} />
                        <Row label="Convidado por" value={c.invited_by_name ?? '—'} />
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* Resumo de contextos */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Contextos Disponíveis ({totalContexts})
                </h3>
                <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-2 text-sm">
                  {totalContexts === 0 ? (
                    <p className="text-gray-500 italic">Nenhum contexto disponível.</p>
                  ) : (
                    <>
                      {data.individual_profile && (
                        <div className="flex items-center gap-2">
                          <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-blue-900 text-blue-200">
                            Individual
                          </span>
                          <span className="text-gray-300">{data.individual_profile.name}</span>
                        </div>
                      )}
                      {data.owned_companies.map((c) => (
                        <div key={c.id} className="flex items-center gap-2">
                          <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-purple-900 text-purple-200">
                            Empresa (dono)
                          </span>
                          <span className="text-gray-300">{c.name}</span>
                        </div>
                      ))}
                      {data.member_companies.map((c) => (
                        <div key={c.id} className="flex items-center gap-2">
                          <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-orange-900 text-orange-200">
                            Empresa (membro)
                          </span>
                          <span className="text-gray-300">{c.name}</span>
                        </div>
                      ))}
                    </>
                  )}
                </div>
              </section>
            </>
          )}
        </div>
      </aside>
    </>
  )
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-gray-400 shrink-0">{label}</span>
      <span className="text-gray-200 text-right">{value}</span>
    </div>
  )
}
