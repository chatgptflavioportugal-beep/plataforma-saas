import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '@/shared/services/api'
import { useTenant } from '@/core/workspaces/TenantContext'
import type { AccessLevel, AvailableModule } from '@/shared/types'

// ─── Árvore de permissões ──────────────────────────────────────────────────────

function ModuleCheckbox({
  checked,
  indeterminate,
  onChange,
}: {
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
      className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 cursor-pointer"
    />
  )
}

function PermissionTree({
  availableModules,
  selectedServiceIds,
  onChange,
}: {
  availableModules: AvailableModule[]
  selectedServiceIds: Set<string>
  onChange: (next: Set<string>) => void
}) {
  if (availableModules.length === 0) {
    return (
      <div className="rounded-lg bg-gray-50 border border-gray-200 px-4 py-6 text-center text-sm text-gray-400">
        Nenhum módulo contratado. Assine um módulo para configurar permissões.
      </div>
    )
  }

  function toggleModule(mod: AvailableModule) {
    const ids = mod.services.map((s) => s.serviceId)
    const allSelected = ids.every((id) => selectedServiceIds.has(id))
    const next = new Set(selectedServiceIds)
    if (allSelected) {
      ids.forEach((id) => next.delete(id))
    } else {
      ids.forEach((id) => next.add(id))
    }
    onChange(next)
  }

  function toggleService(serviceId: string) {
    const next = new Set(selectedServiceIds)
    if (next.has(serviceId)) {
      next.delete(serviceId)
    } else {
      next.add(serviceId)
    }
    onChange(next)
  }

  return (
    <div className="space-y-3">
      {availableModules.map((mod) => {
        const ids = mod.services.map((s) => s.serviceId)
        const selected = ids.filter((id) => selectedServiceIds.has(id)).length
        const allSelected = selected === ids.length && ids.length > 0
        const someSelected = selected > 0 && !allSelected

        return (
          <div key={mod.moduleId} className="rounded-lg border border-gray-200 overflow-hidden">
            {/* Linha do módulo */}
            <label className="flex items-center gap-3 px-4 py-3 bg-gray-50 cursor-pointer hover:bg-gray-100 transition-colors">
              <ModuleCheckbox
                checked={allSelected}
                indeterminate={someSelected}
                onChange={() => toggleModule(mod)}
              />
              <div className="flex-1">
                <span className="text-sm font-semibold text-gray-800">{mod.moduleName}</span>
                <span className="ml-2 text-xs text-gray-400">
                  {selected}/{ids.length} serviço{ids.length !== 1 ? 's' : ''}
                </span>
              </div>
            </label>

            {/* Serviços do módulo */}
            {mod.services.length > 0 && (
              <ul className="divide-y divide-gray-100">
                {mod.services.map((svc) => (
                  <li key={svc.serviceId}>
                    <label className="flex items-center gap-3 px-4 py-2.5 pl-10 cursor-pointer hover:bg-gray-50 transition-colors">
                      <input
                        type="checkbox"
                        checked={selectedServiceIds.has(svc.serviceId)}
                        onChange={() => toggleService(svc.serviceId)}
                        className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 cursor-pointer"
                      />
                      <span className="text-sm text-gray-700">{svc.serviceName}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ─── Modal criar/editar ────────────────────────────────────────────────────────

function AccessLevelModal({
  tenantId,
  level,
  availableModules,
  onClose,
}: {
  tenantId: string
  level: AccessLevel | null
  availableModules: AvailableModule[]
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(level?.name ?? '')
  const [description, setDescription] = useState(level?.description ?? '')
  const [selectedServiceIds, setSelectedServiceIds] = useState<Set<string>>(
    () => new Set(level?.permissions.map((p) => p.serviceId) ?? [])
  )

  const isEdit = !!level

  const mutation = useMutation({
    mutationFn: async () => {
      const body = {
        name: name.trim(),
        description: description.trim() || null,
        serviceIds: Array.from(selectedServiceIds),
      }
      if (isEdit) {
        await api.put(`/api/v1/tenants/${tenantId}/access-levels/${level!.id}`, body)
      } else {
        await api.post(`/api/v1/tenants/${tenantId}/access-levels`, body)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['access-levels', tenantId] })
      onClose()
    },
  })

  const errorMsg =
    (mutation.error as { response?: { data?: { error?: string } } })?.response?.data?.error ??
    (mutation.isError ? 'Erro ao salvar nível de acesso' : null)

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-start justify-center p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg my-8 space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-6">
          <h3 className="text-lg font-semibold text-gray-900">
            {isEdit ? 'Editar Nível de Acesso' : 'Criar Nível de Acesso'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="px-6 space-y-4">
          {/* Nome */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Nome</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex: Operador PDF, Financeiro, Consulta"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              disabled={mutation.isPending}
            />
          </div>

          {/* Descrição */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Descrição <span className="text-gray-400 font-normal">(opcional)</span>
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Descreva o que este nível de acesso permite fazer..."
              rows={2}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
              disabled={mutation.isPending}
            />
          </div>

          {/* Permissões */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Permissões</label>
            <p className="text-xs text-gray-500 mb-3">
              Selecione quais módulos e serviços este nível de acesso pode utilizar.
              Apenas módulos com assinatura ativa são exibidos.
            </p>
            <PermissionTree
              availableModules={availableModules}
              selectedServiceIds={selectedServiceIds}
              onChange={setSelectedServiceIds}
            />
          </div>

          {errorMsg && (
            <p className="text-sm text-red-600 rounded-lg bg-red-50 px-3 py-2">{errorMsg}</p>
          )}
        </div>

        {/* Ações */}
        <div className="flex gap-3 px-6 pb-6">
          <button
            onClick={onClose}
            className="flex-1 rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancelar
          </button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !name.trim()}
            className="flex-1 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {mutation.isPending ? 'Salvando...' : isEdit ? 'Salvar' : 'Criar'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Card de nível de acesso ───────────────────────────────────────────────────

function AccessLevelCard({
  level,
  onEdit,
  onToggleStatus,
  onDelete,
}: {
  level: AccessLevel
  onEdit: () => void
  onToggleStatus: () => void
  onDelete: () => void
}) {
  const moduleCount = new Set(level.permissions.map((p) => p.moduleId)).size
  const serviceCount = level.permissions.length
  const isActive = level.status === 'ACTIVE'

  return (
    <div className="flex items-start gap-4 px-6 py-5">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-semibold text-gray-900">{level.name}</span>
          <span
            className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
              isActive ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'
            }`}
          >
            {isActive ? 'Ativo' : 'Inativo'}
          </span>
        </div>

        {level.description && (
          <p className="text-xs text-gray-500 mt-0.5 truncate">{level.description}</p>
        )}

        <div className="flex items-center gap-3 mt-1.5 text-xs text-gray-400">
          {serviceCount > 0 ? (
            <span>
              {serviceCount} serviço{serviceCount !== 1 ? 's' : ''} em{' '}
              {moduleCount} módulo{moduleCount !== 1 ? 's' : ''}
            </span>
          ) : (
            <span className="text-amber-500">Sem permissões configuradas</span>
          )}
          {level.memberCount > 0 && (
            <span>
              · {level.memberCount} membro{level.memberCount !== 1 ? 's' : ''}
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 flex-shrink-0">
        <button
          onClick={onEdit}
          className="text-xs text-gray-500 hover:text-gray-700 font-medium px-2 py-1 rounded hover:bg-gray-100 transition-colors"
        >
          Editar
        </button>
        <button
          onClick={onToggleStatus}
          className="text-xs text-gray-500 hover:text-gray-700 font-medium px-2 py-1 rounded hover:bg-gray-100 transition-colors"
        >
          {isActive ? 'Inativar' : 'Ativar'}
        </button>
        {level.memberCount === 0 && (
          <button
            onClick={onDelete}
            className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-1 rounded hover:bg-red-50 transition-colors"
          >
            Excluir
          </button>
        )}
      </div>
    </div>
  )
}

// ─── Página principal ──────────────────────────────────────────────────────────

export function AccessLevelsPage() {
  const { activeTenantId, currentTenant } = useTenant()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const tenantId = activeTenantId!
  const myRole = currentTenant?.role
  const canManage = myRole === 'owner' || myRole === 'admin'

  const [showModal, setShowModal] = useState(false)
  const [editingLevel, setEditingLevel] = useState<AccessLevel | null>(null)

  const { data: accessLevels = [], isLoading } = useQuery({
    queryKey: ['access-levels', tenantId],
    queryFn: async () => {
      const { data } = await api.get<AccessLevel[]>(`/api/v1/tenants/${tenantId}/access-levels`)
      return data
    },
    enabled: !!tenantId,
  })

  const { data: availableModules = [] } = useQuery({
    queryKey: ['access-levels-modules', tenantId],
    queryFn: async () => {
      const { data } = await api.get<AvailableModule[]>(
        `/api/v1/tenants/${tenantId}/access-levels/available-modules`
      )
      return data
    },
    enabled: !!tenantId && canManage,
  })

  const toggleStatusMutation = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: 'ACTIVE' | 'INACTIVE' }) => {
      await api.patch(`/api/v1/tenants/${tenantId}/access-levels/${id}/status`, { status })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['access-levels', tenantId] }),
  })

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/v1/tenants/${tenantId}/access-levels/${id}`)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['access-levels', tenantId] }),
  })

  function handleOpenCreate() {
    setEditingLevel(null)
    setShowModal(true)
  }

  function handleOpenEdit(level: AccessLevel) {
    setEditingLevel(level)
    setShowModal(true)
  }

  function handleToggleStatus(level: AccessLevel) {
    const next = level.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    toggleStatusMutation.mutate({ id: level.id, status: next })
  }

  function handleDelete(level: AccessLevel) {
    if (confirm(`Excluir o nível de acesso "${level.name}"?`)) {
      deleteMutation.mutate(level.id)
    }
  }

  return (
    <div className="space-y-8 max-w-3xl">
      {showModal && (
        <AccessLevelModal
          tenantId={tenantId}
          level={editingLevel}
          availableModules={availableModules}
          onClose={() => setShowModal(false)}
        />
      )}

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Níveis de Acesso</h1>
          <p className="text-sm text-gray-500 mt-1">{currentTenant?.tenant?.name}</p>
        </div>
        {canManage && (
          <button
            onClick={handleOpenCreate}
            className="flex items-center gap-2 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors shadow-sm"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            Criar Nível de Acesso
          </button>
        )}
      </div>

      {/* Lista */}
      <div className="rounded-xl bg-white border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900">
            Níveis cadastrados
            <span className="ml-2 text-gray-400 font-normal">({accessLevels.length})</span>
          </h2>
          <button
            onClick={() => navigate('/app/settings/members')}
            className="text-xs text-primary-600 hover:text-primary-700 font-medium"
          >
            ← Voltar para Membros
          </button>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-10">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
          </div>
        ) : accessLevels.length === 0 ? (
          <div className="py-12 text-center space-y-4 px-6">
            <div className="h-12 w-12 rounded-full bg-indigo-50 flex items-center justify-center mx-auto">
              <svg className="h-6 w-6 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
              </svg>
            </div>
            <div>
              <p className="text-sm font-medium text-gray-900">Nenhum nível de acesso cadastrado</p>
              <p className="text-sm text-gray-500 mt-1">
                Crie níveis de acesso para definir quais módulos e serviços cada membro poderá utilizar.
              </p>
            </div>
          </div>
        ) : (
          <ul className="divide-y divide-gray-50">
            {accessLevels.map((level) => (
              <li key={level.id}>
                <AccessLevelCard
                  level={level}
                  onEdit={() => handleOpenEdit(level)}
                  onToggleStatus={() => handleToggleStatus(level)}
                  onDelete={() => handleDelete(level)}
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
