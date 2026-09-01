"""
Eventos de domínio candidatos a publicação via Kafka quando a infraestrutura
de mensageria for ativada (ver producer.py/consumer.py). Definidos como
modelos Pydantic para já nascerem serializáveis.
"""

from datetime import datetime
from typing import Any

from pydantic import BaseModel


class DomainEvent(BaseModel):
    event_type: str
    tenant_id: str
    occurred_at: datetime
    payload: dict[str, Any] = {}


class WhatsAppMessageSentEvent(DomainEvent):
    event_type: str = "whatsapp.message.sent"
