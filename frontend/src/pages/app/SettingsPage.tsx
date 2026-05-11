import { useAuth } from '@/contexts/AuthContext'
import { useTenant } from '@/contexts/TenantContext'

export function SettingsPage() {
  const { profile } = useAuth()
  const { currentTenant } = useTenant()

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
        <h2 className="font-semibold text-gray-900">Empresa</h2>
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
    </div>
  )
}
