import { api } from './api'

const PROFILE_TOKEN_KEY = 'profile_access_token'
const PROFILE_EXPIRY_KEY = 'profile_access_token_expiry'
const MODULE_TOKEN_PREFIX = 'module_access_token_'
const MODULE_EXPIRY_PREFIX = 'module_access_token_expiry_'

/** Margem de renovação antecipada: renova se faltar menos de 2 min */
const RENEWAL_MARGIN_MS = 2 * 60 * 1000

// ─── ProfileAccessToken ──────────────────────────────────────────────────────

export async function fetchProfileToken(tenantId: string): Promise<string> {
  const { data } = await api.post<{ profileAccessToken: string; expiresAt: string }>(
    '/api/v1/profile/access-token',
    null,
    { headers: { 'X-Tenant-ID': tenantId } }
  )
  sessionStorage.setItem(PROFILE_TOKEN_KEY, data.profileAccessToken)
  sessionStorage.setItem(PROFILE_EXPIRY_KEY, data.expiresAt)
  return data.profileAccessToken
}

export function getCachedProfileToken(): string | null {
  const token  = sessionStorage.getItem(PROFILE_TOKEN_KEY)
  const expiry = sessionStorage.getItem(PROFILE_EXPIRY_KEY)
  if (!token || !expiry) return null
  const expiresAt = new Date(expiry).getTime()
  if (Date.now() >= expiresAt - RENEWAL_MARGIN_MS) {
    clearProfileToken()
    return null
  }
  return token
}

export async function getProfileToken(tenantId: string): Promise<string> {
  return getCachedProfileToken() ?? fetchProfileToken(tenantId)
}

export function clearProfileToken(): void {
  sessionStorage.removeItem(PROFILE_TOKEN_KEY)
  sessionStorage.removeItem(PROFILE_EXPIRY_KEY)
}

// ─── ModuleAccessToken ───────────────────────────────────────────────────────

export interface ModuleTokenResponse {
  moduleAccessToken: string
  moduleSlug: string
  moduleName: string
  planName: string
  expiresAt: string
  permissions: string[]
  limits: { key: string; value: string | number; unit?: string }[]
}

export async function fetchModuleToken(
  moduleSlug: string,
  tenantId: string
): Promise<ModuleTokenResponse> {
  const { data } = await api.post<ModuleTokenResponse>(
    `/api/v1/modules/${moduleSlug}/access-token`,
    null,
    { headers: { 'X-Tenant-ID': tenantId } }
  )
  sessionStorage.setItem(MODULE_TOKEN_PREFIX + moduleSlug, data.moduleAccessToken)
  sessionStorage.setItem(MODULE_EXPIRY_PREFIX + moduleSlug, data.expiresAt)
  return data
}

export function getCachedModuleToken(moduleSlug: string): string | null {
  const token  = sessionStorage.getItem(MODULE_TOKEN_PREFIX + moduleSlug)
  const expiry = sessionStorage.getItem(MODULE_EXPIRY_PREFIX + moduleSlug)
  if (!token || !expiry) return null
  const expiresAt = new Date(expiry).getTime()
  if (Date.now() >= expiresAt - RENEWAL_MARGIN_MS) {
    clearModuleToken(moduleSlug)
    return null
  }
  return token
}

export async function getModuleToken(
  moduleSlug: string,
  tenantId: string
): Promise<string> {
  const cached = getCachedModuleToken(moduleSlug)
  if (cached) return cached
  const response = await fetchModuleToken(moduleSlug, tenantId)
  return response.moduleAccessToken
}

export function clearModuleToken(moduleSlug: string): void {
  sessionStorage.removeItem(MODULE_TOKEN_PREFIX + moduleSlug)
  sessionStorage.removeItem(MODULE_EXPIRY_PREFIX + moduleSlug)
}

export function clearAllTokens(): void {
  // Limpa PAT
  clearProfileToken()
  // Limpa todos os MATs (chaves prefixadas)
  const keysToRemove: string[] = []
  for (let i = 0; i < sessionStorage.length; i++) {
    const key = sessionStorage.key(i)
    if (key && (key.startsWith(MODULE_TOKEN_PREFIX) || key.startsWith(MODULE_EXPIRY_PREFIX))) {
      keysToRemove.push(key)
    }
  }
  keysToRemove.forEach((k) => sessionStorage.removeItem(k))
}

/** Verifica se o token de módulo está próximo do vencimento (< 2 min). */
export function isModuleTokenExpiringSoon(moduleSlug: string): boolean {
  const expiry = sessionStorage.getItem(MODULE_EXPIRY_PREFIX + moduleSlug)
  if (!expiry) return true
  return Date.now() >= new Date(expiry).getTime() - RENEWAL_MARGIN_MS
}
