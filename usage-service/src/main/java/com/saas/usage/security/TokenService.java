package com.saas.usage.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validação local do ModuleAccessToken (MAT), emitido pelo auth-service.
 * Mesmo padrão usado pelo backend-quarkus/backend-python: nenhum round-trip de rede,
 * apenas verificação de assinatura HMAC com o segredo compartilhado.
 */
@ApplicationScoped
public class TokenService {

    private static final String TOKEN_TYPE_CLAIM   = "tokenType";
    private static final String MODULE_TYPE        = "MODULE_ACCESS";
    private static final String TENANT_ID_CLAIM    = "tenantId";
    private static final String MODULE_ID_CLAIM    = "moduleId";
    private static final String MODULE_SLUG_CLAIM  = "moduleSlug";
    private static final String PLAN_NAME_CLAIM    = "planName";
    private static final String ACCESS_SRC_CLAIM   = "accessSource";
    private static final String PERMISSIONS_CLAIM  = "permissions";
    private static final String PERM_VERSION_CLAIM = "permissionsVersion";
    private static final String LIMITS_CLAIM       = "limits";

    @ConfigProperty(name = "app.token.module-secret")
    String moduleSecret;

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
                claims.get(LIMITS_CLAIM,       Map.class),
                claims.get(PERM_VERSION_CLAIM, Integer.class),
                claims.getExpiration()
        );
    }

    private SecretKey moduleKey() {
        return Keys.hmacShaKeyFor(moduleSecret.getBytes(StandardCharsets.UTF_8));
    }

    public record ModuleTokenClaims(
            UUID userId,
            UUID tenantId,
            String moduleId,
            String moduleSlug,
            String planName,
            String accessSource,
            List<String> permissions,
            Map<String, Object> limits,
            int permissionsVersion,
            java.util.Date expiresAt
    ) {
        public Object getLimit(String code) {
            return limits != null ? limits.get(code) : null;
        }
    }

    public static class TokenException extends RuntimeException {
        public TokenException(String message) {
            super(message);
        }
    }
}
