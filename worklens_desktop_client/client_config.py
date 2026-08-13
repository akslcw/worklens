from __future__ import annotations

import configparser
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


DEFAULT_BASE_URL = "http://localhost:8080"


class ClientConfigError(RuntimeError):
    """A configuration failure that is safe to display to the user."""


@dataclass(frozen=True)
class ClientConfig:
    base_url: str
    config_path: Path


def application_directory() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def load_client_config(config_path: Path | None = None) -> ClientConfig:
    resolved_path = config_path or application_directory() / "config.ini"
    if not resolved_path.exists():
        return ClientConfig(base_url=DEFAULT_BASE_URL, config_path=resolved_path)

    parser = configparser.ConfigParser()
    try:
        with resolved_path.open("r", encoding="utf-8") as config_file:
            parser.read_file(config_file)
    except (OSError, configparser.Error) as error:
        raise ClientConfigError(f"无法读取配置文件：{resolved_path}") from error

    if not parser.has_option("backend", "base_url"):
        raise ClientConfigError("config.ini 缺少 [backend] 下的 base_url 配置。")

    base_url = parser.get("backend", "base_url").strip().rstrip("/")
    if not base_url:
        raise ClientConfigError("config.ini 中的 base_url 为空。请填写 WorkLens 服务器地址后重新启动。")
    parsed_url = urlparse(base_url)
    if parsed_url.scheme not in {"http", "https"} or not parsed_url.hostname:
        raise ClientConfigError("config.ini 中的 base_url 必须是有效的 HTTP 或 HTTPS 地址。")

    return ClientConfig(base_url=base_url, config_path=resolved_path)
