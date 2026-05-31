import { useState, useRef, useEffect, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '@/shared/services/api'
import { useTenant } from '@/core/workspaces/TenantContext'
import type {
  AccessLevel,
  AvailableModule,
  AvailableServiceGroup,
  AdminPermissionGroup,
  PermissionTreeResponse,
} from '@/shared/types'

// ─── Checkbox com estado indeterminado ────────────────────────────────────────

function TreeCheckbox({
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

// ─── Card de permissão ────────────────────────────────────────────────────────

function PermissionCard({
  name,
  subtitle,
  total,
  selected,
  variant = 'default',
  onConfigure,
}: {
  name: string
  subtitle?: string
  total: number
  selected: number
  variant?: 'default' | 'admin'
  onConfigure: () => void
}) {
  const isNone = selected === 0
  const isComplete = total > 0 && selected === total
  const isAdmin = variant === 'admin'

  const statusLabel = isNone ? 'Não configurado' : isComplete ? 'Completo' : 'Parcial'
  const statusClass = isNone
    ? 'bg-gray-100 text-gray-500'
    : isComplete
      ? 'bg-green-50 text-green-700'
      : isAdmin
        ? 'bg-indigo-50 text-indigo-700'
        : 'bg-blue-50 text-blue-700'

  const totalLabel = isAdmin
    ? `${total} permissão${total !== 1 ? 'ões' : ''} disponíve${total !== 1 ? 'is' : 'l'}`
    : `${total} serviço${total !== 1 ? 's' : ''} disponíve${total !== 1 ? 'is' : 'l'}`

  return (
    <div className={`rounded-xl border overflow-hidden ${isAdmin ? 'border-indigo-100' : 'border-gray-200'}`}>
      <div className={`flex items-center gap-3 px-4 py-3.5 ${isAdmin ? 'bg-indigo-50/40' : 'bg-gray-50'}`}>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-gray-900">{name}</span>
            <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${statusClass}`}>
              {statusLabel}
            </span>
          </div>
          {subtitle && <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>}
          <p className="text-xs text-gray-400 mt-0.5">
            {totalLabel}
            {selected > 0 && ` · ${selected} selecionado${selected !== 1 ? 's' : ''}`}
          </p>
        </div>
        <button
          type="button"
          onClick={onConfigure}
          className={`flex-shrink-0 text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors ${
            isAdmin
              ? 'border-indigo-200 text-indigo-700 hover:bg-indigo-50'
              : 'border-gray-300 text-gray-700 hover:bg-gray-100'
          }`}
        >
          Configurar
        </button>
      </div>
    </div>
  )
}

// ─── Modal de permissões de módulo ────────────────────────────────────────────

function ModulePermissionModal({
  module,
  initialSelected,
  onApply,
  onCancel,
}: {
  module: AvailableModule
  initialSelected: Set<string>
  onApply: (selected: Set<string>) => void
  onCancel: () => void
}) {
  const [selected, setSelected] = useState<Set<string>>(() => new Set(initialSelected))
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set())

  const hasGroups = module.serviceGroups.length > 0

  const allIds = useMemo(() => [
    ...module.serviceGroups.flatMap((g) => g.services.map((s) => s.serviceId)),
    ...module.ungroupedServices.map((s) => s.serviceId),
  ], [module])

  const allChecked = allIds.length > 0 && allIds.every((id) => selected.has(id))
  const someChecked = allIds.some((id) => selected.has(id)) && !allChecked

  function toggleAll() {
    setSelected(allChecked ? new Set() : new Set(allIds))
  }

  function toggleGroup(group: AvailableServiceGroup) {
    const ids = group.services.map((s) => s.serviceId)
    const allGroupChecked = ids.length > 0 && ids.every((id) => selected.has(id))
    const next = new Set(selected)
    allGroupChecked ? ids.forEach((id) => next.delete(id)) : ids.forEach((id) => next.add(id))
    setSelected(next)
  }

  function toggleService(id: string) {
    const next = new Set(selected)
    next.has(id) ? next.delete(id) : next.add(id)
    setSelected(next)
  }

  function toggleExpand(groupId: string) {
    setExpandedGroups((prev) => {
      const next = new Set(prev)
      next.has(groupId) ? next.delete(groupId) : next.add(groupId)
      return next
    })
  }

  return (
    <div className="fixed inset-0 bg-black/60 z-[60] flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-gray-100 flex-shrink-0">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Configurar permissões</h3>
            <p className="text-sm text-gray-500 mt-0.5">{module.moduleName}</p>
          </div>
          <button onClick={onCancel} className="text-gray-400 hover:text-gray-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Select all */}
        <div className="px-6 pt-4 pb-3 border-b border-gray-50 flex-shrink-0">
          <label className="flex items-center gap-3 cursor-pointer">
            <TreeCheckbox checked={allChecked} indeterminate={someChecked} onChange={toggleAll} />
            <span className="text-sm font-medium text-gray-700">Selecionar tudo</span>
            <span className="text-xs text-gray-400 ml-auto">{selected.size}/{allIds.length}</span>
          </label>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-6 py-2">
          {hasGroups ? (
            <div className="space-y-1 py-1">
              {/* Grupos */}
              {module.serviceGroups.map((group) => {
                const groupIds = group.services.map((s) => s.serviceId)
                const groupSel = groupIds.filter((id) => selected.has(id)).length
                const groupAllChecked = groupIds.length > 0 && groupSel === groupIds.length
                const groupSomeChecked = groupSel > 0 && !groupAllChecked
                const isExpanded = expandedGroups.has(group.groupId)

                return (
                  <div key={group.groupId} className="rounded-lg border border-gray-100 overflow-hidden">
                    <div className="flex items-center gap-2 px-3 py-2.5 bg-gray-50/80 hover:bg-gray-50 transition-colors">
                      <TreeCheckbox
                        checked={groupAllChecked}
                        indeterminate={groupSomeChecked}
                        onChange={() => toggleGroup(group)}
                      />
                      <button
                        type="button"
                        onClick={() => toggleExpand(group.groupId)}
                        className="flex items-center gap-2 flex-1 text-left"
                      >
                        <svg
                          className={`h-3.5 w-3.5 text-gray-400 flex-shrink-0 transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                          fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                        </svg>
                        <span className="text-sm font-semibold text-gray-800">{group.groupName}</span>
                        <span className={`text-xs ml-1 ${
                          groupAllChecked
                            ? 'text-green-600 font-medium'
                            : groupSomeChecked
                              ? 'text-blue-600 font-medium'
                              : 'text-gray-400'
                        }`}>
                          {groupSel}/{groupIds.length}
                        </span>
                      </button>
                    </div>
                    {isExpanded && (
                      <ul className="border-t border-gray-100 divide-y divide-gray-50">
                        {group.services.map((svc) => (
                          <li key={svc.serviceId}>
                            <label className="flex items-center gap-3 pl-9 pr-3 py-2 cursor-pointer hover:bg-gray-50/50 transition-colors">
                              <input
                                type="checkbox"
                                checked={selected.has(svc.serviceId)}
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

              {/* Serviços sem grupo */}
              {module.ungroupedServices.length > 0 && (
                <div>
                  {module.serviceGroups.length > 0 && (
                    <p className="text-xs text-gray-400 font-medium px-1 pt-2 pb-1">Outros serviços</p>
                  )}
                  <ul className="space-y-0.5">
                    {module.ungroupedServices.map((svc) => (
                      <li key={svc.serviceId}>
                        <label className="flex items-center gap-3 py-2 px-2 -mx-2 cursor-pointer hover:bg-gray-50 rounded-lg transition-colors">
                          <input
                            type="checkbox"
                            checked={selected.has(svc.serviceId)}
                            onChange={() => toggleService(svc.serviceId)}
                            className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 cursor-pointer"
                          />
                          <span className="text-sm text-gray-700">{svc.serviceName}</span>
                        </label>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ) : (
            // Sem grupos — lista plana (comportamento original)
            <ul className="space-y-0.5 py-1">
              {module.ungroupedServices.map((svc) => (
                <li key={svc.serviceId}>
                  <label className="flex items-center gap-3 py-2 px-2 -mx-2 cursor-pointer hover:bg-gray-50 rounded-lg transition-colors">
                    <input
                      type="checkbox"
                      checked={selected.has(svc.serviceId)}
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

        {/* Footer */}
        <div className="flex gap-3 px-6 py-4 border-t border-gray-100 flex-shrink-0">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => onApply(selected)}
            className="flex-1 rounded-xl bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
          >
            Aplicar permissões
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Modal de permissões administrativas ──────────────────────────────────────

function AdminPermissionModal({
  adminGroups,
  initialSelected,
  onApply,
  onCancel,
}: {
  adminGroups: AdminPermissionGroup[]
  initialSelected: Set<string>
  onApply: (selected: Set<string>) => void
  onCancel: () => void
}) {
  const [selected, setSelected] = useState<Set<string>>(() => new Set(initialSelected))
  const [search, setSearch] = useState('')
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set())

  const allKeys = adminGroups.flatMap((g) => g.permissions.map((p) => p.permissionKey))
  const allChecked = allKeys.length > 0 && allKeys.every((k) => selected.has(k))
  const someChecked = allKeys.some((k) => selected.has(k)) && !allChecked

  const filteredGroups = useMemo(() => {
    const q = search.toLowerCase().trim()
    if (!q) return adminGroups
    return adminGroups
      .map((g) => ({
        ...g,
        permissions: g.permissions.filter(
          (p) => p.label.toLowerCase().includes(q) || g.groupName.toLowerCase().includes(q)
        ),
      }))
      .filter((g) => g.permissions.length > 0)
  }, [adminGroups, search])

  function toggleAll() {
    setSelected(allChecked ? new Set() : new Set(allKeys))
  }

  function toggleGroup(group: AdminPermissionGroup) {
    const keys = group.permissions.map((p) => p.permissionKey)
    const allGroupSelected = keys.every((k) => selected.has(k))
    const next = new Set(selected)
    allGroupSelected ? keys.forEach((k) => next.delete(k)) : keys.forEach((k) => next.add(k))
    setSelected(next)
  }

  function togglePermission(key: string) {
    const next = new Set(selected)
    next.has(key) ? next.delete(key) : next.add(key)
    setSelected(next)
  }

  function toggleExpand(groupKey: string) {
    setExpandedGroups((prev) => {
      const next = new Set(prev)
      next.has(groupKey) ? next.delete(groupKey) : next.add(groupKey)
      return next
    })
  }

  return (
    <div className="fixed inset-0 bg-black/60 z-[60] flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-gray-100 flex-shrink-0">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Configurar permissões administrativas</h3>
            <p className="text-sm text-gray-500 mt-0.5">Administração do Perfil</p>
          </div>
          <button onClick={onCancel} className="text-gray-400 hover:text-gray-600">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="px-6 pt-4 pb-3 flex-shrink-0 space-y-3">
          <div className="relative">
            <svg
              className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
            </svg>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar permissão..."
              className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400"
            />
          </div>
          {!search && (
            <label className="flex items-center gap-3 cursor-pointer">
              <TreeCheckbox checked={allChecked} indeterminate={someChecked} onChange={toggleAll} />
              <span className="text-sm font-medium text-gray-700">Selecionar tudo</span>
              <span className="text-xs text-gray-400 ml-auto">{selected.size}/{allKeys.length}</span>
            </label>
          )}
        </div>

        <div className="flex-1 overflow-y-auto px-6 pb-2">
          <div className="space-y-2">
            {filteredGroups.map((group) => {
              const keys = group.permissions.map((p) => p.permissionKey)
              const groupSel = keys.filter((k) => selected.has(k)).length
              const groupAllChecked = groupSel === keys.length && keys.length > 0
              const groupSomeChecked = groupSel > 0 && !groupAllChecked
              const isExpanded = expandedGroups.has(group.groupKey) || !!search

              return (
                <div key={group.groupKey} className="rounded-lg border border-indigo-100 overflow-hidden">
                  <div className="flex items-center gap-2 px-3 py-2.5 bg-indigo-50/50 hover:bg-indigo-50 transition-colors">
                    <TreeCheckbox
                      checked={groupAllChecked}
                      indeterminate={groupSomeChecked}
                      onChange={() => toggleGroup(group)}
                    />
                    <button
                      type="button"
                      onClick={() => { if (!search) toggleExpand(group.groupKey) }}
                      className="flex items-center gap-2 flex-1 text-left"
                    >
                      {!search && (
                        <svg
                          className={`h-3.5 w-3.5 text-indigo-400 flex-shrink-0 transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                          strokeWidth={2.5}
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                        </svg>
                      )}
                      <span className="text-sm font-semibold text-gray-800">{group.groupName}</span>
                      <span
                        className={`text-xs ml-1 ${
                          groupAllChecked
                            ? 'text-green-600 font-medium'
                            : groupSomeChecked
                              ? 'text-indigo-600 font-medium'
                              : 'text-gray-400'
                        }`}
                      >
                        {groupSel}/{keys.length}
                      </span>
                    </button>
                  </div>
                  {isExpanded && (
                    <ul className="border-t border-indigo-50 divide-y divide-indigo-50/50">
                      {group.permissions.map((perm) => (
                        <li key={perm.permissionKey}>
                          <label className="flex items-center gap-3 pl-9 pr-3 py-2 cursor-pointer hover:bg-indigo-50/30 transition-colors">
                            <input
                              type="checkbox"
                              checked={selected.has(perm.permissionKey)}
                              onChange={() => togglePermission(perm.permissionKey)}
                              className="h-4 w-4 rounded border-indigo-200 text-indigo-600 focus:ring-indigo-500 cursor-pointer"
                            />
                            <span className="text-sm text-gray-700">{perm.label}</span>
                          </label>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )
            })}
          </div>
        </div>

        <div className="flex gap-3 px-6 py-4 border-t border-gray-100 flex-shrink-0">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => onApply(selected)}
            className="flex-1 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 transition-colors"
          >
            Aplicar permissões
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Formulário criar/editar (modal com cards) ────────────────────────────────

function AccessLevelFormModal({
  tenantId,
  level,
  availableModules,
  adminPermissions,
  onClose,
}: {
  tenantId: string
  level: AccessLevel | null
  availableModules: AvailableModule[]
  adminPermissions: AdminPermissionGroup[]
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(level?.name ?? '')
  const [description, setDescription] = useState(level?.description ?? '')
  const [status, setStatus] = useState<'ACTIVE' | 'INACTIVE'>(level?.status ?? 'ACTIVE')
  const [selectedServiceIds, setSelectedServiceIds] = useState<Set<string>>(
    () => new Set(level?.permissions.map((p) => p.serviceId) ?? [])
  )
  const [selectedAdminKeys, setSelectedAdminKeys] = useState<Set<string>>(
    () => new Set(level?.adminPermissions ?? [])
  )
  const [configuringModule, setConfiguringModule] = useState<AvailableModule | null>(null)
  const [configuringAdmin, setConfiguringAdmin] = useState(false)

  const isEdit = !!level

  const mutation = useMutation({
    mutationFn: async () => {
      const body = {
        name: name.trim(),
        description: description.trim() || null,
        serviceIds: Array.from(selectedServiceIds),
        adminPermissionKeys: Array.from(selectedAdminKeys),
      }
      if (isEdit) {
        await api.put(`/api/v1/tenants/${tenantId}/access-levels/${level!.id}`, body)
        if (status !== level!.status) {
          await api.patch(`/api/v1/tenants/${tenantId}/access-levels/${level!.id}/status`, { status })
        }
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

  const totalAdminKeys = adminPermissions.reduce((sum, g) => sum + g.permissions.length, 0)

  function getAllModuleServiceIds(mod: AvailableModule): string[] {
    return [
      ...mod.serviceGroups.flatMap((g) => g.services.map((s) => s.serviceId)),
      ...mod.ungroupedServices.map((s) => s.serviceId),
    ]
  }

  function getModuleSelected(mod: AvailableModule): Set<string> {
    return new Set(getAllModuleServiceIds(mod).filter((id) => selectedServiceIds.has(id)))
  }

  function applyModulePermissions(mod: AvailableModule, newSelected: Set<string>) {
    const moduleServiceIds = new Set(getAllModuleServiceIds(mod))
    const next = new Set(Array.from(selectedServiceIds).filter((id) => !moduleServiceIds.has(id)))
    newSelected.forEach((id) => next.add(id))
    setSelectedServiceIds(next)
    setConfiguringModule(null)
  }

  return (
    <>
      <div className="fixed inset-0 bg-black/40 z-50 flex items-start justify-center p-4 overflow-y-auto">
        <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl my-8">
          {/* Header */}
          <div className="flex items-center justify-between px-6 pt-6 pb-4 border-b border-gray-100">
            <h3 className="text-lg font-semibold text-gray-900">
              {isEdit ? 'Editar Nível de Acesso' : 'Criar Nível de Acesso'}
            </h3>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div className="px-6 py-5 space-y-5">
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

            {/* Status — somente na edição */}
            {isEdit && (
              <div className="flex items-center justify-between rounded-lg border border-gray-200 px-4 py-3">
                <div>
                  <p className="text-sm font-medium text-gray-700">Status</p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {status === 'ACTIVE'
                      ? 'Ativo · disponível para atribuição a membros'
                      : 'Inativo · não pode ser atribuído a novos membros'}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setStatus((s) => (s === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'))}
                  disabled={mutation.isPending}
                  className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none disabled:opacity-60 ${
                    status === 'ACTIVE' ? 'bg-primary-600' : 'bg-gray-200'
                  }`}
                >
                  <span
                    className={`inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ${
                      status === 'ACTIVE' ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            )}

            {/* Permissões */}
            <div>
              <h4 className="text-sm font-semibold text-gray-900 mb-4">Permissões</h4>

              {/* Módulos */}
              <div>
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2.5">
                  Módulos contratados
                </p>
                {availableModules.length === 0 ? (
                  <div className="rounded-lg bg-gray-50 border border-gray-200 px-4 py-4 text-center text-sm text-gray-400">
                    Nenhum módulo contratado. Assine um módulo para configurar permissões.
                  </div>
                ) : (
                  <div className="space-y-2">
                    {availableModules.map((mod) => (
                      <PermissionCard
                        key={mod.moduleId}
                        name={mod.moduleName}
                        total={getAllModuleServiceIds(mod).length}
                        selected={getModuleSelected(mod).size}
                        onConfigure={() => setConfiguringModule(mod)}
                      />
                    ))}
                  </div>
                )}
              </div>

              {/* Divisor */}
              <div className="flex items-center gap-3 my-5">
                <div className="flex-1 border-t border-gray-200" />
                <span className="text-xs text-gray-400 font-medium tracking-wide">Administração do Perfil</span>
                <div className="flex-1 border-t border-gray-200" />
              </div>

              {/* Administração */}
              <div>
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2.5">
                  Permissões administrativas
                </p>
                <PermissionCard
                  name="Administração do Perfil"
                  subtitle="Acesso a membros, convites, planos, assinaturas e configurações"
                  total={totalAdminKeys}
                  selected={selectedAdminKeys.size}
                  variant="admin"
                  onConfigure={() => setConfiguringAdmin(true)}
                />
              </div>
            </div>

            {errorMsg && (
              <p className="text-sm text-red-600 rounded-lg bg-red-50 px-3 py-2">{errorMsg}</p>
            )}
          </div>

          {/* Footer */}
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
              {mutation.isPending
                ? 'Salvando...'
                : isEdit
                  ? 'Salvar Nível de Acesso'
                  : 'Criar Nível de Acesso'}
            </button>
          </div>
        </div>
      </div>

      {/* Modal de módulo específico */}
      {configuringModule && (
        <ModulePermissionModal
          module={configuringModule}
          initialSelected={getModuleSelected(configuringModule)}
          onApply={(sel) => applyModulePermissions(configuringModule, sel)}
          onCancel={() => setConfiguringModule(null)}
        />
      )}

      {/* Modal administrativo */}
      {configuringAdmin && (
        <AdminPermissionModal
          adminGroups={adminPermissions}
          initialSelected={selectedAdminKeys}
          onApply={(sel) => {
            setSelectedAdminKeys(sel)
            setConfiguringAdmin(false)
          }}
          onCancel={() => setConfiguringAdmin(false)}
        />
      )}
    </>
  )
}

// ─── Card de nível de acesso (lista) ──────────────────────────────────────────

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
  const adminPermCount = level.adminPermissions?.length ?? 0
  const isActive = level.status === 'ACTIVE'
  const hasPermissions = serviceCount > 0 || adminPermCount > 0

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

        <div className="flex items-center gap-3 mt-1.5 text-xs text-gray-400 flex-wrap">
          {!hasPermissions ? (
            <span className="text-amber-500">Sem permissões configuradas</span>
          ) : (
            <>
              {serviceCount > 0 && (
                <span>
                  {serviceCount} serviço{serviceCount !== 1 ? 's' : ''} em{' '}
                  {moduleCount} módulo{moduleCount !== 1 ? 's' : ''}
                </span>
              )}
              {serviceCount > 0 && adminPermCount > 0 && <span>·</span>}
              {adminPermCount > 0 && (
                <span>{adminPermCount} perm. adm.</span>
              )}
            </>
          )}
          {level.memberCount > 0 && (
            <span>· {level.memberCount} membro{level.memberCount !== 1 ? 's' : ''}</span>
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

  const { data: permissionTree } = useQuery({
    queryKey: ['access-levels-tree', tenantId],
    queryFn: async () => {
      const { data } = await api.get<PermissionTreeResponse>(
        `/api/v1/tenants/${tenantId}/access-levels/available-modules`
      )
      return data
    },
    enabled: !!tenantId && canManage,
  })

  const availableModules = permissionTree?.modules ?? []
  const adminPermissions = permissionTree?.adminPermissions ?? []

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
        <AccessLevelFormModal
          tenantId={tenantId}
          level={editingLevel}
          availableModules={availableModules}
          adminPermissions={adminPermissions}
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
                Crie níveis de acesso para definir quais módulos, serviços e permissões administrativas cada membro poderá utilizar.
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
