import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/core/auth/AuthContext'
import { useTenant } from '@/core/workspaces/TenantContext'
import { api } from '@/shared/services/api'
import type { DashboardModuleItem, DashboardModuleService } from '@/shared/types'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function profileLabel(tenantName: string, tenantType: string, role: string): string {
  if (tenantType === 'individual') return 'Perfil Individual'
  if (role === 'member') return `${tenantName} (Membro)`
  return tenantName
}

// ─── Badge ────────────────────────────────────────────────────────────────────

const badgeClass: Record<string, string> = {
  SUBSCRIBED: 'bg-primary-100 text-primary-700',
  FREE: 'bg-green-100 text-green-700',
  LOCKED: 'bg-gray-100 text-gray-400',
}

// ─── Module Card ──────────────────────────────────────────────────────────────

function ModuleCard({
  module,
  onClick,
}: {
  module: DashboardModuleItem
  onClick: () => void
}) {
  const isLocked = module.accessStatus === 'LOCKED'

  return (
    <button
      onClick={onClick}
      className={[
        'rounded-xl border p-5 text-left w-full transition-all flex flex-col',
        isLocked
          ? 'bg-gray-50 border-gray-100 opacity-50 grayscale hover:opacity-60'
          : 'bg-white border-gray-100 shadow-sm hover:border-primary-200 hover:shadow-md',
      ].join(' ')}
    >
      {/* Icon */}
      <div className="mb-3 relative w-12 h-12">
        {module.moduleIconPath ? (
          <img
            src={module.moduleIconPath}
            alt={module.moduleName}
            className="w-12 h-12 object-contain"
          />
        ) : (
          <div className="w-12 h-12 rounded-lg bg-gray-200 flex items-center justify-center text-gray-400 text-xl">
            📦
          </div>
        )}
        {isLocked && (
          <span className="absolute -bottom-1 -right-1 text-base leading-none">🔒</span>
        )}
      </div>

      {/* Name */}
      <h3
        className={[
          'font-semibold text-sm leading-snug',
          isLocked ? 'text-gray-400' : 'text-gray-900',
        ].join(' ')}
      >
        {module.moduleName}
      </h3>

      {/* Badge */}
      <span
        className={[
          'inline-block mt-2 px-2.5 py-0.5 rounded-full text-xs font-medium',
          badgeClass[module.accessStatus],
        ].join(' ')}
      >
        {module.badgeLabel}
      </span>

      {/* Service count / locked text */}
      {isLocked ? (
        <p className="text-xs text-gray-400 mt-2">Disponível para contratação</p>
      ) : (
        module.serviceCount > 0 && (
          <p className="text-xs text-gray-400 mt-2">
            {module.serviceCount} {module.serviceCount === 1 ? 'serviço' : 'serviços'}
          </p>
        )
      )}
    </button>
  )
}

// ─── Skeleton Card ────────────────────────────────────────────────────────────

function SkeletonCard() {
  return (
    <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm animate-pulse">
      <div className="w-12 h-12 rounded-lg bg-gray-200 mb-3" />
      <div className="h-4 bg-gray-200 rounded w-3/5 mb-2" />
      <div className="h-5 bg-gray-100 rounded-full w-2/5 mb-2" />
      <div className="h-3 bg-gray-100 rounded w-1/4" />
    </div>
  )
}

// ─── Service Item ─────────────────────────────────────────────────────────────

