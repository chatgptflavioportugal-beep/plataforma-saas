import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useTenant } from '@/contexts/TenantContext'
import type { CompanyMember, Invitation } from '@/types'

const ROLE_LABELS: Record<string, string> = {
  owner: 'Proprietário',
  admin: 'Administrador',
  member: 'Membro',
  finance: 'Financeiro',
}

const ROLE_COLORS: Record<string, string> = {
  owner: 'bg-amber-50 text-amber-700',
  admin: 'bg-blue-50 text-blue-700',
  member: 'bg-gray-100 text-gray-600',
  finance: 'bg-emerald-50 text-emerald-700',
}

function RoleBadge({ role }: { role: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${ROLE_COLORS[role] ?? 'bg-gray-100 text-gray-600'}`}>
      {ROLE_LABELS[role] ?? role}
    </span>
  )
}

function StatusBadge({ status }: { status: string }) {
  if (status === 'pending') {
    return <span className="inline-flex items-center rounded-full bg-yellow-50 text-yellow-700 px-2 py-0.5 text-xs font-medium">Pendente</span>
  }
  if (status === 'expired') {
    return <span className="inline-flex items-center rounded-full bg-red-50 text-red-600 px-2 py-0.5 text-xs font-medium">Expirado</span>
  }
  if (status === 'cancelled') {
    return <span className="inline-flex items-center rounded-full bg-gray-100 text-gray-500 px-2 py-0.5 text-xs font-medium">Cancelado</span>
  }
  return <span className="inline-flex items-center rounded-full bg-green-50 text-green-700 px-2 py-0.5 text-xs font-medium">Aceito</span>
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })
}

// ─── Modal de convite ─────────────────────────────────────────────────────────

function InviteModal({
  tenantId,
  onClose,
}: {
  tenantId: string
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<'member' | 'admin' | 'finance'>('member')
  const [inviteLink, setInviteLink] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const mutation = useMutation({
    mutationFn: async () => {
      const { data } = await api.post(`/api/v1/tenants/${tenantId}/invitations`, { email, role })
      return data as { invite_link: string }
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['invitations', tenantId] })
      setInviteLink(data.invite_link)
    },
  })

  async function handleCopy() {
    if (!inviteLink) return
    await navigator.clipboard.writeText(inviteLink)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (inviteLink) {
    return (
      <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Convite enviado!</h3>
          <p className="text-sm text-gray-500">
            O convite foi enviado para <strong>{email}</strong>. Você também pode compartilhar o link abaixo:
          </p>
          <div className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 p-3">
            <span className="flex-1 text-xs text-gray-600 truncate">{inviteLink}</span>
            <button
              onClick={handleCopy}
              className="flex-shrink-0 text-xs font-medium text-primary-600 hover:text-primary-700"
            >
              {copied ? 'Copiado!' : 'Copiar'}
            </button>
          </div>
          <button
            onClick={onClose}
            className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
          >
            Fechar
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900">Convidar usuário</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">E-mail</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="usuario@empresa.com"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              disabled={mutation.isPending}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Papel</label>
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as 'member' | 'admin' | 'finance')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              disabled={mutation.isPending}
            >
              <option value="member">Membro</option>
              <option value="admin">Administrador</option>
              <option value="finance">Financeiro</option>
            </select>
          </div>

          {mutation.isError && (
            <p className="text-sm text-red-600">
              {(mutation.error as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Erro ao enviar convite'}
            </p>
          )}
        </div>

        <div className="flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancelar
          </button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !email.trim()}
            className="flex-1 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {mutation.isPending ? 'Enviando...' : 'Enviar convite'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export function CompanyMembersPage() {
  const { currentTenant, activeTenantId } = useTenant()
  const queryClient = useQueryClient()
  const [showInviteModal, setShowInviteModal] = useState(false)

  const tenantId = activeTenantId!
  const myRole = currentTenant?.role

  const canManage = myRole === 'owner' || myRole === 'admin'

  const { data: members = [], isLoading: loadingMembers } = useQuery({
    queryKey: ['members', tenantId],
    queryFn: async () => {
      const { data } = await api.get<CompanyMember[]>(`/api/v1/tenants/${tenantId}/members`)
      return data
    },
    enabled: !!tenantId,
  })

  const { data: invitations = [], isLoading: loadingInvitations } = useQuery({
    queryKey: ['invitations', tenantId],
    queryFn: async () => {
      const { data } = await api.get<Invitation[]>(`/api/v1/tenants/${tenantId}/invitations`)
      return data
    },
    enabled: !!tenantId && canManage,
  })

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) =>
      api.delete(`/api/v1/tenants/${tenantId}/members/${userId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members', tenantId] }),
  })

  const cancelInvitationMutation = useMutation({
    mutationFn: (invId: string) =>
      api.delete(`/api/v1/tenants/${tenantId}/invitations/${invId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invitations', tenantId] }),
  })

  const pendingInvitations = invitations.filter((i) => i.status === 'pending')

  return (
    <div className="space-y-8 max-w-3xl">
      {showInviteModal && (
        <InviteModal tenantId={tenantId} onClose={() => setShowInviteModal(false)} />
      )}

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Membros</h1>
          <p className="text-sm text-gray-500 mt-1">{currentTenant?.tenant?.name}</p>
        </div>
        {canManage && (
          <button
            onClick={() => setShowInviteModal(true)}
            className="flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors shadow-sm"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            Convidar usuário
          </button>
        )}
      </div>

      {/* Lista de membros */}
      <div className="rounded-xl bg-white border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100">
          <h2 className="text-sm font-semibold text-gray-900">
            Membros ativos
            <span className="ml-2 text-gray-400 font-normal">({members.length})</span>
          </h2>
        </div>

        {loadingMembers ? (
          <div className="flex justify-center py-10">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
          </div>
        ) : members.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-400">Nenhum membro encontrado.</div>
        ) : (
          <ul className="divide-y divide-gray-50">
            {members.map((member) => (
              <li key={member.user_id} className="flex items-center gap-4 px-6 py-4">
                <div className="h-9 w-9 rounded-full bg-gray-100 flex items-center justify-center flex-shrink-0 text-sm font-semibold text-gray-500">
                  {(member.full_name ?? member.email ?? '?')[0].toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">
                    {member.full_name ?? '—'}
                  </p>
                  <p className="text-xs text-gray-400 truncate">{member.email ?? '—'}</p>
                </div>
                <RoleBadge role={member.role} />
                {canManage && member.role !== 'owner' && (
                  <button
                    onClick={() => {
                      if (confirm(`Remover ${member.full_name ?? member.email} da empresa?`)) {
                        removeMemberMutation.mutate(member.user_id)
                      }
                    }}
                    disabled={removeMemberMutation.isPending}
                    className="text-xs text-red-500 hover:text-red-700 disabled:opacity-50 flex-shrink-0"
                  >
                    Remover
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Convites pendentes */}
      {canManage && (
        <div className="rounded-xl bg-white border border-gray-100 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="text-sm font-semibold text-gray-900">
              Convites pendentes
              <span className="ml-2 text-gray-400 font-normal">({pendingInvitations.length})</span>
            </h2>
          </div>

          {loadingInvitations ? (
            <div className="flex justify-center py-10">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
            </div>
          ) : pendingInvitations.length === 0 ? (
            <div className="py-10 text-center text-sm text-gray-400">Nenhum convite pendente.</div>
          ) : (
            <ul className="divide-y divide-gray-50">
              {pendingInvitations.map((inv) => (
                <li key={inv.id} className="flex items-center gap-4 px-6 py-4">
                  <div className="h-9 w-9 rounded-full bg-yellow-50 flex items-center justify-center flex-shrink-0">
                    <svg className="h-4 w-4 text-yellow-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{inv.email}</p>
                    <p className="text-xs text-gray-400">Expira em {formatDate(inv.expires_at)}</p>
                  </div>
                  <RoleBadge role={inv.role} />
                  <StatusBadge status={inv.status} />
                  <button
                    onClick={() => {
                      if (confirm(`Cancelar convite para ${inv.email}?`)) {
                        cancelInvitationMutation.mutate(inv.id)
                      }
                    }}
                    disabled={cancelInvitationMutation.isPending}
                    className="text-xs text-gray-400 hover:text-gray-600 disabled:opacity-50 flex-shrink-0"
                  >
                    Cancelar
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
