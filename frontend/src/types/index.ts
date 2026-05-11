export interface UserProfile {
  id: string
  full_name: string | null
  avatar_url: string | null
  phone: string | null
  system_role: 'user' | 'SUPER_ADMIN'
  is_active: boolean
  metadata: Record<string, unknown>
  created_at: string
  updated_at: string
}

export interface Tenant {
  id: string
  name: string
  slug: string
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
  code: 'free' | 'starter' | 'pro' | 'enterprise'
  description: string | null
  price_monthly: number
  max_users: number
  max_ai_requests_month: number
  features: PlanFeatures
  is_active: boolean
  sort_order: number
}

export interface PlanFeatures {
  'pdf.merge': boolean
  'reports.view': boolean
  'reports.export': boolean
  'ai.agents': boolean
  'api.access': boolean
  white_label: boolean
  priority_support: boolean
  max_users: number
  max_ai_requests_month: number
  max_pdf_merges_month: number
  [key: string]: boolean | number
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
}

export interface UserTenant {
  id: string
  user_id: string
  tenant_id: string
  role: 'owner' | 'admin' | 'member'
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

export interface TenantContext {
  tenant: Tenant
  subscription: TenantSubscription
  plan: Plan
  role: 'owner' | 'admin' | 'member'
}
