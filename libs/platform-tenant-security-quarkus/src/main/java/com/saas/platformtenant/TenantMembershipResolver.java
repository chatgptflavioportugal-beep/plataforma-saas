package com.saas.platformtenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta o vínculo usuário↔tenant. Cada serviço implementa isto sobre seu próprio DAO (SQL
 * nativo ou Panache) — a lib não assume nenhum ORM/estilo de persistência específico.
 */
public interface TenantMembershipResolver {

    Optional<TenantMembership> findByUserAndTenant(UUID userId, UUID tenantId);

    Optional<TenantMembership> findDefaultTenant(UUID userId);
}
