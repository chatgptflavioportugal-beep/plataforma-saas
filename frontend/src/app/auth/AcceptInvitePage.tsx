import { useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { api, setActiveTenant } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
import type { InvitationPreview } from '@/shared/types'

const ROLE_LABELS: Record<string, string> = {
  admin: 'Administrador',
  member: 'Membro',
  finance: 'Financeiro',
}

function resolveAccessLabel(preview: { access_level_name: string | null; role: string }): string {
  return preview.access_level_name ?? ROLE_LABELS[preview.role] ?? preview.role
}

export function AcceptInvitePage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { user, isLoading: authLoading, signOut } = useAuth()
  const token = searchParams.get('token') ?? ''

  const [accepted, setAccepted] = useState(false)
  const [tenantId, setTenantId] = useState<string | null>(null)

  const { data: preview, isLoading, isError } = useQuery({
    queryKey: ['invite-preview', token],
    queryFn: async () => {
      const { data } = await api.get<InvitationPreview>(`/api/v1/public/invitations/${token}`)
      return data
    },
    enabled: !!token,
    retry: false,
  })

  const acceptMutation = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<{ tenant_id: string; role: string }>(
        `/api/v1/invitations/${token}/accept`
      )
      return data
    },
    onSuccess: (data) => {
      setTenantId(data.tenant_id)
      setAccepted(true)
    },
  })

  function handleGoToDashboard() {
    if (tenantId) {
      setActiveTenant(tenantId)
    }
    window.location.href = '/app/dashboard'
  }

  async function handleSignOutAndSwitch() {
    await signOut()
    navigate(`/login?returnUrl=${encodeURIComponent(window.location.pathname + window.location.search)}`)
  }

  const emailMismatch =
    !!user && !!preview &&
    user.email?.toLowerCase() !== preview.email.toLowerCase()

  if (!token) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="text-center space-y-2">
          <p className="text-gray-500">Link de convite inválido.</p>
          <button onClick={() => navigate('/app/dashboard')} className="text-sm text-primary-600 hover:underline">
            Ir para o dashboard
          </button>
        </div>
      </div>
    )
  }

  if (authLoading || isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="w-full max-w-sm bg-white rounded-2xl border border-gray-200 shadow-sm p-8 text-center space-y-4">
          <div className="h-12 w-12 rounded-full bg-red-50 flex items-center justify-center mx-auto">
            <svg className="h-6 w-6 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-gray-900">Convite inválido ou expirado</h2>
          <p className="text-sm text-gray-500">
            Este link de convite não é mais válido. Peça ao administrador da empresa para enviar um novo convite.
          </p>
          <button
            onClick={() => navigate('/app/dashboard')}
            className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
          >
            Ir para o dashboard
          </button>
        </div>
      </div>
    )
  }

  if (accepted) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="w-full max-w-sm bg-white rounded-2xl border border-gray-200 shadow-sm p-8 text-center space-y-4">
          <div className="h-12 w-12 rounded-full bg-green-50 flex items-center justify-center mx-auto">
            <svg className="h-6 w-6 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-gray-900">Convite aceito!</h2>
          <p className="text-sm text-gray-500">
            Você agora é membro de <strong>{preview?.tenant_name}</strong>. A empresa aparecerá no seu seletor de perfis.
          </p>
          <button
            onClick={handleGoToDashboard}
            className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
          >
            Acessar dashboard
          </button>
        </div>
      </div>
    )
  }

  // Convite expirado ou cancelado (preview sem ação disponível)
  const isExpiredOrCancelled = preview?.status === 'expired' || preview?.status === 'cancelled'
  const isAlreadyAccepted = preview?.status === 'accepted'

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm space-y-6">

        {/* Card do convite */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8 space-y-6">
          <div className="text-center space-y-1">
            <div className="h-12 w-12 rounded-full bg-indigo-50 flex items-center justify-center mx-auto mb-3">
              <svg className="h-6 w-6 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
                <path strokeLinecap="round" strokeLinejoin="round"
                  d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <h1 className="text-xl font-bold text-gray-900">Convite para empresa</h1>
          </div>

          {preview && (
            <div className="rounded-xl bg-gray-50 border border-gray-100 p-4 space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Empresa</span>
                <span className="font-semibold text-gray-900">{preview.tenant_name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Nível de Acesso</span>
                <span className="font-medium text-gray-800">{resolveAccessLabel(preview)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Enviado para</span>
                <span className="font-medium text-gray-800 truncate max-w-[180px]">{preview.email}</span>
              </div>
            </div>
          )}

          {isExpiredOrCancelled && (
            <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700">
              Este convite {preview?.status === 'expired' ? 'expirou' : 'foi cancelado'}.
              Peça ao administrador para enviar um novo convite.
            </div>
          )}

          {isAlreadyAccepted && (
            <div className="rounded-xl bg-green-50 border border-green-100 px-4 py-3 text-sm text-green-700">
              Este convite já foi aceito anteriormente.
            </div>
          )}

          {acceptMutation.isError && !emailMismatch && (
            <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700">
              {(acceptMutation.error as { response?: { data?: { error?: string } } })?.response?.data?.error
                ?? 'Não foi possível aceitar o convite. Tente novamente.'}
            </div>
          )}

          {/* Ações */}
          {!isExpiredOrCancelled && !isAlreadyAccepted && (
            <>
              {user && emailMismatch ? (
                <div className="space-y-3">
                  <div className="rounded-xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-800 space-y-2">
                    <p className="font-medium">Este convite foi enviado para outro e-mail.</p>
                    <div className="space-y-1 text-xs">
                      <div className="flex justify-between gap-2">
                        <span className="text-amber-600">Convite enviado para:</span>
                        <span className="font-semibold truncate">{preview?.email}</span>
                      </div>
                      <div className="flex justify-between gap-2">
                        <span className="text-amber-600">Você está conectado como:</span>
                        <span className="font-semibold truncate">{user.email}</span>
                      </div>
                    </div>
                    <p className="text-xs text-amber-700">
                      Para aceitar este convite, saia e entre com o e-mail correto.
                    </p>
                  </div>
                  <button
                    onClick={handleSignOutAndSwitch}
                    className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
                  >
                    Sair e entrar com outro e-mail
                  </button>
                  <button
                    onClick={() => navigate('/app/dashboard')}
                    className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
                  >
                    Voltar
                  </button>
                </div>
              ) : user ? (
                <button
                  onClick={() => acceptMutation.mutate()}
                  disabled={acceptMutation.isPending}
                  className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                >
                  {acceptMutation.isPending ? 'Aceitando...' : 'Aceitar convite'}
                </button>
              ) : (
                <div className="space-y-2">
                  <p className="text-center text-xs text-gray-500">
                    Entre ou crie uma conta usando o e-mail{' '}
                    <strong className="text-gray-700">{preview?.email}</strong>{' '}
                    para aceitar o convite.
                  </p>
                  <button
                    onClick={() => navigate(`/login?returnUrl=${encodeURIComponent(window.location.pathname + window.location.search)}`)}
                    className="w-full rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
                  >
                    Entrar ou criar conta
                  </button>
                  <button
                    onClick={() => navigate(`/register?returnUrl=${encodeURIComponent(window.location.pathname + window.location.search)}`)}
                    className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
                  >
                    Criar conta nova
                  </button>
                </div>
              )}
            </>
          )}

          {(isExpiredOrCancelled || isAlreadyAccepted) && (
            <button
              onClick={() => navigate('/app/dashboard')}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
            >
              Ir para o dashboard
            </button>
          )}
        </div>

      </div>
    </div>
  )
}
