from pydantic import BaseModel


class SendMessageIn(BaseModel):
    to: str
    text: str
