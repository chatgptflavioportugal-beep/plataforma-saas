## Base global

See: .claude-base/CLAUDE.md

---

## Estrutura do repositório

```
claude-saas-base/
├── README.md                    ← este arquivo
├── CLAUDE.md                    ← documentação da arquitetura para o Claude
├── settings.json                ← configurações padrão do Claude Code
│
├── agents/                      ← agentes especializados por domínio
│   ├── architect.md
│   ├── frontend-react.md
│   ├── backend-quarkus.md
│   ├── backend-python-ai.md
│   ├── database-supabase.md
│   ├── billing-plans.md
│   ├── security-reviewer.md
│   └── code-reviewer.md
│
├── skills/                      ← skills reutilizáveis com fluxo completo
│   ├── criar-feature-completa/
│   ├── criar-tela-react/
│   ├── criar-login-supabase/
│   ├── criar-protecao-rotas/
│   ├── criar-endpoint-quarkus/
│   ├── criar-gateway-autorizacao-quarkus/
│   ├── criar-servico-python-ai/
│   ├── criar-modelagem-supabase/
│   ├── criar-multi-tenant/
│   ├── criar-planos-assinaturas/
│   ├── criar-free-trial/
│   ├── criar-limite-contas-gratis/
│   ├── criar-alertas-expiracao/
│   ├── revisar-seguranca/
│   ├── revisar-codigo/
│   └── documentar-arquitetura/
│
├── commands/                    ← atalhos para fluxos comuns
│   ├── criar-feature.md
│   ├── criar-tela.md
│   ├── criar-endpoint.md
│   ├── criar-fluxo-ai.md
│   ├── revisar-projeto.md
│   ├── revisar-seguranca.md
│   ├── gerar-doc-arquitetura.md
│   └── verificar-planos-acessos.md
│
├── context/                     ← padrões técnicos e arquitetura
│   ├── arquitetura-base.md
│   ├── stack.md
│   ├── fluxo-autenticacao.md
│   ├── fluxo-autorizacao-planos.md
│   ├── fluxo-free-trial.md
│   ├── fluxo-multi-tenant.md
│   ├── padroes-frontend.md
│   ├── padroes-backend-quarkus.md
│   ├── padroes-backend-python-ai.md
│   ├── padroes-database-supabase.md
│   └── padroes-seguranca.md
│
└── workflows/                   ← fluxos completos de desenvolvimento
    ├── nova-feature-saas.md
    ├── autenticacao-supabase.md
    ├── controle-planos.md
    ├── free-trial.md
    ├── acesso-python-ai.md
    ├── multi-tenant.md
    └── auditoria-seguranca.md
```

---

## Comandos disponíveis

| Comando | Descrição |
|---------|-----------|
| `/criar-feature` | Feature completa full-stack (React + Quarkus + DB) |
| `/criar-tela` | Tela React com padrões, rotas e proteção |
| `/criar-endpoint` | Endpoint Quarkus com autorização e plano |
| `/criar-fluxo-ai` | Fluxo Python IA via LangGraph com proxy Quarkus |
| `/revisar-projeto` | Revisão completa do projeto |
| `/revisar-seguranca` | Auditoria de segurança |
| `/gerar-doc-arquitetura` | Documentação de arquitetura atualizada |
| `/verificar-planos-acessos` | Verifica controle de planos e acessos |

---

## Como evoluir esta base

1. **Novos agentes**: crie em `agents/` quando surgir nova especialidade técnica recorrente
2. **Novas skills**: crie em `skills/` para fluxos que se repetem entre projetos
3. **Novos padrões**: documente em `context/` ao consolidar decisões técnicas
4. **Novos workflows**: atualize `workflows/` quando o processo de desenvolvimento evoluir
5. **Versionamento semântico**: use commits como:
   - `feat(agents): adiciona agente de notificações`
   - `feat(skills): adiciona skill criar-webhook`
   - `fix(context): corrige padrão de autenticação JWT`
   - `docs(workflows): atualiza fluxo de auditoria`

---

## Funcionalidades cobertas pela base

- Cadastro e login de usuários (Supabase Auth)
- Criação de empresa/organização (multi-tenant)
- Free trial configurável por dias
- Limite configurável de contas gratuitas por tenant
- Bloqueio automático após expiração do trial
- Alertas de expiração (antecipados e no dia)
- Sistema de planos com permissões por plano
- Controle de uso e quotas
- Logs de auditoria de segurança
- Gateway de autorização centralizado no Quarkus
- Execução de IA isolada no Python (somente se autorizado)


