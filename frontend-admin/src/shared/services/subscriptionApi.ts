import axios, { AxiosError } from 'axios'
import { supabase } from '@/core/auth/supabase'
import type { ApiError } from '@/shared/types'

const SUBSCRIPTION_API_BASE_URL = import.meta.env.VITE_SUBSCRIPTION_API_URL as string

/** Cliente para o subscription-service (cancelar/reativar assinatura como admin). */
export const subscriptionApi = axios.create({
  baseURL: SUBSCRIPTION_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

subscriptionApi.interceptors.request.use(async (config) => {
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

subscriptionApi.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      supabase.auth.signOut()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
