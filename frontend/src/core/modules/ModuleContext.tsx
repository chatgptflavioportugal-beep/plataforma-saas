import {
  createContext,
  useContext,
  useEffect,
  useState,
  useMemo,
  type ReactNode,
} from 'react'
import { useTenant } from '@/core/workspaces/TenantContext'
import {
  fetchModuleToken,
  fetchProfileToken,
  getCachedModuleToken,
  isModuleTokenExpiringSoon,
} from '@/shared/services/tokenService'
import { createModuleApi } from '@/shared/services/moduleApi'
import { api } from '@/shared/services/api'
import { decodeJwt } from '@/shared/utils/jwt'
import type { ModuleTokenClaims, ProfileTokenClaims } from '@/shared/types/tokens'
import type { AxiosInstance } from 'axios'
import { isAxiosError } from 'axios'
import { useQueryClient } from '@tanstack/react-query'

interface ModuleContextValue {
  moduleSlug: string
  moduleToken: string | null
  permissions: string[]
  limits: ModuleTokenClaims['limits']
  planName: string | null
  isLoading: boolean
  /** Mensagem exibida durante a ativação automática do plano Free (primeiro acesso). */
  activationMessage: string | null
  error: string | null
  hasPermission: (key: string) => boolean
  /** Instância Axios pré-configurada com o ModuleAccessToken para este módulo. */
  moduleApi: AxiosInstance | null
  /** Força renovação manual do token (ex: após detectar 401 no módulo). */
  refreshToken: () => Promise<void>
}

const ModuleCtx = createContext<ModuleContextValue | null>(null)

interface ModuleProviderProps {
  moduleSlug: string
  children: ReactNode
}

const NO_PERMISSION_MESSAGE =
  'Você não possui permissão para ativar este módulo. Solicite ao proprietário da empresa ' +
  'ou a um administrador que realize a ativação do plano Free.'

/**
 * Provê o ModuleAccessToken e o axios configurado para um módulo específico.
 *
 * Deve envolver os componentes do módulo. O token é buscado automaticamente
 * ao montar o provider e renovado proativamente antes de expirar.
 */
export function ModuleProvider({ moduleSlug, children }: ModuleProviderProps) {
  const { activeTenantId, profileAccessToken } = useTenant()
  const queryClient = useQueryClient()

  const [moduleToken, setModuleToken] = useState<string | null>(() =>
    getCachedModuleToken(moduleSlug)
  )
  const [isLoading, setIsLoading]                     = useState(!moduleToken)
  const [activationMessage, setActivationMessage]     = useState<string | null>(null)
  const [error, setError]                             = useState<string | null>(null)

  // Checagem local de permissão para ativar um módulo Free — usa exclusivamente
  // as claims já decodificadas do Profile Token (sem consultar banco/backend).
  const canActivateFreeModule = () => {
    if (!profileAccessToken) return false
    try {
      const claims = decodeJwt<ProfileTokenClaims>(profileAccessToken)
      return (
        claims.profileType === 'INDIVIDUAL' ||
        claims.profileRole === 'OWNER' ||
        claims.permissions.includes('profile.plans.subscribe')
      )
    } catch {
      return false
    }
  }

  const loadToken = async () => {
    if (!activeTenantId) return
    setIsLoading(true)
    setError(null)
    setActivationMessage(null)
    try {
      const data = await fetchModuleToken(moduleSlug, activeTenantId)
      setModuleToken(data.moduleAccessToken)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409 && err.response.data?.code === 'FREE_PLAN_NOT_ACTIVATED') {
        if (!canActivateFreeModule()) {
          setError(NO_PERMISSION_MESSAGE)
          setModuleToken(null)
          setIsLoading(false)
          return
        }
        await activateFreeModuleAndRetry()
        return
      }
      setError('Sem acesso a este módulo. Verifique sua assinatura.')
      setModuleToken(null)
    } finally {
      setIsLoading(false)
    }
  }

  const activateFreeModuleAndRetry = async () => {
    if (!activeTenantId) return
    setActivationMessage('Estamos preparando seu acesso. Aguarde alguns segundos...')
    try {
      // Corpo em snake_case: o backend usa Jackson com property-naming-strategy=SNAKE_CASE
      await api.post('/api/v1/subscriptions/free', { module_slug: moduleSlug })
      await fetchProfileToken(activeTenantId)
      queryClient.invalidateQueries({ queryKey: ['dashboard-modules'] })
      const data = await fetchModuleToken(moduleSlug, activeTenantId)
      setModuleToken(data.moduleAccessToken)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 403) {
        setError(NO_PERMISSION_MESSAGE)
      } else {
        setError('Não foi possível ativar o plano gratuito. Tente novamente.')
      }
      setModuleToken(null)
    } finally {
      setActivationMessage(null)
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (!activeTenantId) return
    const cached = getCachedModuleToken(moduleSlug)
    if (cached && !isModuleTokenExpiringSoon(moduleSlug)) {
      setModuleToken(cached)
      setIsLoading(false)
      return
    }
    loadToken()
    // Agenda renovação automática 2 min antes de expirar (token de 30 min → renova em 28 min)
    const interval = setInterval(() => {
      if (isModuleTokenExpiringSoon(moduleSlug)) {
        loadToken()
      }
    }, 60 * 1000) // verifica a cada minuto
    return () => clearInterval(interval)
  }, [moduleSlug, activeTenantId])

  const builtModuleApi = useMemo(() => {
    if (!moduleToken || !activeTenantId) return null
    return createModuleApi(moduleSlug, activeTenantId)
  }, [moduleToken, moduleSlug, activeTenantId])

  const claims = useMemo<ModuleTokenClaims | null>(() => {
    if (!moduleToken) return null
    try {
      return decodeJwt<ModuleTokenClaims>(moduleToken)
    } catch {
      return null
    }
  }, [moduleToken])

  const hasPermission = (key: string) =>
    (claims?.permissions ?? []).includes(key)

  return (
    <ModuleCtx.Provider value={{
      moduleSlug,
      moduleToken,
      permissions: claims?.permissions ?? [],
      limits:      claims?.limits ?? {},
      planName:    claims?.planName ?? null,
      isLoading,
      activationMessage,
      error,
      hasPermission,
      moduleApi:   builtModuleApi,
      refreshToken: loadToken,
    }}>
      {children}
    </ModuleCtx.Provider>
  )
}

export function useModule(): ModuleContextValue {
  const ctx = useContext(ModuleCtx)
  if (!ctx) throw new Error('useModule deve ser usado dentro de ModuleProvider')
  return ctx
}
