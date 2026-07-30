package com.saas.platformsecurity;

import com.saas.platformsecurity.exceptions.ExpiredModuleTokenException;
import com.saas.platformsecurity.exceptions.InvalidModuleTokenException;
import com.saas.platformsecurity.exceptions.ModuleMismatchException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Única classe da plataforma autorizada a decodificar o ModuleAccessToken (JWT HS256, emitido
 * pelo auth-service). Nenhum módulo Quarkus deve chamar Jwts.parser() diretamente — tudo passa
 * por aqui.
 *
 * <p>Configuração: {@code app.token.module-secret} (mesma propriedade já usada por
 * auth-service/usage-service), tipicamente mapeada de {@code MODULE_ACCESS_TOKEN_SECRET}.
 */
@ApplicationScoped
public class ModuleAccessTokenService {

    private static final Logger LOG = Logger.getLogger(ModuleAccessTokenService.class);

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String MODULE_TYPE = "MODULE_ACCESS";
    private static final String TENANT_ID_CLAIM = "tenantId";
    private static final String MODULE_ID_CLAIM = "moduleId";
    private static final String MODULE_SLUG_CLAIM = "moduleSlug";
    private static final String PLAN_NAME_CLAIM = "planName";
    private static final String ACCESS_SRC_CLAIM = "accessSource";
    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String PERM_VERSION_CLAIM = "permissionsVersion";
    private static final String LIMITS_CLAIM = "limits";

    @ConfigProperty(name = "app.token.module-secret")
    String moduleSecret;

    /**
     * Decodifica e valida um ModuleAccessToken, conferindo que ele pertence ao
     * {@code expectedModuleSlug}.
     *
     * @param expectedModuleSlug módulo esperado; passe {@code null} ou vazio para pular essa
     *                           checagem (ex.: um serviço, como o usage-service, que aceita
     *                           tokens de qualquer módulo).
     */
    public ModuleContext validate(String token, String expectedModuleSlug) {
        if (token == null || token.isBlank()) {
            LOG.warn("ModuleAccessToken ausente");
            throw new InvalidModuleTokenException("Token de acesso do módulo não informado.");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(moduleKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            LOG.warn("ModuleAccessToken expirado");
            throw new ExpiredModuleTokenException("Token de acesso do módulo expirado.");
        } catch (JwtException e) {
            LOG.warnf("ModuleAccessToken inválido — erro=%s", e.getMessage());
            throw new InvalidModuleTokenException("Token de acesso do módulo inválido.");
        }

        if (!MODULE_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            LOG.warnf("ModuleAccessToken com tokenType inesperado — tokenType=%s",
                    claims.get(TOKEN_TYPE_CLAIM, String.class));
            throw new InvalidModuleTokenException("Token de acesso do módulo inválido.");
        }

        String moduleSlug = claims.get(MODULE_SLUG_CLAIM, String.class);
        if (expectedModuleSlug != null && !expectedModuleSlug.isBlank()
                && !expectedModuleSlug.equals(moduleSlug)) {
            LOG.warnf("ModuleAccessToken não pertence ao módulo esperado — esperado=%s recebido=%s",
                    expectedModuleSlug, moduleSlug);
            throw new ModuleMismatchException("Token não pertence a este módulo.");
        }

        LOG.debugf("ModuleAccessToken válido — moduleSlug=%s tenantId=%s",
                moduleSlug, claims.get(TENANT_ID_CLAIM, String.class));

        return new ModuleContext(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(TENANT_ID_CLAIM, String.class)),
                claims.get(MODULE_ID_CLAIM, String.class),
                moduleSlug,
                claims.get(PLAN_NAME_CLAIM, String.class),
                claims.get(ACCESS_SRC_CLAIM, String.class),
                claims.get(PERMISSIONS_CLAIM, List.class),
                claims.get(LIMITS_CLAIM, Map.class),
                claims.get(PERM_VERSION_CLAIM, Integer.class),
                claims.getIssuedAt(),
                claims.getExpiration()
        );
    }

    private SecretKey moduleKey() {
        return Keys.hmacShaKeyFor(moduleSecret.getBytes(StandardCharsets.UTF_8));
    }
}
