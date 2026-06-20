import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { getModuleToken, isModuleTokenExpiringSoon, fetchModuleToken } from './tokenService'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

/**
 * Cria uma instância Axios configurada para APIs de módulo.
 *
 * Cada requisição:
 *  1. Obtém o ModuleAccessToken do cache ou busca novo via /modules/{slug}/access-token
 *  2. Usa esse token no header Authorization (substitui o Supabase JWT)
 *  3. Se o token estiver a menos de 2 minutos do vencimento, renova automaticamente
 *
 * Uso:
 *   const mApi = createModuleApi('pdf', tenantId)
 *   mApi.post('/api/v1/modules/pdf/merge', formData)
 */
export function createModuleApi(moduleSlug: string, tenantId: string): AxiosInstance {
  const instance = axios.create({
    baseURL: API_BASE_URL,
  })

  instance.interceptors.request.use(async (config) => {
    // Renova proativamente se próximo de expirar
    if (isModuleTokenExpiringSoon(moduleSlug)) {
      await fetchModuleToken(moduleSlug, tenantId)
    }

    const token = await getModuleToken(moduleSlug, tenantId)
    config.headers.Authorization = `Bearer ${token}`
    return config
  })

  instance.interceptors.response.use(
    (response) => response,
    async (error) => {
      // Se o token foi rejeitado (expirou entre requests), busca um novo e tenta uma vez
      if (error.response?.status === 401 && !error.config?._retried) {
        error.config._retried = true
        await fetchModuleToken(moduleSlug, tenantId)
        const token = await getModuleToken(moduleSlug, tenantId)
        error.config.headers.Authorization = `Bearer ${token}`
        return instance.request(error.config as AxiosRequestConfig)
      }
      return Promise.reject(error)
    }
  )

  return instance
}
