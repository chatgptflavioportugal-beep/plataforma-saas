import { useAuth } from './AuthContext'

interface AdminPermissionGuardProps {
  permission: string
  children: React.ReactNode
}

export function AdminPermissionGuard({ permission, children }: AdminPermissionGuardProps) {
  const { hasAdminPermission } = useAuth()

  if (!hasAdminPermission(permission)) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-900/30 border border-red-700/50">
          <svg className="h-8 w-8 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round"
              d="M12 9v3.75m0-10.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.75c0 5.592 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.57-.598-3.75h-.152c-3.196 0-6.1-1.249-8.25-3.286z" />
          </svg>
        </div>
        <h2 className="text-lg font-semibold text-white mb-2">Acesso negado</h2>
        <p className="text-sm text-gray-400 max-w-xs">
          Você não possui permissão para acessar esta página.
        </p>
      </div>
    )
  }

  return <>{children}</>
}
