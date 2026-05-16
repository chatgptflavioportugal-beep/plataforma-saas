import { Link } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { useTenant } from '@/contexts/TenantContext'

export function SettingsPage() {
  const { profile } = useAuth()
  const { currentTenant } = useTenant()

  const isBusiness = currentTenant?.tenant?.type === 'business'
  const canManageMembers = isBusiness &&
    (currentTenant?.role === 'owner' || currentTenant?.role === 'admin')

  return (
    <div className="space-y-8 max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900">Configurações</h1>

      <div className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm space-y-4">
        <h2 className="font-semibold text-gray-900">Meu perfil</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500">Nome</p>
            <p className="font-medium text-gray-900">{profile?.full_name ?? '—'}</p>
          </div>
          <div>
            <p className="text-gray-500">Papel no sistema</p>
            <p className="font-medium text-gray-900">{profile?.system_role}</p>
          </div>
        </div>
      </div>

      <div className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm space-y-4">
        <h2 className="font-semibold text-gray-900">
          {isBusiness ? 'Empresa' : 'Plano Individual'}
        </h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500">Nome</p>
            <p className="font-medium text-gray-900">{currentTenant?.tenant?.name ?? '—'}</p>
          </div>
          <div>
            <p className="text-gray-500">Slug</p>
            <p className="font-medium text-gray-900">{currentTenant?.tenant?.slug ?? '—'}</p>
          </div>
          <div>
            <p className="text-gray-500">Status</p>
            <p className="font-medium text-gray-900 capitalize">{currentTenant?.tenant?.status ?? '—'}</p>
          </div>
          <div>
            <p className="text-gray-500">Meu papel</p>
            <p className="font-medium text-gray-900 capitalize">{currentTenant?.role ?? '—'}</p>
          </div>
        </div>
      </div>

      {canManageMembers && (
        <div className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-semibold text-gray-900">Membros da empresa</h2>
              <p className="text-sm text-gray-500 mt-0.5">
                Gerencie quem tem acesso a esta empresa.
              </p>
            </div>
            <Link
              to="/app/settings/members"
              className="flex items-center gap-1.5 rounded-lg bg-primary-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary-700 transition-colors"
            >
              Gerenciar
              <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
              </svg>
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
