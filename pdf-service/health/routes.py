from fastapi import APIRouter

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    summary="Verifica a disponibilidade do serviço",
    description="Endpoint público de health check, usado por orquestradores/monitoramento para confirmar que o pdf-service está no ar. Não exige autenticação.",
    response_description="Status simples do serviço.",
)
async def health():
    return {"status": "UP", "service": "pdf-service"}
