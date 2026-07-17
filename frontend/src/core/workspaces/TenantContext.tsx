import { createContext, useContext, useState, useEffect, useRef, useCallback, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useLocation } from 'react-router-dom'
import { AxiosError } from 'axios'
import { api, setActiveTenant } from '@/shared/services/api'
import { fetchProfileToken, fetchPermissionsVersion, clearAllTokens } from '@/shared/services/tokenService'
import { decodeJwt } from '@/shared/utils/jwt'
import type { ProfileTokenClaims } from '@/shared/types/tokens'
import type { ApiError, TenantProfile, UserTenant } from '@/shared/types'
import { useAuth } from '@/core/auth/AuthContext'

/** Intervalo de polling da versão de permissões — invalida tokens em cache sem esperar expirar. */
const PERMISSIONS_VERSION_POLL_MS = 30 * 1000

interface TenantContextValue {
  currentTenant: TenantProfile | null
  userTenants: UserTenant[]
  activeTenantId: string | null
  isLoading: boolean
  switchTenant: (tenantId: string) => void
  isTrialExpiringSoon: boolean
  trialDaysRemaining: number | null
  individualTenant: UserTenant | null
  businessTenants: UserTenant[]
  /** ProfileAccessToken vigente para o perfil ativo (null enquanto carrega). */
  profileAccessToken: string | null
  /** Mensagem de erro ao resolver o tenant ativo (ex.: sem assinatura ativa, acesso negado). */
  tenantProfileError: string | null
  /**
   * Versão vigente de permissões do perfil ativo (permissions_version em user_tenants).
   * Muda quando nível de acesso, permissões do nível ou assinatura são alterados —
   * consumidores (ex.: ModuleProvider) devem renovar tokens em cache ao detectar a mudança.
   */
  permissionsVersion: number | null
}

const TenantCtx = createContext<TenantContextValue | null>(null)

export function TenantProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [activeTenantId, setActiveTenantIdState] = useState<string | null>(
    sessionStorage.getItem('active_tenant_id')
  )
  const [profileAccessToken, setProfileAccessToken] = useState<string | null>(null)
  const [permissionsVersion, setPermissionsVersion] = useState<number | null>(null)
  const permissionsVersionRef = useRef<number | null>(null)

  const { data: userTenants = [], isLoading: tenantsLoading, isFetching: tenantsFetching, isError: tenantsError } = useQuery({
    queryKey: ['user-tenants', user?.id],
    queryFn: async () => {
      const { data } = await api.get<UserTenant[]>('/api/v1/tenants/mine')
      return data
    },
    enabled: !!user,
    retry: false,
  })

  const individualTenant = userTenants.find((ut) => ut.tenant?.type === 'individual') ?? null
  const businessTenants = userTenants.filter((ut) => ut.tenant?.type === 'business')

  // Definido antes do useEffect para evitar TDZ nas deps do effect
  const switchTenant = useCallback((tenantId: string) => {
    // Limpa tokens do perfil anterior ao trocar de perfil
    clearAllTokens()
    setProfileAccessToken(null)
    permissionsVersionRef.current = null
    setPermissionsVersion(null)
    setActiveTenantIdState(tenantId)
    setActiveTenant(tenantId)
  }, [])

  // Busca o ProfileAccessToken sempre que o perfil ativo mudar
  useEffect(() => {
    if (!activeTenantId) return
    fetchProfileToken(activeTenantId)
      .then((token) => {
        setProfileAccessToken(token)
        const claims = decodeJwt<ProfileTokenClaims>(token)
        permissionsVersionRef.current = claims.permissionsVersion
        setPermissionsVersion(claims.permissionsVersion)
      })
      .catch((err) => {
        console.error(
          '[TenantContext] Falha ao gerar ProfileAccessToken para tenant',
          activeTenantId,
          ':',
          err instanceof AxiosError ? err.response?.data ?? err.message : err
        )
        setProfileAccessToken(null)
      })
  }, [activeTenantId])

  // Polling leve: detecta mudança de nível de acesso/permissões/assinatura e descarta
  // PAT/MAT em cache sem esperar a expiração natural do token (8h no PAT, 30min no MAT).
  useEffect(() => {
    if (!activeTenantId) return

    const checkPermissionsVersion = async () => {
      try {
        const version = await fetchPermissionsVersion(activeTenantId)
        if (permissionsVersionRef.current !== null && version !== permissionsVersionRef.current) {
          clearAllTokens()
          const token = await fetchProfileToken(activeTenantId)
          setProfileAccessToken(token)
        }
        permissionsVersionRef.current = version
        setPermissionsVersion(version)
      } catch (err) {
        console.error('[TenantContext] Falha ao verificar permissionsVersion:', err)
      }
    }

    const interval = setInterval(checkPermissionsVersion, PERMISSIONS_VERSION_POLL_MS)
    return () => clearInterval(interval)
  }, [activeTenantId])

  useEffect(() => {
    if (tenantsLoading || tenantsFetching) return
    if (tenantsError) return

    // Sem nenhum tenant → onboarding para escolher/criar perfil
    // O trigger do banco cria user_profile mas não tenants.
    if (userTenants.length === 0) {
      if (location.pathname !== '/onboarding') {
        navigate('/onboarding', { replace: true })
      }
      return
    }

    // Já tem um tenant ativo salvo — valida se ainda pertence ao usuário
    if (activeTenantId) {
      const stillMember = userTenants.some((ut) => ut.tenant_id === activeTenantId)
      if (stillMember) return
      // Tenant salvo não existe mais — limpa e deixa re-render rotear
      sessionStorage.removeItem('active_tenant_id')
      setActiveTenantIdState(null)
      return
    }

    // Só 1 tenant (caso comum: apenas plano individual) → seleciona automaticamente
    if (userTenants.length === 1) {
      switchTenant(userTenants[0].tenant_id)
      return
    }

    // Vários tenants sem preferência salva → seletor de perfil
    if (location.pathname !== '/select-profile') {
      navigate('/select-profile', { replace: true })
    }
  }, [tenantsLoading, tenantsFetching, tenantsError, userTenants, activeTenantId, navigate, location.pathname, switchTenant])

  const { data: currentTenant, isLoading: tenantLoading, error: tenantProfileErrorObj } = useQuery({
    queryKey: ['tenant-profile', activeTenantId],
    queryFn: async () => {
      const { data } = await api.get<TenantProfile>(`/api/v1/tenants/${activeTenantId}/profile`)
      return data
    },
    enabled: !!activeTenantId,
    retry: false,
  })

  const tenantProfileError = (() => {
    if (!tenantProfileErrorObj) return null
    if (tenantProfileErrorObj instanceof AxiosError) {
      const apiError = tenantProfileErrorObj.response?.data as ApiError | undefined
      return apiError?.message ?? apiError?.error ?? tenantProfileErrorObj.message
    }
    return (tenantProfileErrorObj as Error).message
  })()

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
      individualTenant,
      businessTenants,
      profileAccessToken,
      tenantProfileError,
      permissionsVersion,
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
