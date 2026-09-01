from fastapi import APIRouter, Depends

from exceptions.business import BusinessRuleError
from modules.whatsapp.permissions.constants import MODULE_WHATSAPP_SEND
from modules.whatsapp.schemas.message import SendMessageIn
from platform_security import ModuleContext, module_security

router = APIRouter(prefix="/whatsapp", tags=["WhatsApp"])


@router.post(
    "/messages",
    summary="Envia uma mensagem de WhatsApp (integração ainda não implementada)",
    description=(
        "Endpoint destinado ao envio de mensagens de WhatsApp para o contato informado. "
        "A validação de autenticação/autorização via ModuleAccessToken (módulo `whatsapp`, "
        "permissão `whatsapp-send`) já está em vigor, mas a integração real com o provedor "
        "de WhatsApp ainda não foi implementada — toda chamada retorna atualmente "
        "HTTP 501 (Not Implemented). Pertence ao contexto do módulo WhatsApp do cliente."
    ),
    response_description="Não há resposta de sucesso; o endpoint ainda não está implementado.",
    responses={
        401: {"description": "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo."},
        403: {"description": "Token válido, porém sem a permissão `whatsapp-send`."},
        422: {"description": "Corpo da requisição não corresponde ao schema esperado (`to`, `text`)."},
        501: {"description": "Funcionalidade ainda não implementada — nenhum provedor de WhatsApp está integrado."},
    },
)
async def send_message(
    body: SendMessageIn,
    auth: ModuleContext = Depends(module_security("whatsapp", MODULE_WHATSAPP_SEND)),
):
    raise BusinessRuleError("Envio de mensagens ainda não implementado.", status_code=501)
