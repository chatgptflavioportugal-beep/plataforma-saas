// ─── Objetos persistidos (fora do JWT apenas o essencial) ─────────────────────

export interface ProfileToken {
  token: string
  expiresAt: string
  permissionsVersion: number
}

export interface ModuleToken {
  token: string
  expiresAt: string
  permissionsVersion: number
}

export interface TokenStorage {
  profileToken?: ProfileToken
  moduleTokens: Record<string, ModuleToken>
}

// ─── Claims decodificadas do JWT (única fonte da verdade para o resto) ────────

export interface ProfileTokenClaims {
  sub: string
  tenantId: string
  profileType: 'INDIVIDUAL' | 'COMPANY'
  profileRole: 'OWNER' | 'MEMBER' | 'INDIVIDUAL'
  accessLevelId: string | null
  permissions: string[]
  permissionsVersion: number
  iat: number
  exp: number
}

export interface ModuleTokenClaims {
  sub: string
  tenantId: string
  moduleId: string
  moduleSlug: string
  planName: string | null
  accessSource: 'SUBSCRIPTION' | 'FREE_PLAN' | 'TENANT_SUBSCRIPTION'
  permissions: string[]
  limits: Record<string, string | number>
  permissionsVersion: number
  iat: number
  exp: number
}
