import axios, { AxiosError } from 'axios'
import { supabase } from './supabase'
import type { ApiError } from '@/types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(async (config) => {
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
  '/api/v1/tenants/mine',
  '/api/v1/tenants/',
  '/api/v1/public/individual-tenant',
  '/api/v1/public/onboarding',
]

api.interceptors.response.use(
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

export function setActiveTenant(tenantId: string) {
  sessionStorage.setItem('active_tenant_id', tenantId)
}

export function getActiveTenantId(): string | null {
  return sessionStorage.getItem('active_tenant_id')
}
