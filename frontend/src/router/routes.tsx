import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/core/auth/AuthContext'
import { TenantProvider } from '@/core/workspaces/TenantContext'
import { AuthGuard } from '@/core/auth/AuthGuard'
import { SuperAdminGuard } from '@/core/auth/SuperAdminGuard'
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

import { AdminLayout } from '@/shared/layouts/AdminLayout'
import { AdminDashboardPage } from '@/app/admin/AdminDashboardPage'
import { AdminTenantsPage } from '@/app/admin/AdminTenantsPage'
import { AdminCustomersPage } from '@/app/admin/AdminCustomersPage'
import { AdminSystemAdminsPage } from '@/app/admin/AdminSystemAdminsPage'
import { AdminPlansPage } from '@/app/admin/AdminPlansPage'
import { AdminSubscriptionsPage } from '@/app/admin/AdminSubscriptionsPage'
import { AdminModulesPage } from '@/app/admin/AdminModulesPage'

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
              <ProfileSelectorPage />
            </AuthGuard>
          } />

          {/* Onboarding — criar empresa (tenant individual já existe pelo trigger) */}
          <Route path="/onboarding" element={
            <AuthGuard>
              <OnboardingPage />
            </AuthGuard>
          } />

          {/* Aceitar convite — autenticado, sem TenantProvider */}
          <Route path="/invite/accept" element={
            <AcceptInvitePage />
          } />

          {/* /app — área das empresas */}
          <Route path="/app/*" element={
            <AuthGuard>
              <TenantProvider>
                <SubscriptionGuard>
                  <AppLayout />
                </SubscriptionGuard>
              </TenantProvider>
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
            <Route path="dashboard" element={<AdminDashboardPage />} />
            <Route path="tenants" element={<AdminTenantsPage />} />
            <Route path="customers" element={<AdminCustomersPage />} />
            <Route path="system-admins" element={<AdminSystemAdminsPage />} />
            <Route path="plans" element={<AdminPlansPage />} />
            <Route path="subscriptions" element={<AdminSubscriptionsPage />} />
            <Route path="modules" element={<AdminModulesPage />} />
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
