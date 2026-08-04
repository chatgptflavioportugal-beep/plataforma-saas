import json
import logging

DEFAULT_LOG_FORMAT = "%(asctime)s %(levelname)s %(name)s — %(message)s"


class JsonFormatter(logging.Formatter):
    """Uma linha JSON por registro de log — formato esperado pelo Google Cloud
    Logging (severity/message/logger/timestamp)."""

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "severity": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)
