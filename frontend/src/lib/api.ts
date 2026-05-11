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

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
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
