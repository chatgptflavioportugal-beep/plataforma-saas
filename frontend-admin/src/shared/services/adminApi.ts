import axios, { AxiosError } from 'axios'
import { supabase } from '@/core/auth/supabase'
import type { ApiError } from '@/shared/types'

const ADMIN_API_BASE_URL = import.meta.env.VITE_ADMIN_API_URL as string

/** Cliente para o admin-service (tenants, clientes, usuários admin, níveis de acesso, dashboard admin). */
export const adminApi = axios.create({
  baseURL: ADMIN_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

adminApi.interceptors.request.use(async (config) => {
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

adminApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      supabase.auth.signOut()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
