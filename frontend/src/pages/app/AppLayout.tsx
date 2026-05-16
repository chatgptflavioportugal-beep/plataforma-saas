import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { useTenant } from '@/contexts/TenantContext'
import { TrialBanner } from '@/components/billing/TrialBanner'
import { ContextSwitcher } from '@/components/context/ContextSwitcher'

export function AppLayout() {
  const { profile, signOut } = useAuth()
  const { currentTenant } = useTenant()
  const location = useLocation()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  const isBusiness = currentTenant?.tenant?.type === 'business'
  const canManageMembers = isBusiness && (currentTenant?.role === 'owner' || currentTenant?.role === 'admin')

  const navItems = [
    { path: '/app/dashboard', label: 'Dashboard' },
    { path: '/app/pdf/merge', label: 'Merge de PDFs' },
    { path: '/app/billing/plans', label: 'Planos' },
    ...(canManageMembers ? [{ path: '/app/settings/members', label: 'Membros', exact: true }] : []),
    { path: '/app/settings', label: 'Configurações', exact: true },
  ]

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <TrialBanner />

      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 flex h-16 items-center justify-between">

          {/* Logo + Seletor de contexto */}
          <div className="flex items-center gap-4">
            <span className="text-lg font-bold text-primary-700">SaaS Platform</span>
            <ContextSwitcher />
          </div>

          {/* Navegação central */}
          <nav className="hidden md:flex items-center gap-1">
            {navItems.map((item) => {
              const isActive = item.exact
                ? location.pathname === item.path
                : location.pathname.startsWith(item.path)
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-primary-50 text-primary-700'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>

          {/* Usuário */}
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-600">{profile?.full_name}</span>
            <button
              onClick={handleSignOut}
              className="text-sm text-gray-500 hover:text-gray-700"
            >
              Sair
            </button>
          </div>

        </div>
      </header>

      <main className="flex-1">
        <div className="max-w-7xl mx-auto px-4 py-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
