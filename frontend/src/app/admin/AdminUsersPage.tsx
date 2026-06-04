import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
import type { AdminUser, AdminAccessLevel } from '@/shared/types'

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN_USER: 'Usuário Admin',
}

const ROLE_COLORS: Record<string, string> = {
  SUPER_ADMIN: 'bg-red-900 text-red-200',
  ADMIN_USER:  'bg-blue-900 text-blue-200',
}

// ─── Modal criar/editar ────────────────────────────────────────────────────────

function AdminUserModal({
  user,
  accessLevels,
  onClose,
}: {
  user: AdminUser | null
  accessLevels: AdminAccessLevel[]
  onClose: () => void
}) {
  const qc = useQueryClient()
  const isEdit = !!user

  const [form, setForm] = useState({
    email: user?.email ?? '',
    fullName: user?.fullName ?? '',
    accessLevelId: user?.accessLevelId ?? '',
    tempPassword: '',
  })
  const [error, setError] = useState('')
  const [createdPassword, setCreatedPassword] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const mutation = useMutation({
    mutationFn: async () => {
      if (isEdit) {
        await api.put(`/api/v1/admin/admin-users/${user!.id}`, {
          fullName: form.fullName,
          accessLevelId: form.accessLevelId || null,
        })
        return null
      } else {
        const { data } = await api.post<{ tempPassword: string }>('/api/v1/admin/admin-users', {
          email: form.email,
          fullName: form.fullName,
          accessLevelId: form.accessLevelId || null,
          tempPassword: form.tempPassword || null,
        })
        return data
      }
    },
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['admin-admin-users'] })
      if (data?.tempPassword) {
        setCreatedPassword(data.tempPassword)
      } else {
        onClose()
      }
    },
    onError: (err: any) => {
      setError(err?.response?.data?.error ?? 'Erro ao salvar usuário')
    },
  })

  function copyPassword() {
    if (!createdPassword) return
    navigator.clipboard.writeText(createdPassword).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  function set(field: keyof typeof form, value: string) {
    setForm(f => ({ ...f, [field]: value }))
    setError('')
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-md rounded-xl bg-gray-800 border border-gray-700 shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">
            {isEdit ? 'Editar Usuário Admin' : 'Novo Usuário Admin'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">&times;</button>
        </div>

        {createdPassword ? (
          <div className="p-6 space-y-4">
            <div className="rounded-lg bg-green-900/30 border border-green-700/50 p-4 space-y-3">
              <p className="text-sm font-medium text-green-300">Usuário criado com sucesso!</p>
              <p className="text-xs text-gray-400">
                Compartilhe a senha temporária abaixo com o usuário. Ela não será exibida novamente.
              </p>
              <div className="flex items-center gap-2">
                <code className="flex-1 rounded bg-gray-900 border border-gray-700 px-3 py-2 text-sm text-white font-mono tracking-wider">
                  {createdPassword}
                </code>
                <button
                  onClick={copyPassword}
                  className="rounded-lg bg-gray-700 border border-gray-600 text-gray-300 text-xs font-medium px-3 py-2 hover:bg-gray-600 min-w-[64px]"
                >
                  {copied ? 'Copiado!' : 'Copiar'}
                </button>
              </div>
            </div>
            <button
              onClick={onClose}
              className="w-full rounded-lg bg-blue-600 text-white text-sm font-medium py-2 hover:bg-blue-500"
            >
              Fechar
            </button>
          </div>
        ) : (
        <>
        <div className="p-6 space-y-4">
          {!isEdit && (
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">E-mail *</label>
              <input
                type="email"
                value={form.email}
                onChange={e => set('email', e.target.value)}
                className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
                placeholder="admin@empresa.com"
              />
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Nome completo *</label>
            <input
              type="text"
              value={form.fullName}
              onChange={e => set('fullName', e.target.value)}
              className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
              placeholder="João da Silva"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Nível de Acesso</label>
            <select
              value={form.accessLevelId}
              onChange={e => set('accessLevelId', e.target.value)}
              className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
            >
              <option value="">Sem nível de acesso (apenas SUPER_ADMIN pode atribuir)</option>
              {accessLevels.filter(al => al.status === 'ACTIVE').map(al => (
                <option key={al.id} value={al.id}>{al.name}</option>
              ))}
            </select>
          </div>

          {!isEdit && (
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Senha temporária (opcional)</label>
              <input
                type="text"
                value={form.tempPassword}
                onChange={e => set('tempPassword', e.target.value)}
                className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
                placeholder="Deixe em branco para gerar automaticamente"
              />
              <p className="mt-1 text-xs text-gray-500">
                Se não informada, uma senha aleatória será gerada. Compartilhe-a com o usuário.
              </p>
            </div>
          )}

          {!isEdit && (
            <div className="rounded-lg bg-amber-900/30 border border-amber-700/40 px-3 py-2">
              <p className="text-xs text-amber-300">
                O usuário deverá acessar a área Admin usando <strong>e-mail e senha</strong>.
                Login com Google não é permitido para usuários administrativos.
              </p>
            </div>
          )}

          {error && (
            <p className="text-sm text-red-400">{error}</p>
          )}
        </div>

        <div className="flex gap-3 px-6 pb-6">
          <button
            onClick={onClose}
            className="flex-1 rounded-lg border border-gray-600 text-gray-300 text-sm font-medium py-2 hover:bg-gray-700"
          >
            Cancelar
          </button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
            className="flex-1 rounded-lg bg-blue-600 text-white text-sm font-medium py-2 hover:bg-blue-500 disabled:opacity-50"
          >
            {mutation.isPending ? 'Salvando...' : isEdit ? 'Salvar' : 'Criar Usuário'}
          </button>
        </div>
        </>
        )}
      </div>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export function AdminUsersPage() {
  const { isSuperAdmin, hasAdminPermission } = useAuth()
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [editingUser, setEditingUser] = useState<AdminUser | null | 'new'>()
  const [statusFilter, setStatusFilter] = useState('')

  const canCreate = isSuperAdmin || hasAdminPermission('admin.users.create')
  const canEdit   = isSuperAdmin || hasAdminPermission('admin.users.edit')
  const canToggle = isSuperAdmin || hasAdminPermission('admin.users.activate')

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin-admin-users', search, statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (search) params.set('search', search)
      if (statusFilter) params.set('status', statusFilter)
      const { data } = await api.get<AdminUser[]>(`/api/v1/admin/admin-users?${params}`)
      return data
    },
    staleTime: 30_000,
  })

  const { data: accessLevels = [] } = useQuery({
    queryKey: ['admin-access-levels-active'],
    queryFn: async () => {
      const { data } = await api.get<AdminAccessLevel[]>('/api/v1/admin/access-levels?status=ACTIVE')
      return data
    },
    staleTime: 60_000,
  })

  const toggleStatus = useMutation({
    mutationFn: async ({ id, isActive }: { id: string; isActive: boolean }) => {
      await api.patch(`/api/v1/admin/admin-users/${id}/status`, { isActive })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-admin-users'] }),
  })

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Usuários Administrativos</h1>
          <p className="mt-1 text-sm text-gray-400">
            Usuários internos com acesso à área administrativa da plataforma.
          </p>
        </div>
        {canCreate && (
          <button
            onClick={() => setEditingUser('new')}
            className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
          >
            + Novo Usuário
          </button>
        )}
      </div>

      {/* Filtros */}
      <div className="flex gap-3">
        <input
          type="text"
          placeholder="Buscar por nome ou e-mail..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="flex-1 rounded-lg bg-gray-800 border border-gray-700 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
        />
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value)}
          className="rounded-lg bg-gray-800 border border-gray-700 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
        >
          <option value="">Todos</option>
          <option value="true">Ativos</option>
          <option value="false">Inativos</option>
        </select>
      </div>

      {/* Tabela */}
      {isLoading ? (
        <p className="text-gray-400">Carregando...</p>
      ) : (
        <div className="rounded-xl bg-gray-800 border border-gray-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-700">
            <span className="text-sm text-gray-400">
              {users.length} usuário{users.length !== 1 ? 's' : ''}
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-700">
              <thead>
                <tr>
                  {['Nome', 'E-mail', 'Tipo', 'Nível de Acesso', 'Status', 'Último acesso', 'Ações'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700">
                {users.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-4 py-10 text-center text-sm text-gray-500">
                      Nenhum usuário administrativo encontrado.
                    </td>
                  </tr>
                ) : (
                  users.map(u => (
                    <tr key={u.id} className="hover:bg-gray-750">
                      <td className="px-4 py-3 text-sm text-white font-medium">{u.fullName ?? '—'}</td>
                      <td className="px-4 py-3 text-sm text-gray-400">{u.email}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${ROLE_COLORS[u.systemRole] ?? 'bg-gray-700 text-gray-300'}`}>
                          {ROLE_LABELS[u.systemRole] ?? u.systemRole}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400">
                        {u.accessLevelName ?? <span className="text-gray-600 italic">Sem nível</span>}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${u.isActive ? 'bg-green-900 text-green-200' : 'bg-gray-700 text-gray-400'}`}>
                          {u.isActive ? 'Ativo' : 'Inativo'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-400">
                        {u.lastSignInAt ? new Date(u.lastSignInAt).toLocaleDateString('pt-BR') : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {canEdit && u.systemRole !== 'SUPER_ADMIN' && (
                            <button
                              onClick={() => setEditingUser(u)}
                              className="text-xs text-blue-400 hover:text-blue-300"
                            >
                              Editar
                            </button>
                          )}
                          {canToggle && u.systemRole !== 'SUPER_ADMIN' && (
                            <button
                              onClick={() => toggleStatus.mutate({ id: u.id, isActive: !u.isActive })}
                              className={`text-xs ${u.isActive ? 'text-red-400 hover:text-red-300' : 'text-green-400 hover:text-green-300'}`}
                            >
                              {u.isActive ? 'Inativar' : 'Ativar'}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Aviso SUPER_ADMIN */}
      <div className="rounded-xl bg-gray-800/50 border border-gray-700 px-4 py-3">
        <p className="text-xs text-gray-500">
          O <strong className="text-gray-400">SUPER_ADMIN</strong> é ativado diretamente no banco de dados e não depende de nível de acesso.
          Login com Google/Gmail não é permitido para usuários administrativos.
        </p>
      </div>

      {/* Modais */}
      {editingUser !== undefined && (
        <AdminUserModal
          user={editingUser === 'new' ? null : editingUser}
          accessLevels={accessLevels}
          onClose={() => setEditingUser(undefined)}
        />
      )}
    </div>
  )
}
