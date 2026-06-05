import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/core/auth/AuthContext'
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
import { PdfMergePage } from '@/modules/pdf/PdfMergePage'
import { PlansPage } from '@/app/client/billing/PlansPage'
import { SubscriptionsPage } from '@/app/client/billing/SubscriptionsPage'
import { TrialExpiredPage } from '@/app/client/billing/TrialExpiredPage'
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
import { AdminModulesPage } from '@/app/admin/AdminModulesPage'
import { AdminUsersPage } from '@/app/admin/AdminUsersPage'
import { AdminAccessLevelsPage } from '@/app/admin/AdminAccessLevelsPage'

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
            <Route path="pdf/merge" element={<PdfMergePage />} />
            <Route path="billing/plans" element={<PlansPage />} />
            <Route path="billing/subscriptions" element={<SubscriptionsPage />} />
            <Route path="billing/trial-expired" element={<TrialExpiredPage />} />
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
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<AdminPermissionGuard permission="admin.dashboard.view"><AdminDashboardPage /></AdminPermissionGuard>} />
            <Route path="tenants" element={<AdminPermissionGuard permission="admin.companies.view"><AdminTenantsPage /></AdminPermissionGuard>} />
            <Route path="customers" element={<AdminPermissionGuard permission="admin.clients.view"><AdminCustomersPage /></AdminPermissionGuard>} />
            <Route path="system-admins" element={<AdminPermissionGuard permission="admin.users.view"><AdminSystemAdminsPage /></AdminPermissionGuard>} />
            <Route path="plans" element={<AdminPermissionGuard permission="admin.plans.view"><AdminPlansPage /></AdminPermissionGuard>} />
            <Route path="subscriptions" element={<AdminPermissionGuard permission="admin.subscriptions.view"><AdminSubscriptionsPage /></AdminPermissionGuard>} />
            <Route path="modules" element={<AdminPermissionGuard permission="admin.modules.view"><AdminModulesPage /></AdminPermissionGuard>} />
            <Route path="admin-users" element={<AdminPermissionGuard permission="admin.users.view"><AdminUsersPage /></AdminPermissionGuard>} />
            <Route path="admin-access-levels" element={<AdminPermissionGuard permission="admin.access_levels.view"><AdminAccessLevelsPage /></AdminPermissionGuard>} />
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
