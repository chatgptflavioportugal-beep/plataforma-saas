import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/core/auth/AuthContext'
import { TenantProvider } from '@/core/workspaces/TenantContext'
import { AuthGuard } from '@/core/auth/AuthGuard'
import { SuperAdminGuard } from '@/core/auth/SuperAdminGuard'
import { ClientAreaGuard } from '@/core/auth/ClientAreaGuard'
import { SubscriptionGuard } from '@/core/permissions/SubscriptionGuard'

import { LoginPage } from '@/app/public/LoginPage'
import { RegisterPage } from '@/app/public/RegisterPage'
import { AuthCallbackPage } from '@/app/auth/AuthCallbackPage'
import { OnboardingPage } from '@/app/auth/OnboardingPage'
import { ProfileSelectorPage } from '@/app/auth/ProfileSelectorPage'
import { AcceptInvitePage } from '@/app/auth/AcceptInvitePage'

import { AppLayout } from '@/shared/layouts/AppLayout'
import { DashboardPage } from '@/app/client/DashboardPage'
import { ServicePage } from '@/app/client/services/ServicePage'
import { PlansPage } from '@/app/client/billing/PlansPage'
import { SubscriptionsPage } from '@/app/client/billing/SubscriptionsPage'
import { FeatureLockedPage } from '@/app/client/billing/FeatureLockedPage'
import { SettingsPage } from '@/app/client/SettingsPage'
import { CompanyMembersPage } from '@/app/client/settings/CompanyMembersPage'
import { AccessLevelsPage } from '@/app/client/settings/AccessLevelsPage'

import { AdminLayout } from '@/shared/layouts/AdminLayout'
import { AdminPermissionGuard } from '@/core/auth/AdminPermissionGuard'
import { AdminDashboardPage } from '@/app/admin/AdminDashboardPage'
import { AdminTenantsPage } from '@/app/admin/AdminTenantsPage'
import { AdminCustomersPage } from '@/app/admin/AdminCustomersPage'
import { AdminSystemAdminsPage } from '@/app/admin/AdminSystemAdminsPage'
import { AdminPlansPage } from '@/app/admin/AdminPlansPage'
import { AdminSubscriptionsPage } from '@/app/admin/AdminSubscriptionsPage'
import { AdminTrialsPage } from '@/app/admin/AdminTrialsPage'
import { AdminModulesPage } from '@/app/admin/AdminModulesPage'
import { AdminUsersPage } from '@/app/admin/AdminUsersPage'
import { AdminAccessLevelsPage } from '@/app/admin/AdminAccessLevelsPage'
import { AdminPlatformSettingsPage } from '@/app/admin/AdminPlatformSettingsPage'

const ADMIN_ROUTES_BY_PRIORITY = [
  { path: 'dashboard',           permission: 'admin.dashboard.view' },
  { path: 'tenants',             permission: 'admin.companies.view' },
  { path: 'customers',           permission: 'admin.clients.view' },
  { path: 'plans',               permission: 'admin.plans.view' },
  { path: 'subscriptions',       permission: 'admin.subscriptions.view' },
  { path: 'trials',              permission: 'admin.trials.view' },
  { path: 'modules',             permission: 'admin.modules.view' },
  { path: 'admin-users',         permission: 'admin.users.view' },
  { path: 'admin-access-levels', permission: 'admin.access_levels.view' },
  { path: 'settings',            permission: 'admin.settings.view' },
]

