import logging

from app_logging.formatters import DEFAULT_LOG_FORMAT


def configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format=DEFAULT_LOG_FORMAT,
    )
