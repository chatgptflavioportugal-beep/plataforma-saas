export interface UserProfile {
  id: string
  full_name: string | null
  avatar_url: string | null
  phone: string | null
  system_role: 'user' | 'SUPER_ADMIN' | 'ADMIN_USER'
  is_active: boolean
  admin_access_level_id: string | null
  metadata: Record<string, unknown>
  created_at: string
  updated_at: string
}

export interface ApiError {
  error: string
  message?: string
  feature?: string
  currentPlan?: string
  requiredPlan?: string
  upgradeUrl?: string
  limit?: number
  used?: number
}

// ─── Admin Access Levels ──────────────────────────────────────────────────────

export interface AdminAccessLevel {
  id: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'INACTIVE'
  permCount: number
  userCount: number
  createdAt: string
  updatedAt: string
}

export interface AdminAccessLevelDetail extends AdminAccessLevel {
  permissionKeys: string[]
}

export interface AdminPermissionGroup {
  groupKey: string
  groupName: string
  permissions: AdminPermission[]
}

export interface AdminPermission {
  permissionKey: string
  label: string
}

export interface AdminPermissionTreeResponse {
  groups: AdminPermissionGroup[]
}

// ─── Admin Users ──────────────────────────────────────────────────────────────

export interface AdminUser {
  id: string
  email: string
  fullName: string | null
  systemRole: 'SUPER_ADMIN' | 'ADMIN_USER'
  isActive: boolean
  accessLevelId: string | null
  accessLevelName: string | null
  createdAt: string
  lastSignInAt: string | null
}

// ─── Planos / Módulos ──────────────────────────────────────────────────────────

export interface Plan {
  id: string
  name: string
  code: string
  description: string | null
  price_monthly: number
  price_annual: number | null
  discount_annual_percent: number
  max_users: number
  max_ai_requests_month: number
  is_active: boolean
  sort_order: number
  version: number
  is_current_version: boolean
  parent_plan_id: string | null
  billing_type: 'monthly' | 'annual' | 'both'
  is_most_popular: boolean
  plan_type: 'individual' | 'business'
  created_at?: string
  subscriber_count?: number
  paid_subscriptions?: number
  trial_subscriptions?: number
  trial_campaigns_active?: number
  trial_campaigns_cancelled?: number
  total_monthly_price?: number
  total_annual_monthly_price?: number
  total_annual_price?: number
  module_count?: number
  modules_json?: string
}

export interface PlanVersionModule {
  id: string
  plan_id: string
  module_id: string
  module_name: string
  module_slug: string
  module_icon_path: string | null
  monthly_price: number
  annual_monthly_price: number
  status: 'active' | 'inactive'
  sort_order: number
  limits_json?: string
  created_at: string
  updated_at: string
}

export interface PlanVersionModuleLimit {
  id: string
  title: string
  description: string | null
  code: string | null
  limit_value: string | null
  unit: string | null
  sort_order: number
}

export interface PlatformModule {
  id: string
  name: string
  slug: string
  description: string | null
  module_url: string
  icon_path: string | null
  is_active: boolean
  sort_order: number
  service_count?: number
}

// ─── Configurações Gerais da Plataforma ────────────────────────────────────────

export interface PlatformSetting {
  key: string
  value: string
  description: string | null
  updatedAt: string
}

// ─── Trial Campaigns (admin) ───────────────────────────────────────────────────

export type TrialCampaignStatus = 'ACTIVE' | 'SCHEDULED' | 'CLOSED' | 'CANCELLED'

export interface TrialCampaign {
  id: string
  planVersionModuleId: string
  moduleId: string
  moduleName: string
  name: string
  status: TrialCampaignStatus
  days: number
  maxSlots: number
  usedSlots: number
  startDate: string | null
  endDate: string | null
  notes: string | null
  priority: number
  createdAt: string
  updatedAt?: string
  expired?: boolean
  createdByUserId?: string | null
  createdByName?: string | null
  updatedByUserId?: string | null
  updatedByName?: string | null
  planName?: string
  planCode?: string
  planVersion?: number
  planMonthlyPrice?: number
  planAnnualPrice?: number
  moduleSlug?: string
  moduleIcon?: string | null
  totalParticipants?: number
  conversionPercent?: number
  participantsActive?: number
  participantsExpired?: number
  participantsCancelled?: number
}

export interface TrialCampaignHistoryEntry {
  action: string
  actorName: string | null
  createdAt: string
}

export interface TrialCampaignListResult {
  items: TrialCampaign[]
  total: number
  page: number
  size: number
}

export interface TrialCampaignParticipant {
  tenantId: string
  tenantName: string
  tenantType: 'INDIVIDUAL' | 'COMPANY'
  userName: string | null
  userEmail: string | null
  startedAt: string
  finishedAt: string | null
  canceledAt: string | null
  status: string | null
  becameCustomer: boolean
}
