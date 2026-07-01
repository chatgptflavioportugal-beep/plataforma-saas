import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { getModuleToken, isModuleTokenExpiringSoon, fetchModuleToken } from './tokenService'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

/**
 * URL base por módulo. Módulos com backend próprio (ex: PDF → Python)
 * apontam para sua URL específica; os demais caem no Quarkus.
 */
const MODULE_BASE_URLS: Record<string, string> = {
  pdf: import.meta.env.VITE_PDF_API_URL as string,
}

function getModuleBaseUrl(moduleSlug: string): string {
  return MODULE_BASE_URLS[moduleSlug] ?? API_BASE_URL
}

/**
 * Cria uma instância Axios configurada para APIs de módulo.
 *
 * Cada requisição:
 *  1. Resolve a URL base: módulo PDF → Python; demais → Quarkus
 *  2. Obtém o ModuleAccessToken do cache ou busca novo via Quarkus /module-token/{slug}
 *  3. Injeta Bearer token no header Authorization
 *  4. Renova automaticamente se o token estiver próximo do vencimento
 */
export function createModuleApi(moduleSlug: string, tenantId: string): AxiosInstance {
  const instance = axios.create({
    baseURL: getModuleBaseUrl(moduleSlug),
  })

  instance.interceptors.request.use(async (config) => {
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