function AdminIndexRedirect() {
  const { isSuperAdmin, hasAdminPermission } = useAuth()
  const first = ADMIN_ROUTES_BY_PRIORITY.find(r => isSuperAdmin || hasAdminPermission(r.permission))
  if (!first) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-900/30 border border-red-700/50">
          <svg className="h-8 w-8 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
              d="M12 9v3.75m0-10.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.75c0 5.592 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.57-.598-3.75h-.152c-3.196 0-6.1-1.249-8.25-3.286z" />
          </svg>
        </div>
        <h2 className="text-lg font-semibold text-white mb-2">Sem acesso</h2>
        <p className="text-sm text-gray-400 max-w-xs">
          Você não possui permissão para acessar nenhuma área administrativa.
        </p>
      </div>
    )
  }
  return <Navigate to={first.path} replace />
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />

          {/* Seleção de perfil (autenticado, sem TenantProvider) */}
          <Route path="/select-profile" element={
            <AuthGuard>
              <ClientAreaGuard>
                <ProfileSelectorPage />
              </ClientAreaGuard>
            </AuthGuard>
          } />

          {/* Onboarding — criar empresa (tenant individual já existe pelo trigger) */}
          <Route path="/onboarding" element={
            <AuthGuard>
              <ClientAreaGuard>
                <OnboardingPage />
              </ClientAreaGuard>
            </AuthGuard>
          } />

          {/* Aceitar convite — autenticado, sem TenantProvider */}
          <Route path="/invite/accept" element={
            <ClientAreaGuard>
              <AcceptInvitePage />
            </ClientAreaGuard>
          } />

          {/* /app — área das empresas */}
          <Route path="/app/*" element={
            <AuthGuard>
              <ClientAreaGuard>
              <TenantProvider>
                <SubscriptionGuard>
                  <AppLayout />
                </SubscriptionGuard>
              </TenantProvider>
              </ClientAreaGuard>
            </AuthGuard>
          }>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            {/* Rota dinâmica de serviços — usa routeKey do cadastro */}
            <Route path=":routeKey" element={<ServicePage />} />
            {/* Redirect de rota legada */}
            <Route path="pdf/merge" element={<Navigate to="/app/pdf-pdf-merge" replace />} />
            <Route path="billing/plans" element={<PlansPage />} />
            <Route path="billing/subscriptions" element={<SubscriptionsPage />} />
            <Route path="billing/feature-locked" element={<FeatureLockedPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="settings/members" element={<CompanyMembersPage />} />
            <Route path="settings/access-levels" element={<AccessLevelsPage />} />
          </Route>

          {/* /admin — área administrativa (SUPER_ADMIN) */}
          <Route path="/admin/*" element={
            <AuthGuard>
              <SuperAdminGuard>
                <AdminLayout />
              </SuperAdminGuard>
            </AuthGuard>
          }>
            <Route index element={<AdminIndexRedirect />} />
            <Route path="dashboard" element={<AdminPermissionGuard permission="admin.dashboard.view"><AdminDashboardPage /></AdminPermissionGuard>} />
            <Route path="tenants" element={<AdminPermissionGuard permission="admin.companies.view"><AdminTenantsPage /></AdminPermissionGuard>} />
            <Route path="customers" element={<AdminPermissionGuard permission="admin.clients.view"><AdminCustomersPage /></AdminPermissionGuard>} />
            <Route path="system-admins" element={<AdminPermissionGuard permission="admin.users.view"><AdminSystemAdminsPage /></AdminPermissionGuard>} />
            <Route path="plans" element={<AdminPermissionGuard permission="admin.plans.view"><AdminPlansPage /></AdminPermissionGuard>} />
            <Route path="subscriptions" element={<AdminPermissionGuard permission="admin.subscriptions.view"><AdminSubscriptionsPage /></AdminPermissionGuard>} />
            <Route path="trials" element={<AdminPermissionGuard permission="admin.trials.view"><AdminTrialsPage /></AdminPermissionGuard>} />
            <Route path="modules" element={<AdminPermissionGuard permission="admin.modules.view"><AdminModulesPage /></AdminPermissionGuard>} />
            <Route path="admin-users" element={<AdminPermissionGuard permission="admin.users.view"><AdminUsersPage /></AdminPermissionGuard>} />
            <Route path="admin-access-levels" element={<AdminPermissionGuard permission="admin.access_levels.view"><AdminAccessLevelsPage /></AdminPermissionGuard>} />
            <Route path="settings" element={<AdminPermissionGuard permission="admin.settings.view"><AdminPlatformSettingsPage /></AdminPermissionGuard>} />
            {/* Redirecionamentos de rotas legadas */}
            <Route path="company-users" element={<Navigate to="/admin/customers" replace />} />
            <Route path="users" element={<Navigate to="/admin/customers" replace />} />
          </Route>

          {/* Default */}
          <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
