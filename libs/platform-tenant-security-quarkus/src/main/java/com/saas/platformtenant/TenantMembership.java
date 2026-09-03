package com.saas.platformtenant;

import java.util.UUID;

/** Vínculo ativo entre um usuário e um tenant — o mesmo dado que cada serviço já buscava em
 *  UserTenantTO/UserTenantResult, agora com um tipo comum para o {@link TenantMembershipResolver}. */
public record TenantMembership(UUID tenantId, String role) {
}
