import axios, { AxiosError } from 'axios'
import { supabase } from '@/core/auth/supabase'
import type { ApiError } from '@/shared/types'

const CATALOG_API_BASE_URL = import.meta.env.VITE_CATALOG_API_URL as string

/** Cliente para o module-catalog-service (catálogo de módulos/serviços/grupos + resolve-route). */
export const catalogApi = axios.create({
  baseURL: CATALOG_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

catalogApi.interceptors.request.use(async (config) => {
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

catalogApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      supabase.auth.signOut()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