function ServiceItem({
  service,
  onOpen,
}: {
  service: DashboardModuleService
  onOpen: (service: DashboardModuleService) => void
}) {
  return (
    <button
      onClick={() => onOpen(service)}
      className={[
        'flex items-center gap-3 p-3 rounded-lg w-full text-left transition-colors',
        service.hasAccess
          ? 'bg-gray-50 hover:bg-primary-50 hover:border-primary-100 border border-transparent'
          : 'bg-gray-50 opacity-60 cursor-not-allowed border border-transparent',
      ].join(' ')}
    >
      <div className="flex-shrink-0 w-8 h-8 flex items-center justify-center">
        {service.serviceIconPath ? (
          <img
            src={service.serviceIconPath}
            alt={service.serviceName}
            className="w-8 h-8 object-contain"
          />
        ) : (
          <div className="w-8 h-8 rounded bg-gray-200 flex items-center justify-center text-gray-400 text-sm">
            ⚙
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-900">{service.serviceName}</p>
        {service.serviceDescription && (
          <p className="text-xs text-gray-500 truncate">{service.serviceDescription}</p>
        )}
      </div>
      {!service.hasAccess && (
        <span className="text-xs text-gray-400 flex-shrink-0">🔒</span>
      )}
    </button>
  )
}

// ─── Services Modal ───────────────────────────────────────────────────────────

function ServicesModal({
  module,
  onClose,
  onOpenService,
}: {
  module: DashboardModuleItem
  onClose: () => void
  onOpenService: (service: DashboardModuleService) => void
}) {
  // Group services by serviceGroupId/serviceGroupName
  const groupMap = new Map<string, { groupName: string; services: DashboardModuleService[] }>()
  const ungrouped: DashboardModuleService[] = []

  for (const svc of module.services) {
    if (svc.serviceGroupId && svc.serviceGroupName) {
      if (!groupMap.has(svc.serviceGroupId)) {
        groupMap.set(svc.serviceGroupId, { groupName: svc.serviceGroupName, services: [] })
      }
      groupMap.get(svc.serviceGroupId)!.services.push(svc)
    } else {
      ungrouped.push(svc)
    }
  }

  const groups = [...groupMap.entries()]

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-white rounded-2xl max-w-lg w-full max-h-[80vh] flex flex-col shadow-xl">
        {/* Header */}
        <div className="flex items-start justify-between p-6 border-b border-gray-100">
          <div className="flex items-center gap-4">
            {module.moduleIconPath && (
              <img
                src={module.moduleIconPath}
                alt={module.moduleName}
                className="w-10 h-10 object-contain flex-shrink-0"
              />
            )}
            <div>
              <h2 className="text-lg font-bold text-gray-900">{module.moduleName}</h2>
              {module.planName && (
                <p className="text-sm text-gray-500 mt-0.5">
                  Plano atual:{' '}
                  <span
                    className={[
                      'font-medium px-1.5 py-0.5 rounded text-xs',
                      badgeClass[module.accessStatus],
                    ].join(' ')}
                  >
                    {module.planName}
                  </span>
                </p>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-xl leading-none ml-4 mt-0.5 flex-shrink-0"
          >
            ✕
          </button>
        </div>

        {/* Services list */}
        <div className="flex-1 overflow-y-auto p-6">
          {module.services.length === 0 ? (
            <p className="text-sm text-gray-500">Nenhum serviço disponível para exibição.</p>
          ) : (
            <div className="space-y-6">
              {/* Ungrouped services first if no groups */}
              {groups.length === 0 && (
                <div className="space-y-2">
                  {ungrouped.map((svc) => (
                    <ServiceItem key={svc.serviceId} service={svc} onOpen={onOpenService} />
                  ))}
                </div>
              )}

              {/* Grouped services */}
              {groups.map(([groupId, group]) => (
                <div key={groupId}>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
                    {group.groupName}
                  </h3>
                  <div className="space-y-2">
                    {group.services.map((svc) => (
                      <ServiceItem key={svc.serviceId} service={svc} onOpen={onOpenService} />
                    ))}
                  </div>
                </div>
              ))}

              {/* Ungrouped at the bottom when mixed */}
              {groups.length > 0 && ungrouped.length > 0 && (
                <div>
                  <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
                    Outros serviços
                  </h3>
                  <div className="space-y-2">
                    {ungrouped.map((svc) => (
                      <ServiceItem key={svc.serviceId} service={svc} onOpen={onOpenService} />
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Locked Modal ─────────────────────────────────────────────────────────────

function LockedModal({
  module,
  onClose,
  onViewPlans,
}: {
  module: DashboardModuleItem
  onClose: () => void
  onViewPlans: () => void
}) {
  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-white rounded-2xl max-w-sm w-full shadow-xl p-6">
        <div className="text-center">
          <div className="text-4xl mb-4">🔒</div>
          <h2 className="text-lg font-bold text-gray-900 mb-1">Módulo não disponível</h2>
          <p className="text-sm font-medium text-gray-500 mb-3">{module.moduleName}</p>
          <p className="text-sm text-gray-600">
            Este módulo ainda não está ativo para o perfil atual. Escolha um plano para utilizar
            este módulo.
          </p>
        </div>
        <div className="flex gap-3 mt-6">
          <button
            onClick={onClose}
            className="flex-1 rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Fechar
          </button>
          <button
            onClick={onViewPlans}
            className="flex-1 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 transition-colors"
          >
            Ver planos
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Service Access Denied Modal ──────────────────────────────────────────────

function ServiceDeniedModal({
  serviceName,
  onClose,
}: {
  serviceName: string
  onClose: () => void
}) {
  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-white rounded-2xl max-w-sm w-full shadow-xl p-6">
        <div className="text-center">
          <div className="text-4xl mb-4">🚫</div>
          <h2 className="text-lg font-bold text-gray-900 mb-1">Acesso não permitido</h2>
          <p className="text-sm font-medium text-gray-500 mb-3">{serviceName}</p>
          <p className="text-sm text-gray-600">
            Você não possui permissão para acessar este serviço. Entre em contato com o
            administrador da conta.
          </p>
        </div>
        <button
          onClick={onClose}
          className="mt-6 w-full rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
        >
          Fechar
        </button>
      </div>
    </div>
  )
}

// ─── Dashboard Page ───────────────────────────────────────────────────────────

export function DashboardPage() {
  const { profile } = useAuth()
  const { currentTenant } = useTenant()
  const navigate = useNavigate()

  const [selectedModule, setSelectedModule] = useState<DashboardModuleItem | null>(null)
  const [lockedModule, setLockedModule] = useState<DashboardModuleItem | null>(null)
  const [deniedService, setDeniedService] = useState<DashboardModuleService | null>(null)

  const { data: modules = [], isLoading } = useQuery({
    queryKey: ['dashboard-modules', currentTenant?.tenant?.id],
    queryFn: async () => {
      const { data } = await api.get<DashboardModuleItem[]>('/api/v1/dashboard/modules')
      return data
    },
    enabled: !!currentTenant,
  })

  const firstName = profile?.full_name?.split(' ')[0] ?? ''
  const tenant = currentTenant?.tenant
  const role = currentTenant?.role ?? ''

  const currentProfileLabel = tenant
    ? profileLabel(tenant.name, tenant.type, role)
    : null

  function handleModuleClick(mod: DashboardModuleItem) {
    if (mod.accessStatus === 'LOCKED') setLockedModule(mod)
    else setSelectedModule(mod)
  }

  function handleOpenService(service: DashboardModuleService) {
    if (!service.hasAccess) {
      setDeniedService(service)
      return
    }
    if (!service.routeKey) return
    setSelectedModule(null)
    navigate(`/app/${service.routeKey}`)
  }

  return (
    <div className="space-y-8">
      {/* Welcome header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          {firstName ? `Olá, ${firstName}` : 'Bem-vindo'}
        </h1>
        {currentProfileLabel && (
          <p className="text-gray-500 mt-1">
            Perfil atual:{' '}
            <span className="font-medium text-gray-700">{currentProfileLabel}</span>
          </p>
        )}
      </div>

      {/* Modules section */}
      <div>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Módulos disponíveis</h2>

        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : modules.length === 0 ? (
          <div className="rounded-xl border border-gray-100 bg-white p-10 text-center shadow-sm">
            <p className="text-gray-500">Nenhum módulo cadastrado na plataforma.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {modules.map((mod) => (
              <ModuleCard key={mod.moduleId} module={mod} onClick={() => handleModuleClick(mod)} />
            ))}
          </div>
        )}
      </div>

      {/* Services modal */}
      {selectedModule && (
        <ServicesModal
          module={selectedModule}
          onClose={() => setSelectedModule(null)}
          onOpenService={handleOpenService}
        />
      )}

      {/* Service access denied modal */}
      {deniedService && (
        <ServiceDeniedModal
          serviceName={deniedService.serviceName}
          onClose={() => setDeniedService(null)}
        />
      )}

      {/* Locked modal */}
      {lockedModule && (
        <LockedModal
          module={lockedModule}
          onClose={() => setLockedModule(null)}
          onViewPlans={() => {
            setLockedModule(null)
            navigate('/app/billing/plans')
          }}
        />
      )}
    </div>
  )
}
