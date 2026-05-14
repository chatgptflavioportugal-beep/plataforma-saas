import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useLocation } from 'react-router-dom'
import { api, setActiveTenant } from '@/lib/api'
import type { TenantContext as TenantContextType, UserTenant } from '@/types'
import { useAuth } from './AuthContext'

interface TenantContextValue {
  currentTenant: TenantContextType | null
  userTenants: UserTenant[]
  activeTenantId: string | null
  isLoading: boolean
  switchTenant: (tenantId: string) => void
  isTrialExpiringSoon: boolean
  trialDaysRemaining: number | null
}

const TenantCtx = createContext<TenantContextValue | null>(null)

export function TenantProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [activeTenantId, setActiveTenantIdState] = useState<string | null>(
    sessionStorage.getItem('active_tenant_id')
  )

  const { data: userTenants = [], isLoading: tenantsLoading, isFetching: tenantsFetching, isError: tenantsError } = useQuery({
    queryKey: ['user-tenants', user?.id],
    queryFn: async () => {
      const { data } = await api.get<UserTenant[]>('/api/v1/tenants/mine')
      return data
    },
    enabled: !!user,
    retry: false,
  })

  useEffect(() => {
    if (tenantsLoading || tenantsFetching) return
    if (tenantsError) return

    if (userTenants.length === 0) {
      if (location.pathname !== '/onboarding') {
        navigate('/onboarding', { replace: true })
      }
      return
    }

    if (!activeTenantId) {
      switchTenant(userTenants[0].tenant_id)
    }
  }, [tenantsLoading, tenantsFetching, tenantsError, userTenants, activeTenantId])

  const { data: currentTenant, isLoading: tenantLoading } = useQuery({
    queryKey: ['tenant-context', activeTenantId],
    queryFn: async () => {
      const { data } = await api.get<TenantContextType>(`/api/v1/tenants/${activeTenantId}/context`)
      return data
    },
    enabled: !!activeTenantId,
    retry: false,
  })

  function switchTenant(tenantId: string) {
    setActiveTenantIdState(tenantId)
    setActiveTenant(tenantId)
  }

  const trialDaysRemaining = (() => {
    if (!currentTenant?.subscription?.trial_end) return null
    if (currentTenant.subscription.status !== 'trial') return null
    const diff = new Date(currentTenant.subscription.trial_end).getTime() - Date.now()
    return Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)))
  })()

  return (
    <TenantCtx.Provider value={{
      currentTenant: currentTenant ?? null,
      userTenants,
      activeTenantId,
      isLoading: tenantsLoading || tenantLoading,
      switchTenant,
      isTrialExpiringSoon: trialDaysRemaining !== null && trialDaysRemaining <= 7,
      trialDaysRemaining,
    }}>
      {children}
    </TenantCtx.Provider>
  )
}

export function useTenant() {
  const ctx = useContext(TenantCtx)
  if (!ctx) throw new Error('useTenant must be used within TenantProvider')
  return ctx
}
