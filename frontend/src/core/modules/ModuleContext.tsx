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
  getCachedModuleToken,
  isModuleTokenExpiringSoon,
  type ModuleTokenResponse,
} from '@/shared/services/tokenService'
import { createModuleApi } from '@/shared/services/moduleApi'
import type { AxiosInstance } from 'axios'

interface ModuleContextValue {
  moduleSlug: string
  moduleToken: string | null
  permissions: string[]
  limits: ModuleTokenResponse['limits']
  planName: string | null
  isLoading: boolean
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

/**
 * Provê o ModuleAccessToken e o axios configurado para um módulo específico.
 *
 * Deve envolver os componentes do módulo. O token é buscado automaticamente
 * ao montar o provider e renovado proativamente antes de expirar.
 */
export function ModuleProvider({ moduleSlug, children }: ModuleProviderProps) {
  const { activeTenantId } = useTenant()

  const [tokenData, setTokenData] = useState<ModuleTokenResponse | null>(null)
  const [moduleToken, setModuleToken] = useState<string | null>(() =>
    getCachedModuleToken(moduleSlug)
  )
  const [isLoading, setIsLoading] = useState(!moduleToken)
  const [error, setError]         = useState<string | null>(null)

  const loadToken = async () => {
    if (!activeTenantId) return
    setIsLoading(true)
    setError(null)
    try {
      const data = await fetchModuleToken(moduleSlug, activeTenantId)
      setTokenData(data)
      setModuleToken(data.moduleAccessToken)
    } catch (err) {
      setError('Sem acesso a este módulo. Verifique sua assinatura.')
      setModuleToken(null)
    } finally {
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

  const hasPermission = (key: string) =>
    (tokenData?.permissions ?? []).includes(key)

  return (
    <ModuleCtx.Provider value={{
      moduleSlug,
      moduleToken,
      permissions: tokenData?.permissions ?? [],
      limits:      tokenData?.limits ?? [],
      planName:    tokenData?.planName ?? null,
      isLoading,
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
