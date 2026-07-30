from fastapi import APIRouter, Depends

from exceptions.business import BusinessRuleError
from modules.whatsapp.permissions.constants import MODULE_WHATSAPP_SEND
from modules.whatsapp.schemas.message import SendMessageIn
from platform_security import ModuleContext, module_security

router = APIRouter(prefix="/whatsapp", tags=["WhatsApp"])


@router.post("/messages")
async def send_message(
    body: SendMessageIn,
    auth: ModuleContext = Depends(module_security("whatsapp", MODULE_WHATSAPP_SEND)),
):
    """
    Envio de mensagem — estrutura inicial do WhatsApp Service. Autenticação e
    autorização (ModuleAccessToken) já funcionam; a integração real com o
    provedor de WhatsApp ainda não foi implementada.
    """
    raise BusinessRuleError("Envio de mensagens ainda não implementado.", status_code=501)
