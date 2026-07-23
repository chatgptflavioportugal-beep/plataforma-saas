import axios, { type AxiosInstance } from 'axios'

const WHATSAPP_API_URL = import.meta.env.VITE_WHATSAPP_API_URL as string

/**
 * Cria a instância Axios do whatsapp-frontend. O ModuleAccessToken é recebido
 * por prop a partir do Front Host (ver ModuleApiContext) — este arquivo nunca
 * chama Profile/Module Catalog/Subscription/Auth, só o whatsapp-service.
 */
export function createWhatsappApi(moduleToken: string): AxiosInstance {
  return axios.create({
    baseURL: WHATSAPP_API_URL,
    headers: {
      Authorization: `Bearer ${moduleToken}`,
    },
  })
}
