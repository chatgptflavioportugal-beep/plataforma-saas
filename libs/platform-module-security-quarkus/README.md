# platform-module-security-quarkus

Biblioteca compartilhada de autenticacao/autorizacao para os backends **Quarkus** de modulo da
plataforma. Equivalente Java da biblioteca Python [`platform-module-security`](../platform-module-security)
— mesma responsabilidade (validar o `ModuleAccessToken`), mesmos conceitos (`ModuleContext`,
permissoes, limites), adaptados aos idiomas de CDI/JAX-RS.

Hoje nenhum microsservico Quarkus da plataforma processa requisicoes de modulo diretamente
(pdf-service/whatsapp-service sao Python) — esta lib existe para o dia em que um novo modulo for
implementado em Quarkus em vez de Python, para que ele **nao** reimplemente a validacao do JWT
(o jeito que `usage-service/src/main/java/com/saas/usage/security/TokenService.java` faz hoje,
de forma local e duplicavel).

## O token

Mesmo `ModuleAccessToken` documentado na lib Python: JWT **HS256**, sem `iss`/`aud`, claims
`sub`, `tenantId`, `moduleId`, `moduleSlug`, `planName`, `accessSource`, `permissions`,
`limits`, `permissionsVersion`, `iat`, `exp`. Emitido por
`auth-service/.../security/TokenService.java`.

## Instalacao

Ainda nao existe um repositorio Maven privado. Enquanto isso:

```bash
cd libs/platform-module-security-quarkus
mvn install -DskipTests
```

Isso publica `com.saas:platform-module-security-quarkus:1.0.0-SNAPSHOT` no seu `~/.m2`
local. O servico consumidor entao declara a dependencia normalmente:

```xml
<dependency>
    <groupId>com.saas</groupId>
    <artifactId>platform-module-security-quarkus</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Em Docker, o build multi-stage do servico consumidor precisa copiar o codigo-fonte desta lib
para dentro do contexto de build e rodar `mvn install -N -DskipTests` nela **antes** de compilar
o servico — mesmo principio do `COPY libs/platform-module-security` usado nos Dockerfiles do
pdf-service/whatsapp-service, adaptado para Maven (contexto de build do `docker-compose.yml`
precisa ser a raiz do repositorio, nao a pasta do servico).

## Configuracao

Mesma propriedade ja usada por `auth-service`/`usage-service` — nenhuma variavel nova:

```properties
app.token.module-secret=${MODULE_ACCESS_TOKEN_SECRET}
```

## Uso

```java
import com.saas.platformsecurity.CurrentModuleContext;
import com.saas.platformsecurity.ModuleContext;
import com.saas.platformsecurity.RequireModuleAccess;

@Path("/pdf")
public class PdfResource {

    @Inject
    CurrentModuleContext current;

    @POST
    @Path("/merge")
    @RequireModuleAccess(moduleSlug = "pdf", permission = "pdf-merge")
    public Response merge(...) {
        ModuleContext auth = current.get();
        Object maxSize = auth.getLimit("max-file-size");
        ...
    }
}
```

`@RequireModuleAccess(moduleSlug, permission = "")`:

1. Intercepta a requisicao (via `ModuleAccessFilter`, um `ContainerRequestFilter` associado por
   `@NameBinding` — so roda em endpoints anotados, nunca globalmente).
2. Le `Authorization: Bearer <token>`, decodifica e valida o JWT (assinatura, expiracao,
   `tokenType == MODULE_ACCESS`).
3. Confere que o token pertence ao `moduleSlug` declarado.
4. Se `permission` foi declarada, confere que o token a possui.
5. Disponibiliza o `ModuleContext` resultante via `CurrentModuleContext` (`@RequestScoped`,
   injete e chame `.get()`).

Pode ser usada na classe (aplica a todos os metodos) ou em um metodo especifico (sobrescreve).

`ModuleContext` (record) expoe `userId`, `tenantId`, `moduleId`, `moduleSlug`, `planName`,
`accessSource`, `permissions`, `limits`, `permissionsVersion`, `issuedAt`, `expiresAt`, alem de
`hasPermission(key)`, `requirePermission(key)` (lanca `PermissionDeniedException` — 403,
mapeada automaticamente por `ModuleSecurityExceptionMapper` mesmo se chamada de dentro da
logica de negocio, nao so pelo filtro) e `getLimit(code[, default])`.

## Erros

Todas as excecoes (`com.saas.platformsecurity.exceptions`) estendem `ModuleSecurityException` e
sao mapeadas automaticamente para a resposta HTTP correspondente por
`ModuleSecurityExceptionMapper` — nenhum servico precisa registrar seu proprio
`ExceptionMapper` para elas.

| Excecao | Status | Quando |
|---|---|---|
| `ExpiredModuleTokenException` | 401 | Token com `exp` no passado. |
| `InvalidModuleTokenException` | 401 | Assinatura invalida, token malformado, ausente ou `tokenType` diferente de `MODULE_ACCESS`. |
| `ModuleMismatchException` | 403 | Token valido, mas emitido para outro `moduleSlug`. |
| `PermissionDeniedException` | 403 | Token valido, mas sem a permissao exigida. |

## Testes

```bash
mvn test
```

`ModuleAccessTokenServiceTest` cobre a decodificacao/validacao isolada; `ModuleAccessFilterTest`
sobe um `@QuarkusTest` real com um recurso de teste (`TestSecuredResource`) e exercita o filtro
ponta a ponta via RestAssured (401 sem token, 401 com header malformado, 403 modulo errado, 403
sem permissao, 200 com token valido).
