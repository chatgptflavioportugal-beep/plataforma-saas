import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/shared/services/adminApi'

interface TenantMember {
  full_name: string | null
  email: string
  role: string
  is_active: boolean
  joined_at: string
}

interface PendingInvitation {
  email: string
  role: string
  created_at: string
  invited_by_name: string | null
}

interface TenantDetail {
  id: string
  name: string
  slug: string
  status: string
  created_at: string
  trial_ends_at: string | null
  plan_name: string | null
  plan_code: string | null
  subscription_status: string | null
  trial_start: string | null
  trial_end: string | null
  current_period_start: string | null
  current_period_end: string | null
  billing_type: string | null
  owner_name: string | null
  owner_email: string | null
  owner_joined_at: string | null
  members: TenantMember[]
  pending_invitations: PendingInvitation[]
}

const STATUS_COLOR: Record<string, string> = {
  trial: 'bg-blue-900 text-blue-200',
  active: 'bg-green-900 text-green-200',
  suspended: 'bg-red-900 text-red-200',
  cancelled: 'bg-gray-700 text-gray-400',
}

const STATUS_LABEL: Record<string, string> = {
  trial: 'Trial',
  active: 'Ativa',
  suspended: 'Suspensa',
  cancelled: 'Cancelada',
}

const ROLE_LABEL: Record<string, string> = {
  owner: 'Dono',
  admin: 'Admin',
  member: 'Membro',
  finance: 'Financeiro',
}

const ROLE_COLOR: Record<string, string> = {
  owner: 'bg-purple-900 text-purple-200',
  admin: 'bg-blue-900 text-blue-200',
  member: 'bg-gray-700 text-gray-300',
  finance: 'bg-green-900 text-green-200',
}

const BILLING_LABEL: Record<string, string> = {
  monthly: 'Mensal',
  annual: 'Anual',
}

function fmt(date: string | null | undefined) {
  if (!date) return '—'
  return new Date(date).toLocaleDateString('pt-BR')
}

interface Props {
  tenantId: string | null
  onClose: () => void
}

export function AdminTenantDetailModal({ tenantId, onClose }: Props) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-tenant-detail', tenantId],
    queryFn: async () => {
      const { data } = await adminApi.get<TenantDetail>(`/api/v1/admin/tenants/${tenantId}`)
      return data
    },
    enabled: !!tenantId,
    retry: false,
    staleTime: 30_000,
  })

  if (!tenantId) return null

  return (
    <>
      <div
        className="fixed inset-0 bg-black/60 z-40 flex items-center justify-center p-4"
        onClick={onClose}
      >
        <div
          className="w-full max-w-2xl max-h-[90vh] bg-gray-900 border border-gray-700 rounded-2xl z-50 flex flex-col shadow-2xl"
          onClick={(e) => e.stopPropagation()}
        >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">Detalhe da Empresa</h2>
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

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">
          {isLoading && <p className="text-sm text-gray-400">Carregando...</p>}
          {isError && <p className="text-sm text-red-400">Erro ao carregar dados da empresa.</p>}

          {data && (
            <>
              {/* Dados da Empresa */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Dados da Empresa
                </h3>
                <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-2 text-sm">
                  <Row label="Nome" value={data.name} />
                  <Row label="Slug" value={data.slug} />
                  <Row
                    label="Status"
                    value={
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLOR[data.status] ?? 'bg-gray-700 text-gray-400'}`}>
                        {STATUS_LABEL[data.status] ?? data.status}
                      </span>
                    }
                  />
                  <Row label="Plano" value={data.plan_name ?? '—'} />
                  {data.subscription_status && (
                    <Row
                      label="Status assinatura"
                      value={
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLOR[data.subscription_status] ?? 'bg-gray-700 text-gray-400'}`}>
                          {STATUS_LABEL[data.subscription_status] ?? data.subscription_status}
                        </span>
                      }
                    />
                  )}
                  {data.billing_type && (
                    <Row label="Cobrança" value={BILLING_LABEL[data.billing_type] ?? data.billing_type} />
                  )}
                  {data.trial_end && (
                    <Row label="Trial até" value={fmt(data.trial_end)} />
                  )}
                  {data.current_period_end && (
                    <Row label="Período atual até" value={fmt(data.current_period_end)} />
                  )}
                  <Row label="Criada em" value={fmt(data.created_at)} />
                </div>
              </section>

              {/* Proprietário */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Proprietário
                </h3>
                <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 space-y-2 text-sm">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-purple-900 text-purple-200">
                      Dono
                    </span>
                  </div>
                  <Row label="Nome" value={data.owner_name ?? '—'} />
                  <Row label="E-mail" value={data.owner_email ?? '—'} />
                  <Row label="Desde" value={fmt(data.owner_joined_at)} />
                </div>
              </section>

              {/* Membros */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Membros ({data.members.length})
                </h3>
                {data.members.length === 0 ? (
                  <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm text-gray-500 italic">
                    Nenhum membro.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {data.members.map((m, i) => (
                      <div key={i} className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm space-y-2">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-medium text-white">{m.full_name ?? '—'}</span>
                          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${ROLE_COLOR[m.role] ?? 'bg-gray-700 text-gray-300'}`}>
                            {ROLE_LABEL[m.role] ?? m.role}
                          </span>
                          {!m.is_active && (
                            <span className="inline-flex rounded-full px-2 py-0.5 text-xs font-medium bg-gray-700 text-gray-400">
                              Inativo
                            </span>
                          )}
                        </div>
                        <Row label="E-mail" value={m.email} />
                        <Row label="Desde" value={fmt(m.joined_at)} />
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* Convites pendentes */}
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-gray-500 mb-3">
                  Convites Pendentes ({data.pending_invitations.length})
                </h3>
                {data.pending_invitations.length === 0 ? (
                  <div className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm text-gray-500 italic">
                    Nenhum convite pendente.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {data.pending_invitations.map((inv, i) => (
                      <div key={i} className="rounded-xl bg-gray-800 border border-gray-700 p-4 text-sm space-y-2">
                        <Row label="E-mail" value={inv.email} />
                        <Row
                          label="Função"
                          value={
                            <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${ROLE_COLOR[inv.role] ?? 'bg-gray-700 text-gray-300'}`}>
                              {ROLE_LABEL[inv.role] ?? inv.role}
                            </span>
                          }
                        />
                        <Row label="Convidado por" value={inv.invited_by_name ?? '—'} />
                        <Row label="Enviado em" value={fmt(inv.created_at)} />
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </>
          )}
        </div>
        </div>
      </div>
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
