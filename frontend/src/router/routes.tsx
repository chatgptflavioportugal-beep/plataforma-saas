import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/contexts/AuthContext'
import { TenantProvider } from '@/contexts/TenantContext'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { SuperAdminGuard } from '@/components/auth/SuperAdminGuard'
import { SubscriptionGuard } from '@/components/auth/SubscriptionGuard'

import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { OnboardingPage } from '@/pages/OnboardingPage'

import { AppLayout } from '@/pages/app/AppLayout'
import { DashboardPage } from '@/pages/app/DashboardPage'
import { PdfMergePage } from '@/pages/app/pdf/PdfMergePage'
import { PlansPage } from '@/pages/app/billing/PlansPage'
import { TrialExpiredPage } from '@/pages/app/billing/TrialExpiredPage'
import { FeatureLockedPage } from '@/pages/app/billing/FeatureLockedPage'
import { SettingsPage } from '@/pages/app/SettingsPage'

import { AdminLayout } from '@/pages/admin/AdminLayout'
import { AdminDashboardPage } from '@/pages/admin/AdminDashboardPage'
import { AdminTenantsPage } from '@/pages/admin/AdminTenantsPage'
import { AdminPlansPage } from '@/pages/admin/AdminPlansPage'
import { AdminUsersPage } from '@/pages/admin/AdminUsersPage'

export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Onboarding (autenticado, sem tenant ainda) */}
          <Route path="/onboarding" element={
            <AuthGuard>
              <OnboardingPage />
            </AuthGuard>
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
            <Route path="plans" element={<AdminPlansPage />} />
            <Route path="users" element={<AdminUsersPage />} />
          </Route>

          {/* Default */}
          <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
