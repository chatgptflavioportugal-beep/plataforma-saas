import { api } from './api'
import { decodeJwt } from '../utils/jwt'
import type { ModuleTokenClaims, ProfileTokenClaims, TokenStorage } from '../types/tokens'

const STORAGE_KEY = 'auth_tokens'

/** Margem de renovação antecipada: renova se faltar menos de 2 min */
const RENEWAL_MARGIN_MS = 2 * 60 * 1000

function readStorage(): TokenStorage {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return { moduleTokens: {} }
  try {
    const parsed = JSON.parse(raw) as Partial<TokenStorage>
    return { profileToken: parsed.profileToken, moduleTokens: parsed.moduleTokens ?? {} }
  } catch {
    return { moduleTokens: {} }
  }
}

function writeStorage(storage: TokenStorage): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(storage))
}

function isExpiredOrExpiringSoon(expiresAt: string): boolean {
  return Date.now() >= new Date(expiresAt).getTime() - RENEWAL_MARGIN_MS
}

// ─── ProfileAccessToken ──────────────────────────────────────────────────────

export async function fetchProfileToken(tenantId: string): Promise<string> {
  const { data } = await api.post<{ profileAccessToken: string; expiresAt: string }>(
    '/api/v1/profile/access-token',
    null,
    { headers: { 'X-Tenant-ID': tenantId } }
  )
  const claims = decodeJwt<ProfileTokenClaims>(data.profileAccessToken)

  const storage = readStorage()
  storage.profileToken = {
    token: data.profileAccessToken,
    expiresAt: data.expiresAt,
    permissionsVersion: claims.permissionsVersion,
  }
  writeStorage(storage)

  return data.profileAccessToken
}

/**
 * Retorna o ProfileAccessToken em cache se ainda válido.
 * Se `expectedPermissionsVersion` for informado e divergir do token armazenado, descarta o cache.
 */
export function getCachedProfileToken(expectedPermissionsVersion?: number): string | null {
  const { profileToken } = readStorage()
  if (!profileToken) return null

  if (isExpiredOrExpiringSoon(profileToken.expiresAt)) {
    clearProfileToken()
    return null
  }
  if (
    expectedPermissionsVersion !== undefined &&
    profileToken.permissionsVersion !== expectedPermissionsVersion
  ) {
    clearProfileToken()
    return null
  }

  return profileToken.token
}

export async function getProfileToken(
  tenantId: string,
  expectedPermissionsVersion?: number
): Promise<string> {
  return getCachedProfileToken(expectedPermissionsVersion) ?? fetchProfileToken(tenantId)
}

export function clearProfileToken(): void {
  const storage = readStorage()
  delete storage.profileToken
  writeStorage(storage)
}

// ─── ModuleAccessToken ───────────────────────────────────────────────────────

export interface ModuleTokenResponse {
  moduleAccessToken: string
  moduleSlug: string
  moduleName: string
  planName: string
  expiresAt: string
  permissions: string[]
  limits: Record<string, string | number>
}

export async function fetchModuleToken(
  moduleSlug: string,
  tenantId: string
): Promise<ModuleTokenResponse> {
  const { data } = await api.post<ModuleTokenResponse>(
    `/api/v1/module-token/${moduleSlug}`,
    null,
    { headers: { 'X-Tenant-ID': tenantId } }
  )
  const claims = decodeJwt<ModuleTokenClaims>(data.moduleAccessToken)

  const storage = readStorage()
  storage.moduleTokens[moduleSlug] = {
    token: data.moduleAccessToken,
    expiresAt: data.expiresAt,
    permissionsVersion: claims.permissionsVersion,
  }
  writeStorage(storage)

  return data
}

/**
 * Retorna o ModuleAccessToken em cache se ainda válido.
 * Se `expectedPermissionsVersion` for informado e divergir do token armazenado, descarta o cache.
 */
export function getCachedModuleToken(
  moduleSlug: string,
  expectedPermissionsVersion?: number
): string | null {
  const { moduleTokens } = readStorage()
  const entry = moduleTokens[moduleSlug]
  if (!entry) return null

  if (isExpiredOrExpiringSoon(entry.expiresAt)) {
    clearModuleToken(moduleSlug)
    return null
  }
  if (expectedPermissionsVersion !== undefined && entry.permissionsVersion !== expectedPermissionsVersion) {
    clearModuleToken(moduleSlug)
    return null
  }

  return entry.token
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
  const storage = readStorage()
  delete storage.moduleTokens[moduleSlug]
  writeStorage(storage)
}

export function clearAllTokens(): void {
  writeStorage({ moduleTokens: {} })
}

/** Verifica se o token de módulo está próximo do vencimento (< 2 min) ou ausente. */
export function isModuleTokenExpiringSoon(moduleSlug: string): boolean {
  const { moduleTokens } = readStorage()
  const entry = moduleTokens[moduleSlug]
  if (!entry) return true
  return isExpiredOrExpiringSoon(entry.expiresAt)
}
