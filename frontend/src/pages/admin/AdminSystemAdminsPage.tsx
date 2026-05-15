import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

interface SystemAdmin {
  id: string
  email: string
  full_name: string | null
  system_role: string
  is_active: boolean
  created_at: string
}

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  SUPPORT: 'Suporte',
  FINANCE: 'Financeiro',
}

const ROLE_COLORS: Record<string, string> = {
  SUPER_ADMIN: 'bg-red-900 text-red-200',
  ADMIN: 'bg-orange-900 text-orange-200',
  SUPPORT: 'bg-blue-900 text-blue-200',
  FINANCE: 'bg-purple-900 text-purple-200',
}

export function AdminSystemAdminsPage() {
  const { data: admins = [], isLoading } = useQuery({
    queryKey: ['admin-system-admins'],
    queryFn: async () => {
      const { data } = await api.get<SystemAdmin[]>('/api/v1/admin/system-admins')
      return data
    },
    retry: false,
    staleTime: 30_000,
  })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Administradores do Sistema</h1>
        <p className="mt-1 text-sm text-gray-400">
          Usuários internos com acesso administrativo à plataforma.
        </p>
      </div>

      <div className="rounded-xl bg-amber-900/20 border border-amber-700/40 px-4 py-3">
        <p className="text-xs text-amber-300">
          <strong>Somente leitura.</strong> Para promover ou remover um administrador, altere o campo{' '}
          <code className="bg-gray-900/60 px-1 rounded">system_role</code> na tabela{' '}
          <code className="bg-gray-900/60 px-1 rounded">user_profiles</code> diretamente no banco de dados.
          Papéis disponíveis: <strong>SUPER_ADMIN</strong>, <strong>ADMIN</strong>, <strong>SUPPORT</strong>, <strong>FINANCE</strong>.
        </p>
      </div>

      {isLoading ? (
        <p className="text-gray-400">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700">
            <span className="text-sm text-gray-400">
              {admins.length} administrador{admins.length !== 1 ? 'es' : ''}
            </span>
          </div>
          <table className="min-w-full divide-y divide-gray-700">
            <thead>
              <tr>
                {['Nome', 'Email', 'Papel', 'Status', 'Cadastrado em'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-700">
              {admins.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-sm text-gray-500">
                    Nenhum administrador encontrado.
                  </td>
                </tr>
              ) : (
                admins.map((a) => (
                  <tr key={a.id} className="hover:bg-gray-750">
                    <td className="px-4 py-3 text-sm text-white font-medium">{a.full_name ?? '—'}</td>
                    <td className="px-4 py-3 text-sm text-gray-400">{a.email}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                        ROLE_COLORS[a.system_role] ?? 'bg-gray-700 text-gray-300'
                      }`}>
                        {ROLE_LABELS[a.system_role] ?? a.system_role}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                        a.is_active ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'
                      }`}>
                        {a.is_active ? 'Ativo' : 'Inativo'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-400">
                      {new Date(a.created_at).toLocaleDateString('pt-BR')}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
