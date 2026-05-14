import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'

export function AdminLayout() {
  const { profile, signOut } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  const navItems = [
    { path: '/admin/dashboard', label: 'Dashboard' },
    { path: '/admin/tenants', label: 'Empresas' },
    { path: '/admin/plans', label: 'Planos' },
    { path: '/admin/users', label: 'Usuários' },
  ]

  return (
    <div className="min-h-screen flex bg-gray-900 text-white">
      <aside className="w-56 flex-shrink-0 border-r border-gray-700 flex flex-col">
        <div className="p-6">
          <span className="text-lg font-bold text-white">SaaS Admin</span>
          <span className="mt-1 block text-xs text-red-400 font-medium uppercase tracking-wide">
            Super Admin
          </span>
        </div>

        <nav className="flex-1 px-3 pb-4 space-y-1">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={`flex items-center px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                location.pathname.startsWith(item.path)
                  ? 'bg-gray-700 text-white'
                  : 'text-gray-400 hover:bg-gray-800 hover:text-white'
              }`}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="p-4 border-t border-gray-700">
          <p className="text-xs text-gray-500 mb-2">{profile?.full_name}</p>
          <button
            onClick={handleSignOut}
            className="text-xs text-gray-400 hover:text-white"
          >
            Sair
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto">
        <div className="max-w-6xl mx-auto px-6 py-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
