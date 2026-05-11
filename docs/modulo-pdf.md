# Módulo PDF

## Fluxo completo

```
1. Usuário seleciona PDF A e PDF B no frontend
2. Frontend POST /api/v1/pdf/merge (multipart/form-data)
   Headers: Authorization: Bearer <JWT>, X-Tenant-ID: <uuid>
   
3. Quarkus — TenantResolutionFilter:
   • Valida JWT
   • Resolve tenant
   • Verifica subscription ativa
   
4. Quarkus — PlanFeatureInterceptor:
   • Verifica feature "pdf.merge" no plano
   • Todos os planos (free+) têm acesso
   
5. Quarkus — PdfResource.merge():
   • Salva arquivos em /tmp/saas-pdf/{tenantId}/
   • Cria PdfJob com status "pending"
   • Registra auditoria "pdf.merge.submitted"
   • Chama Python: POST /pdf/merge (multipart)
     Headers: X-Internal-Token, X-Tenant-ID, X-User-ID
     
6. Python — pdf_router.merge_pdf():
   • Valida X-Internal-Token
   • Lê arquivos
   • Chama services/pdf_service.merge_pdfs()
   • Retorna stream do PDF mesclado
   
7. Quarkus — recebe resposta do Python:
   • Salva resultado em /tmp/saas-pdf/{tenantId}/{jobId}-merged.pdf
   • Atualiza PdfJob status = "completed"
   
8. Frontend — lista jobs e exibe botão "Baixar"
   
9. Usuário clica "Baixar" → GET /api/v1/pdf/jobs/{jobId}/download
   Quarkus retorna stream do arquivo salvo
```

## Endpoints

### POST /api/v1/pdf/merge
- **Auth**: JWT obrigatório
- **Feature**: `pdf.merge`
- **Body**: multipart/form-data com `file_a` e `file_b` (PDF)
- **Retorno**: `PdfJob` com status inicial

### GET /api/v1/pdf/jobs
- **Auth**: JWT obrigatório
- **Feature**: `pdf.merge`
- **Retorno**: lista de `PdfJob` do tenant

### GET /api/v1/pdf/jobs/{jobId}/download
- **Auth**: JWT obrigatório
- **Feature**: `pdf.merge`
- **Retorno**: stream do PDF mesclado

## Status do job

| Status | Descrição |
|--------|-----------|
| `pending` | Criado, aguardando processamento |
| `processing` | Em processamento no Python |
| `completed` | Concluído, pronto para download |
| `failed` | Falhou (ver `error_message`) |

## Cotas

O plano free limita `max_pdf_merges_month = 10`. Controle via `usage_records`.
