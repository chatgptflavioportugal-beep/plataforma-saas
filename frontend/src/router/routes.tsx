import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/contexts/AuthContext'
import { TenantProvider } from '@/contexts/TenantContext'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { SuperAdminGuard } from '@/components/auth/SuperAdminGuard'
import { SubscriptionGuard } from '@/components/auth/SubscriptionGuard'

import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { AuthCallbackPage } from '@/pages/AuthCallbackPage'
import { OnboardingPage } from '@/pages/OnboardingPage'
import { ContextSelectorPage } from '@/pages/ContextSelectorPage'

import { AppLayout } from '@/pages/app/AppLayout'
import { DashboardPage } from '@/pages/app/DashboardPage'
import { PdfMergePage } from '@/pages/app/pdf/PdfMergePage'
import { PlansPage } from '@/pages/app/billing/PlansPage'
import { TrialExpiredPage } from '@/pages/app/billing/TrialExpiredPage'
import { FeatureLockedPage } from '@/pages/app/billing/FeatureLockedPage'
import { SettingsPage } from '@/pages/app/SettingsPage'
import { CompanyMembersPage } from '@/pages/app/settings/CompanyMembersPage'
import { AcceptInvitePage } from '@/pages/AcceptInvitePage'

import { AdminLayout } from '@/pages/admin/AdminLayout'
import { AdminDashboardPage } from '@/pages/admin/AdminDashboardPage'
import { AdminTenantsPage } from '@/pages/admin/AdminTenantsPage'
import { AdminCompanyUsersPage } from '@/pages/admin/AdminCompanyUsersPage'
import { AdminSystemAdminsPage } from '@/pages/admin/AdminSystemAdminsPage'
import { AdminPlansPage } from '@/pages/admin/AdminPlansPage'
import { AdminSubscriptionsPage } from '@/pages/admin/AdminSubscriptionsPage'

export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />

          {/* Seleção de contexto (autenticado, sem TenantProvider) */}
          <Route path="/select-context" element={
            <AuthGuard>
              <ContextSelectorPage />
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
            <Route path="company-users" element={<AdminCompanyUsersPage />} />
            <Route path="system-admins" element={<AdminSystemAdminsPage />} />
            <Route path="plans" element={<AdminPlansPage />} />
            <Route path="subscriptions" element={<AdminSubscriptionsPage />} />
            {/* Redireciona rota legada */}
            <Route path="users" element={<Navigate to="/admin/company-users" replace />} />
          </Route>

          {/* Default */}
          <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
