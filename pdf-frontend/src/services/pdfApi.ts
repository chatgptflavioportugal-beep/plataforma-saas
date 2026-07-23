import axios, { type AxiosInstance } from 'axios'

const PDF_API_URL = import.meta.env.VITE_PDF_API_URL as string

/**
 * Cria a instância Axios do pdf-frontend. O ModuleAccessToken é recebido por
 * prop a partir do Front Host (ver ModuleApiContext) — este arquivo nunca
 * chama Profile/Module Catalog/Subscription/Auth, só o pdf-service.
 */
export function createPdfApi(moduleToken: string): AxiosInstance {
  const instance = axios.create({
    baseURL: PDF_API_URL,
    headers: {
      Authorization: `Bearer ${moduleToken}`,
    },
  })

  return instance
}
