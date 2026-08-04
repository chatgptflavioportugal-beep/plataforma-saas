import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/core/auth/AuthContext'
import { SuperAdminGuard } from '@/core/auth/SuperAdminGuard'
import { AdminPermissionGuard } from '@/core/auth/AdminPermissionGuard'
import { Spinner } from '@/shared/components/Spinner'

import { AdminLayout } from '@/shared/layouts/AdminLayout'

const AdminLoginPage = lazy(() => import('@/app/public/AdminLoginPage').then(m => ({ default: m.AdminLoginPage })))
const AdminDashboardPage = lazy(() => import('@/app/admin/AdminDashboardPage').then(m => ({ default: m.AdminDashboardPage })))
const AdminTenantsPage = lazy(() => import('@/app/admin/AdminTenantsPage').then(m => ({ default: m.AdminTenantsPage })))
const AdminCustomersPage = lazy(() => import('@/app/admin/AdminCustomersPage').then(m => ({ default: m.AdminCustomersPage })))
const AdminSystemAdminsPage = lazy(() => import('@/app/admin/AdminSystemAdminsPage').then(m => ({ default: m.AdminSystemAdminsPage })))
const AdminPlansPage = lazy(() => import('@/app/admin/AdminPlansPage').then(m => ({ default: m.AdminPlansPage })))
const AdminSubscriptionsPage = lazy(() => import('@/app/admin/AdminSubscriptionsPage').then(m => ({ default: m.AdminSubscriptionsPage })))
const AdminTrialsPage = lazy(() => import('@/app/admin/AdminTrialsPage').then(m => ({ default: m.AdminTrialsPage })))
const AdminModulesPage = lazy(() => import('@/app/admin/AdminModulesPage').then(m => ({ default: m.AdminModulesPage })))
const AdminUsersPage = lazy(() => import('@/app/admin/AdminUsersPage').then(m => ({ default: m.AdminUsersPage })))
const AdminAccessLevelsPage = lazy(() => import('@/app/admin/AdminAccessLevelsPage').then(m => ({ default: m.AdminAccessLevelsPage })))
const AdminPlatformSettingsPage = lazy(() => import('@/app/admin/AdminPlatformSettingsPage').then(m => ({ default: m.AdminPlatformSettingsPage })))

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

function IndexRedirect() {
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
        <Suspense fallback={<Spinner fullscreen />}>
        <Routes>
          <Route path="/login" element={<AdminLoginPage />} />

          <Route path="/*" element={
            <SuperAdminGuard>
              <AdminLayout />
            </SuperAdminGuard>
          }>
            <Route index element={<IndexRedirect />} />
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
            <Route path="company-users" element={<Navigate to="/customers" replace />} />
            <Route path="users" element={<Navigate to="/customers" replace />} />
          </Route>
        </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  )
}
