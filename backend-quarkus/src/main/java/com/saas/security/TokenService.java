package com.saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gera e valida ProfileAccessToken (PAT) e ModuleAccessToken (MAT).
 *
 * Usa JJWT com HMAC-SHA256, isolado da verificação Supabase (SmallRye JWT / ES256).
 * Secrets distintos por tipo de token impedem uso cruzado indevido.
 */
@ApplicationScoped
public class TokenService {

    private static final String TOKEN_TYPE_CLAIM   = "tokenType";
    private static final String PROFILE_TYPE       = "PROFILE_ACCESS";
    private static final String MODULE_TYPE        = "MODULE_ACCESS";
    private static final String TENANT_ID_CLAIM    = "tenantId";
    private static final String PROFILE_TYPE_CLAIM = "profileType";
    private static final String PROFILE_ROLE_CLAIM = "profileRole";
    private static final String ACCESS_LEVEL_CLAIM = "accessLevelId";
    private static final String PERMISSIONS_CLAIM  = "permissions";
    private static final String PERM_VERSION_CLAIM = "permissionsVersion";
    private static final String MODULE_ID_CLAIM    = "moduleId";
    private static final String MODULE_SLUG_CLAIM  = "moduleSlug";
    private static final String PLAN_NAME_CLAIM    = "planName";
    private static final String ACCESS_SRC_CLAIM   = "accessSource";
    private static final String LIMITS_CLAIM       = "limits";

    @ConfigProperty(name = "app.token.profile-secret")
    String profileSecret;

    @ConfigProperty(name = "app.token.module-secret")
    String moduleSecret;

    @ConfigProperty(name = "app.token.profile-expiry-hours", defaultValue = "8")
    int profileExpiryHours;

    @ConfigProperty(name = "app.token.module-expiry-minutes", defaultValue = "30")
    int moduleExpiryMinutes;

    // ─── Geração ────────────────────────────────────────────────────────────────

    public String generateProfileToken(
            UUID userId,
            UUID tenantId,
            String profileType,
            String profileRole,
            String accessLevelId,
            List<String> permissions,
            int permissionsVersion
    ) {
        long now = System.currentTimeMillis();
        long exp = now + (long) profileExpiryHours * 3600 * 1000;

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .claim(TOKEN_TYPE_CLAIM,    PROFILE_TYPE)
                .claim(TENANT_ID_CLAIM,     tenantId.toString())
                .claim(PROFILE_TYPE_CLAIM,  profileType)
                .claim(PROFILE_ROLE_CLAIM,  profileRole)
                .claim(ACCESS_LEVEL_CLAIM,  accessLevelId)
                .claim(PERMISSIONS_CLAIM,   permissions)
                .claim(PERM_VERSION_CLAIM,  permissionsVersion)
                .signWith(profileKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateModuleToken(
            UUID userId,
            UUID tenantId,
            String moduleId,
            String moduleSlug,
            String planName,
            String accessSource,
            List<String> permissions,
            List<Map<String, Object>> limits,
            int permissionsVersion
    ) {
        long now = System.currentTimeMillis();
        long exp = now + (long) moduleExpiryMinutes * 60 * 1000;

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .claim(TOKEN_TYPE_CLAIM,   MODULE_TYPE)
                .claim(TENANT_ID_CLAIM,    tenantId.toString())
                .claim(MODULE_ID_CLAIM,    moduleId)
                .claim(MODULE_SLUG_CLAIM,  moduleSlug)
                .claim(PLAN_NAME_CLAIM,    planName)
                .claim(ACCESS_SRC_CLAIM,   accessSource)
                .claim(PERMISSIONS_CLAIM,  permissions)
                .claim(LIMITS_CLAIM,       limits)
                .claim(PERM_VERSION_CLAIM, permissionsVersion)
                .signWith(moduleKey(), Jwts.SIG.HS256)
                .compact();
    }

    // ─── Validação ───────────────────────────────────────────────────────────────

    public ModuleTokenClaims validateModuleToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(moduleKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenException("Module token expirado");
        } catch (JwtException e) {
            throw new TokenException("Module token inválido: " + e.getMessage());
        }

        if (!MODULE_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new TokenException("Token não é do tipo MODULE_ACCESS");
        }

        return new ModuleTokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(TENANT_ID_CLAIM, String.class)),
                claims.get(MODULE_ID_CLAIM,    String.class),
                claims.get(MODULE_SLUG_CLAIM,  String.class),
                claims.get(PLAN_NAME_CLAIM,    String.class),
                claims.get(ACCESS_SRC_CLAIM,   String.class),
                claims.get(PERMISSIONS_CLAIM,  List.class),
                claims.get(LIMITS_CLAIM,       List.class),
                claims.get(PERM_VERSION_CLAIM, Integer.class),
                claims.getExpiration()
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private SecretKey profileKey() {
        return Keys.hmacShaKeyFor(profileSecret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey moduleKey() {
        return Keys.hmacShaKeyFor(moduleSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ─── Record de claims do module token ────────────────────────────────────────

    public record ModuleTokenClaims(
            UUID userId,
            UUID tenantId,
            String moduleId,
            String moduleSlug,
            String planName,
            String accessSource,
            List<String> permissions,
            List<Map<String, Object>> limits,
            int permissionsVersion,
            Date expiresAt
    ) {
        public boolean hasPermission(String key) {
            return permissions != null && permissions.contains(key);
        }

        /** Milissegundos até expiração (negativo se expirado). */
        public long millisUntilExpiry() {
            return expiresAt.getTime() - System.currentTimeMillis();
        }
    }

    // ─── Exceção ─────────────────────────────────────────────────────────────────

    public static class TokenException extends RuntimeException {
        public TokenException(String message) {
            super(message);
        }
    }
}
