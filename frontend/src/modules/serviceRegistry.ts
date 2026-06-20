import { lazy, type ComponentType } from 'react'

/**
 * Registry de serviços por routeKey com lazy loading.
 *
 * Cada entrada mapeia um routeKey para um componente carregado sob demanda.
 * O ModuleProvider deve envolver o componente para garantir que o ModuleAccessToken
 * esteja disponível quando o módulo for montado.
 *
 * Adicionar novos serviços aqui ao registrar um módulo na plataforma:
 *   'module-slug-service-slug': lazy(() => import('./module/ServicePage').then(m => ({ default: m.ServicePage })))
 */
const registry: Record<string, ReturnType<typeof lazy>> = {
  'pdf-pdf-merge': lazy(() =>
    import('./pdf/PdfMergePage').then((m) => ({ default: m.PdfMergePage }))
  ),
}

export function getServiceComponent(routeKey: string): ComponentType | null {
  return registry[routeKey] ?? null
}

/** Módulo pai de um routeKey (ex: 'pdf-pdf-merge' → 'pdf') */
export function getModuleSlugFromRouteKey(routeKey: string): string | null {
  const entry = Object.keys(registry).find((k) => k === routeKey)
  if (!entry) return null
  return entry.split('-')[0] ?? null
}
