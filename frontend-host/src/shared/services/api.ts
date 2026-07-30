export function setActiveTenant(tenantId: string) {
  sessionStorage.setItem('active_tenant_id', tenantId)
}

export function getActiveTenantId(): string | null {
  return sessionStorage.getItem('active_tenant_id')
}
