# pdf-service

Serviço FastAPI dedicado ao módulo PDF (merge, histórico de jobs, download). Recebe requisições diretamente do frontend, autenticadas por um `ModuleAccessToken` (JWT HMAC-SHA256) emitido pelo `auth-service` — não há proxy Quarkus no meio da chamada, e nenhuma rota consulta Auth/Profile/Subscription para autorização: tudo vem das claims do próprio token.

## Estrutura

```
main.py                  # app FastAPI, lifespan (pool de conexões), CORS, registra routers
config/                  # variáveis de ambiente por ambiente (.env.dev/.env.hml/.env.producao)
app_logging/             # configuração central de logging
exceptions/              # exceptions tipadas (401/403/400/erros de negócio), todas HTTPException
responses/               # SuccessResponse/ErrorResponse/ValidationResponse
security/
    tokens/               # validação de ModuleAccessToken (JWT)
    dependencies/         # CurrentUser — contexto do usuário autenticado
middleware/               # require_permission()/require_limit() — dependencies FastAPI
routes/                    # rotas HTTP (/pdf/merge, /pdf/jobs, /pdf/jobs/{id}/download)
health/                    # GET /health
permissions/               # chaves de permissão e de limite do módulo
services/
    merge/                 # lógica de negócio do merge
    ai/ cache/ email/ kafka/ notification/ queue/ redis/ websocket/  # stubs, não ativados
validators/                # validação de request/arquivo/limites
schemas/                   # modelos Pydantic de request/response
repository/                 # toda consulta ao banco (pdf_jobs)
storage/                    # persistência dos arquivos (local hoje, S3 stub)
models/                     # reservado para futuras entidades ORM (hoje não há nenhuma — I/O é Pydantic + SQL cru)
enums/                       # enums compartilhados (ex: PdfJobStatus)
utils/                        # helpers genuinamente reaproveitáveis
db/                            # pool asyncpg
tests/
    routes/                    # espelha routes/
    utils/                      # espelha utils/
```

## Fluxo de autenticação e permissão

1. O frontend (via `pdf-frontend`, carregado pelo Front Host) envia `Authorization: Bearer <ModuleAccessToken>` para o endpoint.
2. O endpoint declara `Depends(require_permission(<permission_key ou None>))` ou `Depends(require_limit(<limit_code>))`.
3. `require_permission`/`require_limit` (em `middleware/module_token_middleware.py`) validam o token via `security/tokens/module_token.py`, conferem que ele pertence ao módulo `pdf` e, se uma permissão foi informada, que ela está na lista de permissões do token.
4. O resultado é um `CurrentUser` (`security/dependencies/current_user.py`) com `tenant_id`, `user_id`, `permissions`, `limits` etc. — o endpoint não faz nenhuma validação manual, só usa o contexto já pronto.

Limites (`limits` no JWT) são checados em dois níveis:
- **`max-file-size`**: comparado direto contra o tamanho do upload em `validators/merge_validator.py`.
- **`daily-merges`**: o teto vem do JWT, mas a contagem do dia é uma consulta à própria tabela `pdf_jobs` deste serviço (`repository/pdf_jobs_repository.py::count_jobs_today`) — nenhuma chamada a outro microsserviço.

Erros de autenticação/autorização/validação são levantados como subclasses de `HTTPException` em `exceptions/`.

## Variáveis de ambiente

Ver `config/config.py`. Em desenvolvimento local, os arquivos reais ficam em `config/.env.*` (fora do controle de versão — contêm segredos).
