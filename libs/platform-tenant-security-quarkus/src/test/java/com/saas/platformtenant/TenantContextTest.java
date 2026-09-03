package com.saas.platformtenant;

import com.saas.platformtenant.exceptions.TenantPathMismatchException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextTest {

    private static SecurityContext securityContextWith(Principal principal) {
        return new SecurityContext() {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public boolean isUserInRole(String role) { return false; }
            @Override public boolean isSecure() { return true; }
            @Override public String getAuthenticationScheme() { return "Bearer"; }
        };
    }

    @Test
    void resolveAndCheck_returnsContext_whenTenantMatches() {
        UUID tenantId = UUID.randomUUID();
        TenantContext tenantCtx = new TenantContext(UUID.randomUUID(), tenantId, "OWNER", "PRO", Set.of("pdf"), "active");
        SecurityContext ctx = securityContextWith(new TenantContextPrincipal(tenantCtx));

        TenantContext resolved = TenantContext.resolveAndCheck(ctx, tenantId);

        assertEquals(tenantId, resolved.getTenantId());
    }

    @Test
    void resolveAndCheck_throwsTenantPathMismatch_whenTenantDiffers() {
        TenantContext tenantCtx = new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "OWNER", "PRO", Set.of(), "active");
        SecurityContext ctx = securityContextWith(new TenantContextPrincipal(tenantCtx));

        assertThrows(TenantPathMismatchException.class, () -> TenantContext.resolveAndCheck(ctx, UUID.randomUUID()));
    }

    @Test
    void from_throwsIllegalState_whenPrincipalIsNotTenantContextPrincipal() {
        SecurityContext ctx = securityContextWith(() -> "not-a-tenant-principal");

        assertThrows(IllegalStateException.class, () -> TenantContext.from(ctx));
    }
}
