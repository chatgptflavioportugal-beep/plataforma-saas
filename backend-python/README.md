# backend-python

Serviço FastAPI de execução de módulos (hoje: `pdf`). Recebe requisições diretamente do frontend, autenticadas por um `ModuleAccessToken` (JWT HMAC-SHA256) emitido pelo Quarkus — não há proxy Quarkus no meio da chamada.

## Estrutura

```
main.py                  # app FastAPI, lifespan (pool de conexões), CORS, /health, registra routers
config/                  # variáveis de ambiente por ambiente (.env.dev/.env.hml/.env.producao)
app_logging/             # configuração central de logging
exceptions/              # exceptions tipadas (401/403/400/erros de negócio), todas HTTPException
responses/                # SuccessResponse/ErrorResponse/ValidationResponse — scaffold, ainda não usado
security/
    tokens/               # validação de ModuleAccessToken (JWT)
    dependencies/         # CurrentUser — contexto do usuário autenticado
    permissions/          # require_permission(module_slug, permission) — dependency FastAPI
services/                 # integrações externas (redis, kafka, storage, email, ...) — a maioria stub
db/                        # pool asyncpg compartilhado entre módulos
modules/
    pdf/
        api/                # rotas HTTP
        services/           # lógica de negócio do módulo
        schemas/            # modelos Pydantic de request/response
        repository/         # toda consulta ao banco deste módulo
        permissions/        # chaves de permissão do módulo
enums/                     # enums compartilhados (ex: PdfJobStatus)
utils/                      # helpers genuinamente reaproveitáveis entre módulos
tests/
    modules/<módulo>/       # testes espelham a estrutura de modules/
```

## Fluxo de autenticação e permissão

1. O frontend envia `Authorization: Bearer <ModuleAccessToken>` para o endpoint do módulo.
2. O endpoint declara `Depends(require_permission(<module_slug>, <permission_key ou None>))`.
3. `require_permission` (em `security/permissions/decorators.py`) valida o token via `security/tokens/module_token.py`, confere se o token pertence ao `module_slug` esperado e, se uma permissão foi informada, confere se ela está na lista de permissões do token.
4. O resultado é um `CurrentUser` (`security/dependencies/current_user.py`) com `tenant_id`, `user_id`, `permissions`, `limits` etc. — o endpoint não faz nenhuma validação manual, só usa o contexto já pronto.

Erros de autenticação/autorização/validação são levantados como subclasses de `HTTPException` em `exceptions/`, preservando o formato padrão `{"detail": "..."}` do FastAPI.

## Adicionando um novo módulo

1. Criar `modules/<nome>/` com `api/`, `services/`, `schemas/`, `repository/` e `permissions/` (só o que fizer sentido para o módulo).
2. Definir as chaves de permissão em `modules/<nome>/permissions/constants.py`.
3. Nas rotas, usar `Depends(require_permission("<slug>", MINHA_PERMISSAO))`.
4. Registrar o router em `main.py` via `app.include_router(...)`.
5. Espelhar a estrutura em `tests/modules/<nome>/`.

## Variáveis de ambiente

Ver `.env.example`. Em desenvolvimento local, os arquivos reais ficam em `config/.env.*` (fora do controle de versão — contêm segredos).
