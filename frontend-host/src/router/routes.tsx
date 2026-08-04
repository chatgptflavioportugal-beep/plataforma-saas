import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/core/auth/AuthContext'
import { TenantProvider } from '@/core/workspaces/TenantContext'
import { AuthGuard } from '@/core/auth/AuthGuard'
import { ClientAreaGuard } from '@/core/auth/ClientAreaGuard'
import { SubscriptionGuard } from '@/core/permissions/SubscriptionGuard'
import { Spinner } from '@/shared/components/Spinner'

import { AppLayout } from '@/shared/layouts/AppLayout'

const LoginPage = lazy(() => import('@/app/public/LoginPage').then(m => ({ default: m.LoginPage })))
const RegisterPage = lazy(() => import('@/app/public/RegisterPage').then(m => ({ default: m.RegisterPage })))
const AuthCallbackPage = lazy(() => import('@/app/auth/AuthCallbackPage').then(m => ({ default: m.AuthCallbackPage })))
const OnboardingPage = lazy(() => import('@/app/auth/OnboardingPage').then(m => ({ default: m.OnboardingPage })))
const ProfileSelectorPage = lazy(() => import('@/app/auth/ProfileSelectorPage').then(m => ({ default: m.ProfileSelectorPage })))
const AcceptInvitePage = lazy(() => import('@/app/auth/AcceptInvitePage').then(m => ({ default: m.AcceptInvitePage })))

const DashboardPage = lazy(() => import('@/app/client/DashboardPage').then(m => ({ default: m.DashboardPage })))
const ServicePage = lazy(() => import('@/app/client/services/ServicePage').then(m => ({ default: m.ServicePage })))
const PlansPage = lazy(() => import('@/app/client/billing/PlansPage').then(m => ({ default: m.PlansPage })))
const SubscriptionsPage = lazy(() => import('@/app/client/billing/SubscriptionsPage').then(m => ({ default: m.SubscriptionsPage })))
const FeatureLockedPage = lazy(() => import('@/app/client/billing/FeatureLockedPage').then(m => ({ default: m.FeatureLockedPage })))
const SettingsPage = lazy(() => import('@/app/client/SettingsPage').then(m => ({ default: m.SettingsPage })))
const CompanyMembersPage = lazy(() => import('@/app/client/settings/CompanyMembersPage').then(m => ({ default: m.CompanyMembersPage })))
const AccessLevelsPage = lazy(() => import('@/app/client/settings/AccessLevelsPage').then(m => ({ default: m.AccessLevelsPage })))

export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<Spinner fullscreen />}>
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
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  )
}
