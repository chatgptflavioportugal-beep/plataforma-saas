import { createContext, useContext, useMemo, type ReactNode } from 'react'
import type { AxiosInstance } from 'axios'
import { createWhatsappApi } from '@/services/whatsappApi'

interface ModuleApiContextValue {
  moduleApi: AxiosInstance
  onUnauthorized?: () => void
}

const ModuleApiCtx = createContext<ModuleApiContextValue | null>(null)

interface ModuleApiProviderProps {
  moduleToken: string
  onUnauthorized?: () => void
  children: ReactNode
}

/**
 * Recebe o ModuleAccessToken já resolvido pelo Front Host (via prop do
 * componente exposto por Module Federation) e monta a instância Axios do
 * whatsapp-service para os hooks/páginas deste micro-frontend consumirem.
 */
export function ModuleApiProvider({ moduleToken, onUnauthorized, children }: ModuleApiProviderProps) {
  const moduleApi = useMemo(() => {
    const instance = createWhatsappApi(moduleToken)
    instance.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          onUnauthorized?.()
        }
        return Promise.reject(error)
      }
    )
    return instance
  }, [moduleToken, onUnauthorized])

  return (
    <ModuleApiCtx.Provider value={{ moduleApi, onUnauthorized }}>
      {children}
    </ModuleApiCtx.Provider>
  )
}

export function useModuleApi(): ModuleApiContextValue {
  const ctx = useContext(ModuleApiCtx)
  if (!ctx) throw new Error('useModuleApi deve ser usado dentro de ModuleApiProvider')
  return ctx
}
