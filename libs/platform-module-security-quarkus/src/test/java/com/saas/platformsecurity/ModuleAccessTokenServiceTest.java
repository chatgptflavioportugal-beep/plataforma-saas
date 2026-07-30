package com.saas.platformsecurity;

import com.saas.platformsecurity.exceptions.ExpiredModuleTokenException;
import com.saas.platformsecurity.exceptions.InvalidModuleTokenException;
import com.saas.platformsecurity.exceptions.ModuleMismatchException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ModuleAccessTokenServiceTest {

    @Inject
    ModuleAccessTokenService tokenService;

    @Test
    void validTokenIsDecodedIntoModuleContext() {
        String token = TestTokens.moduleToken(
                "pdf", List.of("pdf-merge"), Map.of("max-file-size", 50),
                "MODULE_ACCESS", TestTokens.SECRET, 300);

        ModuleContext context = tokenService.validate(token, "pdf");

        assertEquals(TestTokens.USER_ID, context.userId().toString());
        assertEquals(TestTokens.TENANT_ID, context.tenantId().toString());
        assertEquals("pdf", context.moduleSlug());
        assertEquals("pro", context.planName());
        assertTrue(context.hasPermission("pdf-merge"));
        assertFalse(context.hasPermission("pdf-split"));
        assertEquals(50, context.getLimit("max-file-size"));
        assertEquals("default", context.getLimit("missing", "default"));
    }

    @Test
    void expiredTokenThrows() {
        String token = TestTokens.moduleToken(
                "pdf", List.of(), Map.of(), "MODULE_ACCESS", TestTokens.SECRET, -10);

        assertThrows(ExpiredModuleTokenException.class, () -> tokenService.validate(token, "pdf"));
    }

    @Test
    void badSignatureThrows() {
        String token = TestTokens.moduleToken(
                "pdf", List.of(), Map.of(), "MODULE_ACCESS", "wrong-secret-wrong-secret-wrong", 300);

        assertThrows(InvalidModuleTokenException.class, () -> tokenService.validate(token, "pdf"));
    }

    @Test
    void malformedTokenThrows() {
        assertThrows(InvalidModuleTokenException.class, () -> tokenService.validate("not-a-jwt", "pdf"));
    }

    @Test
    void wrongTokenTypeThrows() {
        String token = TestTokens.moduleToken(
                "pdf", List.of(), Map.of(), "PROFILE_ACCESS", TestTokens.SECRET, 300);

        assertThrows(InvalidModuleTokenException.class, () -> tokenService.validate(token, "pdf"));
    }

    @Test
    void wrongModuleSlugThrows() {
        String token = TestTokens.moduleToken("whatsapp", List.of());

        assertThrows(ModuleMismatchException.class, () -> tokenService.validate(token, "pdf"));
    }

    @Test
    void nullExpectedModuleSlugSkipsModuleCheck() {
        String token = TestTokens.moduleToken("whatsapp", List.of());

        ModuleContext context = tokenService.validate(token, null);

        assertEquals("whatsapp", context.moduleSlug());
    }

    @Test
    void missingTokenThrows() {
        assertThrows(InvalidModuleTokenException.class, () -> tokenService.validate("", "pdf"));
    }
}
