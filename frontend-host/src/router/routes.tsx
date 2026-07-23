import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/core/auth/AuthContext'
import { TenantProvider } from '@/core/workspaces/TenantContext'
import { AuthGuard } from '@/core/auth/AuthGuard'
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
            {/* Rota dinâmica de serviços — resolve o módulo e carrega o Micro Frontend correspondente via Module Federation */}
            <Route path=":routeKey" element={<ServicePage />} />
            <Route path="billing/plans" element={<PlansPage />} />
            <Route path="billing/subscriptions" element={<SubscriptionsPage />} />
            <Route path="billing/feature-locked" element={<FeatureLockedPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="settings/members" element={<CompanyMembersPage />} />
            <Route path="settings/access-levels" element={<AccessLevelsPage />} />
          </Route>

          {/* Default */}
          <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
