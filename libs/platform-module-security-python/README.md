# platform-module-security-python

Biblioteca compartilhada de autenticacao/autorizacao para os backends Python de modulo
da plataforma (`pdf-service`, `whatsapp-service`, e futuros modulos).

Responsavel por toda a validacao do `ModuleAccessToken` (JWT HS256) emitido pelo
`auth-service` (Java/Quarkus). Nenhum modulo Python deve chamar `jwt.decode()`
diretamente nem duplicar essa logica — tudo passa por esta biblioteca.

## Instalacao

Enquanto nao existe um indice PyPI privado, os servicos consomem a biblioteca via
instalacao local editavel, referenciando o caminho relativo no `requirements.txt`:

```
-e ../libs/platform-module-security-python
```

Quando um indice PyPI privado estiver disponivel, a mesma dependencia passa a ser:

```
pip install platform-module-security-python
```

ou, direto do repositorio:

```
pip install git+ssh://git@<host>/<org>/<repo>.git#subdirectory=libs/platform-module-security-python
```

## Configuracao

Variaveis de ambiente (lidas via `pydantic-settings`, mesmo mecanismo `.env.global` /
`.env.{AMBIENTE}` ja usado pelos servicos):

| Variavel | Obrigatoria | Padrao | Descricao |
|---|---|---|---|
| `MODULE_ACCESS_TOKEN_SECRET` | sim | - | Segredo HMAC compartilhado com o `auth-service` (`app.token.module-secret`). Nunca reutilizar o mesmo valor do `PROFILE_ACCESS_TOKEN_SECRET`. |
| `JWT_ALGORITHM` | nao | `HS256` | Algoritmo de assinatura do ModuleAccessToken. |
| `JWT_ISSUER` | nao | `None` | Reservado para uma futura validacao de `iss`. O ModuleAccessToken atual nao emite essa claim. |
| `JWT_AUDIENCE` | nao | `None` | Reservado para uma futura validacao de `aud`. O ModuleAccessToken atual nao emite essa claim. |
| `CLOCK_SKEW` | nao | `0` | Tolerancia (segundos) de relogio na validacao de expiracao, repassada ao PyJWT como `leeway`. |

## Uso

```python
from fastapi import APIRouter, Depends
from platform_security_python import ModuleContext, module_security

router = APIRouter(prefix="/pdf", tags=["PDF"])


@router.post("/merge")
async def merge_pdf(
    auth: ModuleContext = Depends(module_security("pdf", "pdf-merge")),
):
    max_size = auth.get_limit("max-file-size")
    ...
```

`module_security(module_slug, permission=None)` retorna uma dependency do FastAPI que:

1. Le o header `Authorization: Bearer <token>`.
2. Decodifica e valida o JWT (assinatura, expiracao, `tokenType == "MODULE_ACCESS"`).
3. Garante que o token pertence ao `module_slug` esperado.
4. Se `permission` for informado, garante que o usuario a possui.
5. Retorna um `ModuleContext` com `user_id`, `tenant_id`, `module_id`, `module_slug`,
   `plan_name`, `access_source`, `permissions`, `limits`, `permissions_version`, alem dos
   helpers `has_permission()`, `require_permission()` e `get_limit()`.

`require_permission` e um alias de `module_security`, mantido para compatibilidade com o
nome usado nos servicos antes da extracao desta biblioteca.

## Erros

Todas as excecoes da biblioteca (`platform_security_python.exceptions`) sao subclasses de
`fastapi.HTTPException`, entao sao tratadas automaticamente pelo handler padrao do FastAPI
— nenhum servico precisa registrar um exception handler proprio para elas.

| Excecao | Status | Quando |
|---|---|---|
| `ExpiredTokenError` | 401 | Token com `exp` no passado. |
| `InvalidTokenError` | 401 | Assinatura invalida, token malformado ou `tokenType` diferente de `MODULE_ACCESS`. |
| `AuthenticationError` | 401 | Token ausente ou header `Authorization` malformado (base para os dois acima). |
| `ModuleMismatchError` | 403 | Token valido, mas emitido para outro `moduleSlug`. |
| `PermissionDeniedError` | 403 | Token valido, mas sem a permissao exigida pela rota. |
| `AuthorizationError` | 403 | Base dos dois erros 403 acima. |

## Testes

```
pip install -e ".[dev]"
pytest
```
