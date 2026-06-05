import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useAuth } from '@/core/auth/AuthContext'
import type {
  AdminAccessLevel,
  AdminAccessLevelDetail,
  AdminPermissionGroup,
  AdminPermissionTreeResponse,
} from '@/shared/types'

// ─── Checkbox com estado indeterminado ────────────────────────────────────────

function TreeCheckbox({ checked, indeterminate, onChange }: {
  checked: boolean
  indeterminate: boolean
  onChange: () => void
}) {
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = indeterminate
  }, [indeterminate])

  return (
    <input
      ref={ref}
      type="checkbox"
      checked={checked}
      onChange={onChange}
      className="h-4 w-4 rounded border-gray-600 bg-gray-700 text-blue-500 focus:ring-blue-500 cursor-pointer"
    />
  )
}

// ─── Card de grupo de permissão ────────────────────────────────────────────────

function PermGroupCard({ group, selectedKeys, onConfigure }: {
  group: AdminPermissionGroup
  selectedKeys: Set<string>
  onConfigure: () => void
}) {
  const total    = group.permissions.length
  const selected = group.permissions.filter(p => selectedKeys.has(p.permissionKey)).length
  const isNone   = selected === 0
  const isFull   = total > 0 && selected === total

  const statusLabel = isNone ? 'Não configurado' : isFull ? 'Completo' : 'Parcial'
  const statusClass = isNone
    ? 'bg-gray-700 text-gray-400'
    : isFull
    ? 'bg-green-900/60 text-green-300'
    : 'bg-blue-900/60 text-blue-300'

  return (
    <div className="rounded-xl border border-gray-700 overflow-hidden">
      <div className="flex items-center gap-3 px-4 py-3.5 bg-gray-750">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-white">{group.groupName}</span>
            <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${statusClass}`}>
              {statusLabel}
            </span>
          </div>
          <p className="text-xs text-gray-500 mt-0.5">
            {total} permissão{total !== 1 ? 'ões' : ''} disponíve{total !== 1 ? 'is' : 'l'}
            {selected > 0 && ` · ${selected} selecionada${selected !== 1 ? 's' : ''}`}
          </p>
        </div>
        <button
          type="button"
          onClick={onConfigure}
          className="flex-shrink-0 text-xs font-medium px-3 py-1.5 rounded-lg border border-gray-600 text-gray-300 hover:bg-gray-700"
        >
          Configurar
        </button>
      </div>
    </div>
  )
}

// ─── Modal de permissões de grupo ────────────────────────────────────────────

function PermGroupModal({ group, selectedKeys, onClose, onSave }: {
  group: AdminPermissionGroup
  selectedKeys: Set<string>
  onClose: () => void
  onSave: (groupKey: string, keys: Set<string>) => void
}) {
  const [local, setLocal] = useState(new Set(selectedKeys))
  const groupKeys = group.permissions.map(p => p.permissionKey)
  const allSelected = groupKeys.every(k => local.has(k))
  const someSelected = groupKeys.some(k => local.has(k))

  function toggleGroup() {
    setLocal(prev => {
      const next = new Set(prev)
      if (allSelected) {
        groupKeys.forEach(k => next.delete(k))
      } else {
        groupKeys.forEach(k => next.add(k))
      }
      return next
    })
  }

  function togglePerm(key: string) {
    setLocal(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key); else next.add(key)
      return next
    })
  }

  function handleSave() {
    onSave(group.groupKey, local)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-sm rounded-xl bg-gray-800 border border-gray-700 shadow-2xl">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-700">
          <h3 className="text-sm font-semibold text-white">{group.groupName}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">&times;</button>
        </div>
        <div className="p-4">
          <label className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-gray-700 cursor-pointer mb-2">
            <TreeCheckbox
              checked={allSelected}
              indeterminate={!allSelected && someSelected}
              onChange={toggleGroup}
            />
            <span className="text-sm font-medium text-white">Selecionar todos</span>
          </label>
          <div className="border-t border-gray-700 pt-2 space-y-1">
            {group.permissions.map(p => (
              <label key={p.permissionKey} className="flex items-center gap-3 px-2 py-1.5 rounded-lg hover:bg-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={local.has(p.permissionKey)}
                  onChange={() => togglePerm(p.permissionKey)}
                  className="h-4 w-4 rounded border-gray-600 bg-gray-700 text-blue-500 focus:ring-blue-500 cursor-pointer"
                />
                <span className="text-sm text-gray-300 flex-1">
                  {p.label}
                  <span className="ml-1.5 text-xs text-gray-500 font-mono">({p.permissionKey})</span>
                </span>
              </label>
            ))}
          </div>
        </div>
        <div className="flex gap-3 px-5 pb-5">
          <button onClick={onClose} className="flex-1 rounded-lg border border-gray-600 text-gray-300 text-sm py-2 hover:bg-gray-700">
            Cancelar
          </button>
          <button onClick={handleSave} className="flex-1 rounded-lg bg-blue-600 text-white text-sm py-2 hover:bg-blue-500">
            Confirmar
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Modal criar/editar nível ─────────────────────────────────────────────────

function AccessLevelModal({ level, permissionGroups, onClose }: {
  level: AdminAccessLevelDetail | null
  permissionGroups: AdminPermissionGroup[]
  onClose: () => void
}) {
  const qc = useQueryClient()
  const isEdit = !!level

  const [name, setName] = useState(level?.name ?? '')
  const [description, setDescription] = useState(level?.description ?? '')
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set(level?.permissionKeys ?? []))
  const [configuringGroup, setConfiguringGroup] = useState<AdminPermissionGroup | null>(null)
  const [error, setError] = useState('')

  function handleGroupSave(groupKey: string, keys: Set<string>) {
    setSelectedKeys(prev => {
      const next = new Set(prev)
      // Remover chaves antigas do grupo
      permissionGroups
        .find(g => g.groupKey === groupKey)
        ?.permissions.forEach(p => next.delete(p.permissionKey))
      // Adicionar chaves novas
      keys.forEach(k => next.add(k))
      return next
    })
  }

  const mutation = useMutation({
    mutationFn: async () => {
      const body = {
        name: name.trim(),
        description: description.trim() || null,
        permissionKeys: Array.from(selectedKeys),
      }
      if (isEdit) {
        await api.put(`/api/v1/admin/access-levels/${level!.id}`, body)
      } else {
        await api.post('/api/v1/admin/access-levels', body)
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-access-levels'] })
      onClose()
    },
    onError: (err: any) => {
      setError(err?.response?.data?.error ?? 'Erro ao salvar nível de acesso')
    },
  })

  const totalSelected = selectedKeys.size

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-auto">
      <div className="w-full max-w-xl rounded-xl bg-gray-800 border border-gray-700 shadow-2xl my-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700">
          <h2 className="text-lg font-semibold text-white">
            {isEdit ? 'Editar Nível de Acesso' : 'Novo Nível de Acesso Admin'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white text-xl leading-none">&times;</button>
        </div>

        <div className="p-6 space-y-5">
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Nome *</label>
            <input
              type="text"
              value={name}
              onChange={e => { setName(e.target.value); setError('') }}
              className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500"
              placeholder="Ex: Financeiro, Suporte, Administrador Operacional"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1">Descrição</label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              className="w-full rounded-lg bg-gray-700 border border-gray-600 text-white text-sm px-3 py-2 focus:outline-none focus:border-blue-500 resize-none"
              rows={2}
              placeholder="Descreva o que este nível de acesso permite..."
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-white">Permissões</h3>
              <span className="text-xs text-gray-500">{totalSelected} selecionada{totalSelected !== 1 ? 's' : ''}</span>
            </div>
            <div className="space-y-2">
              {permissionGroups.map(group => (
                <PermGroupCard
                  key={group.groupKey}
                  group={group}
                  selectedKeys={selectedKeys}
                  onConfigure={() => setConfiguringGroup(group)}
                />
              ))}
            </div>
          </div>

          {error && <p className="text-sm text-red-400">{error}</p>}
        </div>

        <div className="flex gap-3 px-6 pb-6">
          <button onClick={onClose} className="flex-1 rounded-lg border border-gray-600 text-gray-300 text-sm font-medium py-2 hover:bg-gray-700">
            Cancelar
          </button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !name.trim()}
            className="flex-1 rounded-lg bg-blue-600 text-white text-sm font-medium py-2 hover:bg-blue-500 disabled:opacity-50"
          >
            {mutation.isPending ? 'Salvando...' : isEdit ? 'Salvar' : 'Criar Nível de Acesso'}
          </button>
        </div>
      </div>

      {configuringGroup && (
        <PermGroupModal
          group={configuringGroup}
          selectedKeys={selectedKeys}
          onClose={() => setConfiguringGroup(null)}
          onSave={handleGroupSave}
        />
      )}
    </div>
  )
}

// ─── Card de nível ─────────────────────────────────────────────────────────────

function AccessLevelCard({ level, onEdit, onToggle, canEdit, canToggle }: {
  level: AdminAccessLevel
  onEdit: () => void
  onToggle: () => void
  canEdit: boolean
  canToggle: boolean
}) {
  const isActive = level.status === 'ACTIVE'

  return (
    <div className={`rounded-xl border overflow-hidden ${isActive ? 'border-gray-700' : 'border-gray-700/50'}`}>
      <div className="p-4">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="text-sm font-semibold text-white">{level.name}</h3>
              <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${isActive ? 'bg-green-900/60 text-green-300' : 'bg-gray-700 text-gray-400'}`}>
                {isActive ? 'Ativo' : 'Inativo'}
              </span>
            </div>
            {level.description && (
              <p className="mt-1 text-xs text-gray-400 line-clamp-2">{level.description}</p>
            )}
            <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
              <span>{level.permCount} permissão{level.permCount !== 1 ? 'ões' : ''}</span>
              <span>{level.userCount} usuário{level.userCount !== 1 ? 's' : ''}</span>
            </div>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            {canEdit && (
              <button onClick={onEdit} className="text-xs text-blue-400 hover:text-blue-300">
                Editar
              </button>
            )}
            {canToggle && (
              <button onClick={onToggle} className={`text-xs ${isActive ? 'text-red-400 hover:text-red-300' : 'text-green-400 hover:text-green-300'}`}>
                {isActive ? 'Inativar' : 'Ativar'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export function AdminAccessLevelsPage() {
  const { isSuperAdmin, hasAdminPermission } = useAuth()
  const qc = useQueryClient()
  const [editingLevel, setEditingLevel] = useState<string | 'new' | null>(null)
  const [statusFilter, setStatusFilter] = useState('ACTIVE')

  const canCreate = isSuperAdmin || hasAdminPermission('admin.access_levels.create')
  const canEdit   = isSuperAdmin || hasAdminPermission('admin.access_levels.edit')
  const canToggle = isSuperAdmin || hasAdminPermission('admin.access_levels.activate')

  const { data: levels = [], isLoading } = useQuery({
    queryKey: ['admin-access-levels', statusFilter],
    queryFn: async () => {
      const params = statusFilter ? `?status=${statusFilter}` : ''
      const { data } = await api.get<AdminAccessLevel[]>(`/api/v1/admin/access-levels${params}`)
      return data
    },
    staleTime: 30_000,
  })

  const { data: treeData } = useQuery({
    queryKey: ['admin-permission-tree'],
    queryFn: async () => {
      const { data } = await api.get<AdminPermissionTreeResponse>('/api/v1/admin/access-levels/permission-tree')
      return data
    },
    staleTime: 5 * 60_000,
  })

  const { data: editingDetail } = useQuery({
    queryKey: ['admin-access-level-detail', editingLevel],
    queryFn: async () => {
      if (!editingLevel || editingLevel === 'new') return null
      const { data } = await api.get<AdminAccessLevelDetail>(`/api/v1/admin/access-levels/${editingLevel}`)
      return data
    },
    enabled: !!editingLevel && editingLevel !== 'new',
    staleTime: 0,
  })

  const toggleStatus = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      await api.patch(`/api/v1/admin/access-levels/${id}/status`, { status })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-access-levels'] }),
  })

  const permissionGroups = treeData?.groups ?? []
  const showModal = editingLevel === 'new' || (editingLevel && editingDetail)

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Níveis de Acesso Admin</h1>
          <p className="mt-1 text-sm text-gray-400">
            Defina conjuntos de permissões para usuários administrativos da plataforma.
          </p>
        </div>
        {canCreate && (
          <button
            onClick={() => setEditingLevel('new')}
            className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
          >
            + Novo Nível
          </button>
        )}
      </div>

      {/* Filtro de status */}
      <div className="flex gap-2">
        {[['', 'Todos'], ['ACTIVE', 'Ativos'], ['INACTIVE', 'Inativos']].map(([val, label]) => (
          <button
            key={val}
            onClick={() => setStatusFilter(val)}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
              statusFilter === val
                ? 'bg-gray-700 text-white'
                : 'text-gray-400 hover:bg-gray-800 hover:text-white'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="text-gray-400">Carregando...</p>
      ) : levels.length === 0 ? (
        <div className="rounded-xl bg-gray-800 border border-gray-700 px-6 py-12 text-center">
          <p className="text-sm text-gray-500">Nenhum nível de acesso encontrado.</p>
          {canCreate && (
            <button
              onClick={() => setEditingLevel('new')}
              className="mt-4 text-sm text-blue-400 hover:text-blue-300"
            >
              Criar o primeiro nível
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {levels.map(level => (
            <AccessLevelCard
              key={level.id}
              level={level}
              canEdit={canEdit}
              canToggle={canToggle}
              onEdit={() => setEditingLevel(level.id)}
              onToggle={() => toggleStatus.mutate({
                id: level.id,
                status: level.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
              })}
            />
          ))}
        </div>
      )}

      {showModal && permissionGroups.length > 0 && (
        <AccessLevelModal
          level={editingLevel === 'new' ? null : (editingDetail ?? null)}
          permissionGroups={permissionGroups}
          onClose={() => setEditingLevel(null)}
        />
      )}
    </div>
  )
}
