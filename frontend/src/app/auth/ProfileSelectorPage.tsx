import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, setActiveTenant } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
import type { UserTenant } from '@/shared/types'

function PersonIcon() {
  return (
    <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  )
}

function BuildingIcon() {
  return (
    <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  )
}

const ROLE_LABELS: Record<string, string> = {
  owner: 'Proprietário',
  admin: 'Administrador',
  member: 'Membro',
  finance: 'Financeiro',
}

export function ProfileSelectorPage() {
  const navigate = useNavigate()
  const { profile, signOut } = useAuth()
  const [isCreatingIndividual, setIsCreatingIndividual] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ['profile-selector-tenants'],
    queryFn: async () => {
      const { data } = await api.get<UserTenant[]>('/api/v1/tenants/mine')
      return data
    },
    retry: false,
  })

  const individual = tenants.find((t) => t.tenant?.type === 'individual')
  const businesses = tenants.filter((t) => t.tenant?.type === 'business')

  function selectProfile(tenantId: string) {
    setActiveTenant(tenantId)
    window.location.href = '/app/dashboard'
  }

  async function handleCreateIndividual() {
    setIsCreatingIndividual(true)
    setCreateError(null)
    try {
      const { data } = await api.post<{ id: string }>('/api/v1/public/individual-tenant')
      setActiveTenant(data.id)
      window.location.href = '/app/dashboard'
    } catch {
      setCreateError('Não foi possível criar o perfil individual. Tente novamente.')
      setIsCreatingIndividual(false)
    }
  }

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-md space-y-6">

        {/* Header */}
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900">Selecione um perfil</h1>
          <p className="mt-1 text-sm text-gray-500">
            Olá, {profile?.full_name ?? 'usuário'}. Em qual perfil deseja trabalhar?
          </p>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
          </div>
        ) : (
          <div className="space-y-3">

            {/* Perfil Individual */}
            {individual ? (
              <button
                onClick={() => selectProfile(individual.tenant_id)}
                className="w-full flex items-center gap-4 rounded-2xl bg-white border border-gray-200 px-5 py-4 text-left shadow-sm hover:border-primary-500 hover:shadow-md transition-all group"
              >
                <div className="flex-shrink-0 h-12 w-12 rounded-xl bg-primary-50 text-primary-600 flex items-center justify-center group-hover:bg-primary-100 transition-colors">
                  <PersonIcon />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-wide text-primary-500 mb-0.5">
                    Perfil Individual
                  </p>
                  <p className="text-base font-semibold text-gray-900 truncate">
                    {individual.tenant?.name}
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">
                    {ROLE_LABELS[individual.role] ?? individual.role}
                    {individual.tenant?.status === 'trial' && (
                      <span className="ml-2 inline-flex items-center rounded-full bg-blue-50 px-2 py-0.5 text-blue-600 font-medium">
                        Trial
                      </span>
                    )}
                  </p>
                </div>
                <svg className="h-5 w-5 text-gray-300 group-hover:text-primary-500 transition-colors flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>
            ) : (
              /* Opção de criar perfil individual — para usuários sem perfil individual */
              <button
                onClick={handleCreateIndividual}
                disabled={isCreatingIndividual}
                className="w-full flex items-center gap-4 rounded-2xl border-2 border-dashed border-primary-200 bg-primary-50/40 px-5 py-4 text-left hover:border-primary-400 hover:bg-primary-50 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <div className="flex-shrink-0 h-12 w-12 rounded-xl bg-primary-100 text-primary-500 flex items-center justify-center">
                  {isCreatingIndividual ? (
                    <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" />
                  ) : (
                    <PersonIcon />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-wide text-primary-400 mb-0.5">
                    Perfil Individual
                  </p>
                  <p className="text-sm font-semibold text-primary-700">
                    {isCreatingIndividual ? 'Criando perfil...' : 'Criar perfil individual'}
                  </p>
                  <p className="text-xs text-primary-400 mt-0.5">
                    Uso pessoal · 14 dias grátis
                  </p>
                </div>
              </button>
            )}

            {/* Empresas existentes */}
            {businesses.length > 0 && (
              <div className="space-y-2">
                {individual && (
                  <p className="text-xs font-semibold uppercase tracking-wide text-gray-400 px-1">
                    Empresas
                  </p>
                )}
                {businesses.map((ut) => (
                  <button
                    key={ut.tenant_id}
                    onClick={() => selectProfile(ut.tenant_id)}
                    className="w-full flex items-center gap-4 rounded-2xl bg-white border border-gray-200 px-5 py-4 text-left shadow-sm hover:border-indigo-500 hover:shadow-md transition-all group"
                  >
                    <div className="flex-shrink-0 h-12 w-12 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center group-hover:bg-indigo-100 transition-colors">
                      <BuildingIcon />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-wide text-indigo-500 mb-0.5">
                        Perfil Empresarial
                      </p>
                      <p className="text-base font-semibold text-gray-900 truncate">
                        {ut.tenant?.name}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {ROLE_LABELS[ut.role] ?? ut.role}
                        {ut.tenant?.status === 'trial' && (
                          <span className="ml-2 inline-flex items-center rounded-full bg-blue-50 px-2 py-0.5 text-blue-600 font-medium">
                            Trial
                          </span>
                        )}
                      </p>
                    </div>
                    <svg className="h-5 w-5 text-gray-300 group-hover:text-indigo-500 transition-colors flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                    </svg>
                  </button>
                ))}
              </div>
            )}

            {/* Criar nova empresa */}
            <button
              onClick={() => navigate('/onboarding', { state: { type: 'business' } })}
              className="w-full flex items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-gray-200 px-5 py-4 text-sm text-gray-400 hover:border-gray-300 hover:text-gray-500 transition-colors"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              Criar nova empresa
            </button>

            {createError && (
              <p className="text-center text-sm text-red-600">{createError}</p>
            )}

          </div>
        )}

        {/* Footer */}
        <div className="text-center">
          <button onClick={handleSignOut} className="text-xs text-gray-400 hover:text-gray-600">
            Sair da conta
          </button>
        </div>

      </div>
    </div>
  )
}
