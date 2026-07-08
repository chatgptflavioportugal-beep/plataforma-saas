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

export interface Tenant {
  id: string
  name: string
  slug: string
  type: 'individual' | 'business'
  status: 'trial' | 'active' | 'suspended' | 'cancelled'
  plan_id: string | null
  trial_ends_at: string | null
  settings: Record<string, unknown>
  created_at: string
  updated_at: string
}

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
  // calculados a partir dos módulos (sem fallback a preços fixos)
  total_monthly_price?: number
  total_annual_monthly_price?: number  // soma dos preços anual/mês dos módulos ativos
  total_annual_price?: number          // total_annual_monthly_price * 12
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


export interface TenantSubscription {
  id: string
  tenant_id: string
  plan_id: string
  status: 'trial' | 'active' | 'past_due' | 'cancelled' | 'suspended'
  trial_start: string | null
  trial_end: string | null
  current_period_start: string | null
  current_period_end: string | null
  cancelled_at: string | null
  plan_version: number
  contracted_price_monthly: number | null
  contracted_price_annual: number | null
  billing_type: 'monthly' | 'annual'
}

export interface UserTenant {
  id: string
  user_id: string
  tenant_id: string
  role: 'owner' | 'admin' | 'member' | 'finance'
  is_active: boolean
  tenant?: Tenant
}

export interface PdfJob {
  id: string
  tenant_id: string
  user_id: string
  status: 'pending' | 'processing' | 'completed' | 'failed'
  file_a_name: string
  file_b_name: string
  result_name: string | null
  error_message: string | null
  created_at: string
  updated_at: string
}

export interface ModulePlanLimit {
  title: string
  description: string | null
  limit_value: string | null
  unit: string | null
  sort_order: number
}

export interface ModulePlan {
  plan_id: string
  plan_name: string
  plan_slug: string
  plan_version_id: string
  plan_version: number
  /** Ordem cadastrada do plano (usada para comparação de upgrade/downgrade) */
  plan_sort_order: number
  monthly_price: number
  annual_monthly_price: number
  annual_total_price: number
  limits: ModulePlanLimit[]
}

export interface ModuleService {
  id: string
  name: string
  description: string | null
  icon_path: string | null
}

export interface ModuleBillingOption {
  module_id: string
  module_name: string
  module_slug: string
  module_description: string | null
  icon_path: string | null
  services: ModuleService[]
  available_plans: ModulePlan[]
}

export interface DashboardModuleService {
  serviceId: string
  serviceName: string
  serviceSlug: string
  serviceDescription: string | null
  serviceIconPath: string | null
  serviceGroupId: string | null
  serviceGroupName: string | null
  routeKey: string
  hasAccess: boolean
}

export type ServiceAccessStatus = 'ALLOWED' | 'DENIED' | 'NOT_FOUND'

export interface ResolvedServiceRoute {
  serviceId?: string
  serviceName?: string
  routeKey: string
  permissionKey?: string
  moduleId?: string
  moduleName?: string
  moduleSlug?: string
  accessStatus: ServiceAccessStatus
}

export interface DashboardModuleItem {
  moduleId: string
  moduleName: string
  moduleSlug: string
  moduleDescription: string | null
  moduleIconPath: string | null
  accessStatus: 'SUBSCRIBED' | 'FREE' | 'LOCKED'
  planName: string | null
  planSlug: string | null
  planVersionId: string | null
  badgeLabel: string
  serviceCount: number
  services: DashboardModuleService[]
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

export interface TenantProfile {
  tenant: Tenant
  subscription: TenantSubscription
  plan: Plan
  role: 'owner' | 'admin' | 'member' | 'finance'
}

export interface CompanyMember {
  user_id: string
  full_name: string | null
  email: string | null
  role: 'owner' | 'admin' | 'member' | 'finance'
  access_level_id: string | null
  access_level_name: string | null
  joined_at: string
}

export interface Invitation {
  id: string
  email: string
  role: 'admin' | 'member' | 'finance'
  access_level_id: string | null
  access_level_name: string | null
  status: 'pending' | 'accepted' | 'expired' | 'cancelled'
  expires_at: string
  created_at: string
}

export interface InvitationPreview {
  email: string
  role: string
  access_level_name: string | null
  status: 'pending' | 'accepted' | 'expired' | 'cancelled'
  expires_at: string
  tenant_name: string
}

export interface AccessLevelPermission {
  id: string
  moduleId: string
  serviceId: string
}

export interface AccessLevel {
  id: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
  updatedAt: string
  permissions: AccessLevelPermission[]
  adminPermissions: string[]
  memberCount: number
}

export interface AvailableService {
  serviceId: string
  serviceName: string
  serviceSlug: string
  serviceIconPath: string | null
  sortOrder?: number | null
}

export interface AvailableServiceGroup {
  groupId: string
  groupName: string
  groupSlug: string
  groupDescription: string | null
  groupIconPath: string | null
  sortOrder: number | null
  services: AvailableService[]
}

export interface AvailableModule {
  moduleId: string
  moduleName: string
  moduleSlug: string
  moduleIconPath: string | null
  serviceGroups: AvailableServiceGroup[]
  ungroupedServices: AvailableService[]
}

export interface PermissionTreeResponse {
  modules: AvailableModule[]
  adminPermissions: AdminPermissionGroup[]
}

export interface ProfileModuleSubscription {
  id: string
  profileId: string
  profileType: 'INDIVIDUAL' | 'COMPANY'
  moduleId: string
  moduleName: string
  moduleIconPath: string | null
  planVersionId: string
  planId: string
  planName: string
  /** Código estável do plano (plans.code), não muda entre versões — use para comparar com plan_slug do catálogo */
  planCode: string
  planVersion: number
  /** Ordem cadastrada do plano na versão contratada (congelada) */
  planSortOrder: number
  billingCycle: 'MONTHLY' | 'ANNUAL'
  /** Preço mensal do plano para este módulo — sempre da versão contratada (congelado) */
  monthlyPrice: number
  /** Preço mensal equivalente no ciclo anual (total anual = annualMonthlyPrice × 12) — congelado */
  annualMonthlyPrice: number
  status: 'ACTIVE' | 'PENDING_PAYMENT' | 'CANCELED' | 'EXPIRED'
  startedAt: string
  expiresAt: string | null
  canceledAt: string | null
  /** Limites da versão contratada (congelada), nunca da versão vigente do plano */
  limits: ModulePlanLimit[]
}
