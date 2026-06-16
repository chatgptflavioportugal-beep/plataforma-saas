import type { ComponentType } from 'react'
import { PdfMergePage } from './pdf/PdfMergePage'

const registry: Record<string, ComponentType> = {
  'pdf-pdf-merge': PdfMergePage,
}

export function getServiceComponent(routeKey: string): ComponentType | null {
  return registry[routeKey] ?? null
}
