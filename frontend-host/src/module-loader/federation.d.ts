/**
 * Módulos remotos carregados via Module Federation em runtime (não existem em
 * tempo de build no Host — cada um é servido pelo próprio Micro Frontend).
 */
declare module 'pdf_frontend/App' {
  import type { ComponentType } from 'react'
  const Component: ComponentType<{
    moduleToken: string
    onUnauthorized?: () => void
    onFeatureLocked?: (details: { requiredPlan?: string; feature?: string }) => void
  }>
  export default Component
}

declare module 'whatsapp_frontend/App' {
  import type { ComponentType } from 'react'
  const Component: ComponentType<{
    moduleToken: string
    onUnauthorized?: () => void
    onFeatureLocked?: (details: { requiredPlan?: string; feature?: string }) => void
  }>
  export default Component
}
