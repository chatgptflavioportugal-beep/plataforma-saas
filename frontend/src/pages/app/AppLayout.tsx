import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { useTenant } from '@/contexts/TenantContext'
import { TrialBanner } from '@/components/billing/TrialBanner'

export function AppLayout() {
  const { profile, signOut } = useAuth()
  const { currentTenant } = useTenant()
  const location = useLocation()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  const navItems = [
    { path: '/app/dashboard', label: 'Dashboard' },
    { path: '/app/pdf/merge', label: 'Merge de PDFs' },
    { path: '/app/billing/plans', label: 'Planos' },
    { path: '/app/settings', label: 'Configurações' },
  ]

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <TrialBanner />

      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 flex h-16 items-center justify-between">
          <div className="flex items-center gap-6">
            <span className="text-lg font-bold text-primary-700">SaaS Platform</span>
            <span className="text-sm text-gray-400">{currentTenant?.tenant?.name}</span>
          </div>

          <nav className="hidden md:flex items-center gap-1">
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  location.pathname.startsWith(item.path)
                    ? 'bg-primary-50 text-primary-700'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                {item.label}
              </Link>
            ))}
          </nav>

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
