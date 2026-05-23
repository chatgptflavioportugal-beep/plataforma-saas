export interface UserProfile {
  id: string
  full_name: string | null
  avatar_url: string | null
  phone: string | null
  system_role: 'user' | 'SUPER_ADMIN' | 'ADMIN' | 'SUPPORT' | 'FINANCE_ADMIN'
  is_active: boolean
  metadata: Record<string, unknown>
  created_at: string
  updated_at: string
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
  limit_key: string | null
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
  joined_at: string
}

export interface Invitation {
  id: string
  email: string
  role: 'admin' | 'member' | 'finance'
  status: 'pending' | 'accepted' | 'expired' | 'cancelled'
  expires_at: string
  created_at: string
}

export interface InvitationPreview {
  email: string
  role: string
  status: 'pending' | 'accepted' | 'expired' | 'cancelled'
  expires_at: string
  tenant_name: string
}
