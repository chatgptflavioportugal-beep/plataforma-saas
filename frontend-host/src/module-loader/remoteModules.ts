import { lazy, type ComponentType } from 'react'

export interface RemoteModuleProps {
  moduleToken: string
  onUnauthorized?: () => void
  onFeatureLocked?: (details: { requiredPlan?: string; feature?: string }) => void
}

/**
 * Carregadores dos Micro Frontends de módulo via Module Federation — cada um
 * baixado sob demanda (lazy) só quando o usuário abre aquele módulo. Nunca
 * importado estaticamente.
 *
 * Adicionar um novo módulo: registrar o remote em vite.config.ts (`remotes`)
 * e uma entrada aqui com o mesmo nome usado no `federation({ name })` do
 * Micro Frontend correspondente.
 */
const remoteLoaders: Record<string, () => Promise<{ default: ComponentType<RemoteModuleProps> }>> = {
  pdf: () => import('pdf_frontend/App'),
  whatsapp: () => import('whatsapp_frontend/App'),
}

export function getRemoteModule(moduleSlug: string): ComponentType<RemoteModuleProps> | null {
  const loader = remoteLoaders[moduleSlug]
  if (!loader) return null
  return lazy(loader)
}
