from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any
from urllib.parse import urlparse

import requests


class LoginError(RuntimeError):
    """A login failure that is safe and useful to show to the user."""


@dataclass(frozen=True)
class LoginResult:
    token: str
    username: str
    display_name: str
    role: str
    must_change_password: bool = False


class WorkLensApiClient:
    def __init__(self, base_url: str, session: requests.Session | None = None) -> None:
        parsed_url = urlparse(base_url)
        local_hosts = {"localhost", "127.0.0.1", "::1"}
        if parsed_url.scheme == "http" and parsed_url.hostname not in local_hosts:
            raise ValueError("HTTPS is required when connecting to a non-local WorkLens server.")
        self._base_url = base_url.rstrip("/")
        self._session = session or requests.Session()

    def login(self, username: str, password: str) -> LoginResult:
        try:
            response = self._session.post(
                f"{self._base_url}/auth/login",
                json={
                    "username": username,
                    "password": password,
                },
                timeout=10,
            )
            response.raise_for_status()
        except requests.HTTPError as error:
            status_code = error.response.status_code if error.response is not None else None
            if status_code == 401:
                raise LoginError("用户名或密码错误，请重新输入。") from error
            if status_code == 403:
                raise LoginError("该账号暂不可用于桌面客户端，请联系管理员。") from error
            if status_code == 429:
                raise LoginError("登录尝试次数过多，请在 15 分钟后重试。") from error
            raise LoginError(f"登录服务返回错误（HTTP {status_code or '未知'}）。") from error
        except requests.RequestException as error:
            raise LoginError("无法连接 WorkLens 服务器，请检查网络或服务是否已启动。") from error

        try:
            payload = response.json()
        except ValueError as error:
            raise LoginError("服务器返回了无效的登录响应。") from error

        token = payload.get("token") if isinstance(payload, dict) else None
        if not isinstance(token, str) or not token.strip():
            raise LoginError("登录响应中没有有效 token。")

        response_username = payload.get("username")
        role = payload.get("role")
        if not isinstance(response_username, str) or not response_username.strip() or not isinstance(role, str) or not role.strip():
            raise LoginError("服务器返回了无效的登录响应。")

        return LoginResult(
            token=token,
            username=response_username,
            display_name=payload.get("displayName") or response_username,
            role=role,
            must_change_password=bool(payload.get("mustChangePassword", False)),
        )

    def create_usage_record(
        self,
        token: str,
        app_name: str,
        started_at: datetime,
        ended_at: datetime,
        client_record_id: str | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "appName": app_name,
            "startedAt": started_at.isoformat(timespec="seconds"),
            "endedAt": ended_at.isoformat(timespec="seconds"),
        }
        if client_record_id:
            payload["clientRecordId"] = client_record_id
        response = self._session.post(
            f"{self._base_url}/usage-records",
            headers={
                "Authorization": f"Bearer {token}",
            },
            json=payload,
            timeout=5,
        )
        response.raise_for_status()
        return response.json()
