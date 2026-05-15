import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'

function NavIcon({ d }: { d: string }) {
  return (
    <svg
      className="h-4 w-4 flex-shrink-0"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.75}
    >
      <path strokeLinecap="round" strokeLinejoin="round" d={d} />
    </svg>
  )
}

const SECTIONS = [
  {
    title: 'Visão Geral',
    items: [
      {
        path: '/admin/dashboard',
        label: 'Dashboard',
        icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6',
      },
    ],
  },
  {
    title: 'Clientes',
    items: [
      {
        path: '/admin/tenants',
        label: 'Empresas',
        icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4',
      },
      {
        path: '/admin/company-users',
        label: 'Usuários das Empresas',
        icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
      },
    ],
  },
  {
    title: 'Financeiro',
    items: [
      {
        path: '/admin/plans',
        label: 'Planos',
        icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2',
      },
      {
        path: '/admin/subscriptions',
        label: 'Assinaturas',
        icon: 'M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z',
      },
    ],
  },
  {
    title: 'Sistema',
    items: [
      {
        path: '/admin/system-admins',
        label: 'Administradores',
        icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z',
      },
    ],
  },
]

export function AdminLayout() {
  const { profile, signOut } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen flex bg-gray-900 text-white">
      <aside className="w-60 flex-shrink-0 border-r border-gray-700 flex flex-col">
        <div className="p-6">
          <span className="text-lg font-bold text-white">SaaS Admin</span>
          <span className="mt-1 block text-xs text-red-400 font-medium uppercase tracking-wide">
            Super Admin
          </span>
        </div>

        <nav className="flex-1 px-3 pb-4 space-y-5 overflow-y-auto">
          {SECTIONS.map((section) => (
            <div key={section.title}>
              <p className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-gray-500">
                {section.title}
              </p>
              <div className="space-y-0.5">
                {section.items.map((item) => {
                  const active = location.pathname.startsWith(item.path)
                  return (
                    <Link
                      key={item.path}
                      to={item.path}
                      className={`flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                        active
                          ? 'bg-gray-700 text-white'
                          : 'text-gray-400 hover:bg-gray-800 hover:text-white'
                      }`}
                    >
                      <NavIcon d={item.icon} />
                      {item.label}
                    </Link>
                  )
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="p-4 border-t border-gray-700">
          <p className="text-xs text-gray-500 mb-2 truncate">{profile?.full_name}</p>
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
