import logging

from app_logging.formatters import DEFAULT_LOG_FORMAT, JsonFormatter


def configure_logging(level: str, log_format: str = "json") -> None:
    handler = logging.StreamHandler()
    if log_format.lower() == "text":
        handler.setFormatter(logging.Formatter(DEFAULT_LOG_FORMAT))
    else:
        handler.setFormatter(JsonFormatter())

    root = logging.getLogger()
    root.setLevel(getattr(logging, level.upper(), logging.INFO))
    root.handlers = [handler]
