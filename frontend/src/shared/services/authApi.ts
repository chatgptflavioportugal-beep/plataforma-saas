import axios, { AxiosError } from 'axios'
import { supabase } from '@/core/auth/supabase'
import type { ApiError } from '@/shared/types'

const AUTH_API_BASE_URL = import.meta.env.VITE_AUTH_API_URL as string

/** Cliente para o auth-service (emissão de ProfileAccessToken / ModuleAccessToken). */
export const authApi = axios.create({
  baseURL: AUTH_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

authApi.interceptors.request.use(async (config) => {
  const { data: { session } } = await supabase.auth.getSession()
  if (session?.access_token) {
    config.headers.Authorization = `Bearer ${session.access_token}`
  }

  const tenantId = sessionStorage.getItem('active_tenant_id')
  if (tenantId) {
    config.headers['X-Tenant-ID'] = tenantId
  }

  return config
})

const SKIP_SIGNOUT_URLS = [
  '/api/v1/profile/access-token',
]

authApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const url = error.config?.url ?? ''
    const isSkipped = SKIP_SIGNOUT_URLS.some((u) => url.includes(u))

    if (error.response?.status === 401 && !isSkipped) {
      supabase.auth.signOut()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
