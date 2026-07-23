import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTenant } from './TenantContext'
import { setActiveTenant } from '@/shared/services/api'
import { profileApi } from '@/shared/services/profileApi'
import type { UserTenant } from '@/shared/types'

function PersonIcon({ className }: { className?: string }) {
  return (
    <svg className={className ?? 'h-4 w-4'} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  )
}

function BuildingIcon({ className }: { className?: string }) {
  return (
    <svg className={className ?? 'h-4 w-4'} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg className="h-3.5 w-3.5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
    </svg>
  )
}

function TenantIcon({ type, className }: { type?: string; className?: string }) {
  return type === 'individual'
    ? <PersonIcon className={className} />
    : <BuildingIcon className={className} />
}

const ROLE_LABELS: Record<string, string> = {
  owner: 'Proprietário',
  admin: 'Admin',
  member: 'Membro',
  finance: 'Financeiro',
}

function ProfileItem({ ut, isActive, onSelect }: { ut: UserTenant; isActive: boolean; onSelect: () => void }) {
  const type = ut.tenant?.type
  const sublabel = type === 'individual'
    ? 'Perfil Individual'
    : ROLE_LABELS[ut.role] ?? 'Empresa'

  return (
    <button
      onClick={onSelect}
      className={`w-full flex items-center gap-3 px-3 py-2.5 text-left transition-colors rounded-lg ${
        isActive
          ? 'bg-primary-50 text-primary-700'
          : 'text-gray-700 hover:bg-gray-50'
      }`}
    >
      <div className={`flex-shrink-0 h-8 w-8 rounded-lg flex items-center justify-center ${
        isActive
          ? type === 'individual' ? 'bg-primary-100 text-primary-600' : 'bg-indigo-100 text-indigo-600'
          : type === 'individual' ? 'bg-gray-100 text-gray-500' : 'bg-gray-100 text-gray-500'
      }`}>
        <TenantIcon type={type} className="h-4 w-4" />
      </div>
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-medium truncate ${isActive ? 'text-primary-700' : 'text-gray-800'}`}>
          {ut.tenant?.name}
        </p>
        <p className="text-xs text-gray-400 truncate">{sublabel}</p>
      </div>
      {isActive && (
        <svg className="h-4 w-4 text-primary-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
        </svg>
      )}
    </button>
  )
}

export function ProfileSwitcher() {
  const { currentTenant, userTenants, activeTenantId, switchTenant, individualTenant } = useTenant()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [isCreatingIndividual, setIsCreatingIndividual] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
        setCreateError(null)
      }
    }
    document.addEventListener('mousedown', handleOutside)
    return () => document.removeEventListener('mousedown', handleOutside)
  }, [])

  const activeTenant = userTenants.find((ut) => ut.tenant_id === activeTenantId)
  const activeType = activeTenant?.tenant?.type

  function handleSwitch(tenantId: string) {
    switchTenant(tenantId)
    setOpen(false)
    navigate('/app/dashboard', { replace: true })
  }

  async function handleCreateIndividual() {
    setIsCreatingIndividual(true)
    setCreateError(null)
    try {
      const { data } = await profileApi.post<{ id: string }>('/api/v1/public/individual-tenant')
      setActiveTenant(data.id)
      window.location.href = '/app/dashboard'
    } catch {
      setCreateError('Não foi possível criar o perfil individual.')
      setIsCreatingIndividual(false)
    }
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 hover:border-gray-300 transition-colors"
      >
        <div className={`h-6 w-6 rounded-md flex items-center justify-center flex-shrink-0 ${
          activeType === 'individual' ? 'bg-primary-100 text-primary-600' : 'bg-indigo-100 text-indigo-600'
        }`}>
          <TenantIcon type={activeType} className="h-3.5 w-3.5" />
        </div>
        <span className="max-w-[140px] truncate">
          {currentTenant?.tenant?.name ?? 'Selecionar perfil'}
        </span>
        <ChevronIcon />
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-2 w-72 rounded-2xl bg-white border border-gray-200 shadow-xl z-50 overflow-hidden">
          <div className="px-3 py-2 border-b border-gray-100">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Seus perfis</p>
          </div>

          <div className="p-2 space-y-0.5 max-h-80 overflow-y-auto">
            {userTenants.map((ut) => (
              <ProfileItem
                key={ut.tenant_id}
                ut={ut}
                isActive={ut.tenant_id === activeTenantId}
                onSelect={() => handleSwitch(ut.tenant_id)}
              />
            ))}
          </div>

          <div className="border-t border-gray-100 p-2 space-y-0.5">
            <button
              onClick={() => { setOpen(false); navigate('/onboarding', { state: { type: 'business' } }) }}
              className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-gray-500 hover:bg-gray-50 transition-colors"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              Criar nova empresa
            </button>

            {!individualTenant && (
              <button
                onClick={handleCreateIndividual}
                disabled={isCreatingIndividual}
                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-primary-600 hover:bg-primary-50 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isCreatingIndividual ? (
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-primary-600 border-t-transparent flex-shrink-0" />
                ) : (
                  <PersonIcon className="h-4 w-4 flex-shrink-0" />
                )}
                {isCreatingIndividual ? 'Criando perfil...' : 'Criar perfil individual'}
              </button>
            )}

            {createError && (
              <p className="px-3 text-xs text-red-600">{createError}</p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
