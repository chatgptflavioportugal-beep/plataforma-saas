package com.saas.platformsecurity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Constrói ModuleAccessTokens de teste com o mesmo shape emitido pelo auth-service. */
final class TestTokens {

    static final String SECRET = "test-module-access-token-secret-min-32-bytes-long";
    static final String TENANT_ID = UUID.randomUUID().toString();
    static final String USER_ID = UUID.randomUUID().toString();

    private TestTokens() {
    }

    static String moduleToken(String moduleSlug, List<String> permissions) {
        return moduleToken(moduleSlug, permissions, Map.of(), "MODULE_ACCESS", SECRET, 300);
    }

    static String moduleToken(
            String moduleSlug,
            List<String> permissions,
            Map<String, Object> limits,
            String tokenType,
            String secret,
            long expiresInSeconds
    ) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiresInSeconds * 1000);
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(USER_ID)
                .issuedAt(now)
                .expiration(exp)
                .claim("tokenType", tokenType)
                .claim("tenantId", TENANT_ID)
                .claim("moduleId", UUID.randomUUID().toString())
                .claim("moduleSlug", moduleSlug)
                .claim("planName", "pro")
                .claim("accessSource", "plan")
                .claim("permissions", permissions)
                .claim("limits", limits)
                .claim("permissionsVersion", 1)
                .signWith(key)
                .compact();
    }
}
