import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { profileApi } from '@/shared/services/profileApi'
import { useTenant } from '@/core/workspaces/TenantContext'
import type { CompanyMember, Invitation, AccessLevel } from '@/shared/types'

function AccessLevelBadge({ name }: { name: string | null }) {
  if (!name) return null
  return (
    <span className="inline-flex items-center rounded-full bg-indigo-50 text-indigo-700 px-2 py-0.5 text-xs font-medium">
      {name}
    </span>
  )
}

function OwnerBadge() {
  return (
    <span className="inline-flex items-center rounded-full bg-amber-50 text-amber-700 px-2 py-0.5 text-xs font-medium">
      Proprietário
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
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [accessLevelId, setAccessLevelId] = useState('')
  const [sent, setSent] = useState(false)

  const { data: accessLevels = [], isLoading: loadingLevels } = useQuery({
    queryKey: ['access-levels', tenantId],
    queryFn: async () => {
      const { data } = await profileApi.get<AccessLevel[]>(`/api/v1/tenants/${tenantId}/access-levels`)
      return data
    },
  })

  const activeAccessLevels = accessLevels.filter((al) => al.status === 'ACTIVE')

  const mutation = useMutation({
    mutationFn: async () => {
      await profileApi.post(`/api/v1/tenants/${tenantId}/invitations`, { email, accessLevelId })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invitations', tenantId] })
      setSent(true)
    },
  })

  if (sent) {
    return (
      <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-4">
          <div className="flex flex-col items-center gap-3 py-2">
            <div className="h-12 w-12 rounded-full bg-green-50 flex items-center justify-center">
              <svg className="h-6 w-6 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h3 className="text-lg font-semibold text-gray-900">Convite enviado!</h3>
            <p className="text-sm text-gray-500 text-center">
              O convite foi enviado para <strong>{email}</strong>.
            </p>
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

        {loadingLevels ? (
          <div className="flex justify-center py-6">
            <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
          </div>
        ) : activeAccessLevels.length === 0 ? (
          /* Estado vazio: sem níveis de acesso */
          <div className="rounded-xl bg-amber-50 border border-amber-100 p-5 text-center space-y-3">
            <div className="h-10 w-10 rounded-full bg-amber-100 flex items-center justify-center mx-auto">
              <svg className="h-5 w-5 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
              </svg>
            </div>
            <div>
              <p className="text-sm font-semibold text-gray-900">Nenhum nível de acesso cadastrado</p>
              <p className="text-sm text-gray-600 mt-1">
                Antes de convidar membros, crie um nível de acesso para definir quais módulos e serviços eles poderão utilizar.
              </p>
            </div>
            <button
              onClick={() => {
                onClose()
                navigate('/app/settings/access-levels')
              }}
              className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
            >
              Criar Nível de Acesso
            </button>
          </div>
        ) : (
          /* Formulário de convite */
          <>
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
                <label className="block text-sm font-medium text-gray-700 mb-1">Nível de Acesso</label>
                <select
                  value={accessLevelId}
                  onChange={(e) => setAccessLevelId(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  disabled={mutation.isPending}
                >
                  <option value="">Selecione um nível de acesso...</option>
                  {activeAccessLevels.map((al) => (
                    <option key={al.id} value={al.id}>
                      {al.name}
                    </option>
                  ))}
                </select>
              </div>

              {mutation.isError && (
                <p className="text-sm text-red-600">
                  {(mutation.error as { response?: { data?: { error?: string } } })?.response?.data?.error ??
                    'Erro ao enviar convite'}
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
                disabled={mutation.isPending || !email.trim() || !accessLevelId}
                className="flex-1 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
              >
                {mutation.isPending ? 'Enviando...' : 'Enviar convite'}
              </button>
            </div>
          </>
        )}
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
      const { data } = await profileApi.get<CompanyMember[]>(`/api/v1/tenants/${tenantId}/members`)
      return data
    },
    enabled: !!tenantId,
    staleTime: 0,
  })

  const { data: invitations = [], isLoading: loadingInvitations } = useQuery({
    queryKey: ['invitations', tenantId],
    queryFn: async () => {
      const { data } = await profileApi.get<Invitation[]>(`/api/v1/tenants/${tenantId}/invitations`)
      return data
    },
    enabled: !!tenantId && canManage,
    staleTime: 0,
  })

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) =>
      profileApi.delete(`/api/v1/tenants/${tenantId}/members/${userId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members', tenantId] }),
  })

  const cancelInvitationMutation = useMutation({
    mutationFn: (invId: string) =>
      profileApi.delete(`/api/v1/tenants/${tenantId}/invitations/${invId}`),
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
                {member.role === 'owner' ? (
                  <OwnerBadge />
                ) : (
                  <AccessLevelBadge name={member.access_level_name} />
                )}
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
                  <AccessLevelBadge name={inv.access_level_name} />
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
