import unittest
from datetime import datetime
from unittest.mock import Mock

import requests

from worklens_desktop_client import api_client
from worklens_desktop_client.api_client import WorkLensApiClient


LoginError = getattr(api_client, "LoginError", Exception)


class WorkLensApiClientTests(unittest.TestCase):

    def test_login_reports_invalid_credentials_for_unauthorized_response(self) -> None:
        session = Mock()
        session.post.return_value = self._json_response(
            401,
            {"code": "INVALID_CREDENTIALS", "message": "Invalid username or password"},
        )
        client = WorkLensApiClient("http://localhost:8080", session=session)

        with self.assertRaisesRegex(LoginError, "用户名或密码错误，请重新输入"):
            client.login("missing-user", "wrong-password")

    def test_login_reports_temporary_lockout_for_too_many_attempts(self) -> None:
        session = Mock()
        session.post.return_value = self._json_response(
            429,
            {"code": "LOGIN_LOCKED", "message": "Too many failed login attempts"},
        )
        client = WorkLensApiClient("http://localhost:8080", session=session)

        with self.assertRaisesRegex(LoginError, "登录尝试次数过多，请在 15 分钟后重试"):
            client.login("employee.alice", "wrong-password")

    def test_login_reports_server_unavailable_for_connection_failure(self) -> None:
        session = Mock()
        session.post.side_effect = requests.ConnectionError("connection refused")
        client = WorkLensApiClient("http://localhost:8080", session=session)

        with self.assertRaisesRegex(LoginError, "无法连接 WorkLens 服务器"):
            client.login("employee.alice", "Password123!")

    def test_login_rejects_success_response_without_token(self) -> None:
        session = Mock()
        session.post.return_value = self._json_response(
            200,
            {
                "username": "employee.alice",
                "displayName": "Alice Chen",
                "role": "EMPLOYEE",
                "mustChangePassword": False,
            },
        )
        client = WorkLensApiClient("http://localhost:8080", session=session)

        with self.assertRaisesRegex(LoginError, "登录响应中没有有效 token"):
            client.login("employee.alice", "Password123!")

    def test_login_preserves_password_change_requirement(self) -> None:
        session = Mock()
        session.post.return_value = self._json_response(
            200,
            {
                "token": "abc123",
                "username": "employee.alice",
                "displayName": "Alice Chen",
                "role": "EMPLOYEE",
                "mustChangePassword": True,
            },
        )
        client = WorkLensApiClient("http://localhost:8080", session=session)

        login_result = client.login("employee.alice", "Password123!")

        self.assertTrue(
            hasattr(login_result, "must_change_password"),
            "LoginResult must preserve mustChangePassword",
        )
        self.assertTrue(login_result.must_change_password)

    def test_non_local_plain_http_base_url_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            WorkLensApiClient("http://worklens.example.com:8080")

    def test_login_posts_credentials_and_returns_token(self) -> None:
        session = Mock()
        response = Mock()
        response.json.return_value = {
            "token": "abc123",
            "username": "employee.alice",
            "displayName": "Alice Chen",
            "role": "EMPLOYEE",
        }
        session.post.return_value = response
        client = WorkLensApiClient("http://localhost:8080", session=session)

        login_result = client.login("employee.alice", "Password123!")

        self.assertEqual("abc123", login_result.token)
        self.assertEqual("employee.alice", login_result.username)
        self.assertEqual("Alice Chen", login_result.display_name)
        self.assertEqual("EMPLOYEE", login_result.role)
        session.post.assert_called_once_with(
            "http://localhost:8080/auth/login",
            json={
                "username": "employee.alice",
                "password": "Password123!",
            },
            timeout=10,
        )
        response.raise_for_status.assert_called_once()

    def test_login_falls_back_to_username_when_display_name_is_missing(self) -> None:
        session = Mock()
        response = Mock()
        response.json.return_value = {
            "token": "abc123",
            "username": "employee.alice",
            "role": "EMPLOYEE",
        }
        session.post.return_value = response
        client = WorkLensApiClient("http://localhost:8080", session=session)

        login_result = client.login("employee.alice", "Password123!")

        self.assertEqual("employee.alice", login_result.display_name)

    def test_create_usage_record_posts_without_employee_id(self) -> None:
        session = Mock()
        response = Mock()
        response.json.return_value = {
            "id": 7,
            "appName": "Manual Test App",
            "startedAt": "2026-07-04T12:00:00",
            "endedAt": "2026-07-04T12:05:00",
            "createdAt": "2026-07-04T12:05:01",
        }
        session.post.return_value = response
        client = WorkLensApiClient("http://localhost:8080", session=session)
        started_at = datetime.fromisoformat("2026-07-04T12:00:00")
        ended_at = datetime.fromisoformat("2026-07-04T12:05:00")

        usage_record = client.create_usage_record(
            token="abc123",
            app_name="Manual Test App",
            started_at=started_at,
            ended_at=ended_at,
        )

        self.assertEqual(7, usage_record["id"])
        session.post.assert_called_once_with(
            "http://localhost:8080/usage-records",
            headers={
                "Authorization": "Bearer abc123",
            },
            json={
                "appName": "Manual Test App",
                "startedAt": "2026-07-04T12:00:00",
                "endedAt": "2026-07-04T12:05:00",
            },
            timeout=10,
        )
        response.raise_for_status.assert_called_once()

    def test_create_usage_record_includes_client_record_id_when_provided(self) -> None:
        session = Mock()
        response = Mock()
        response.json.return_value = {
            "id": 8,
            "appName": "Manual Test App",
            "startedAt": "2026-07-04T12:00:00",
            "endedAt": "2026-07-04T12:05:00",
            "createdAt": "2026-07-04T12:05:01",
        }
        session.post.return_value = response
        client = WorkLensApiClient("http://localhost:8080", session=session)
        started_at = datetime.fromisoformat("2026-07-04T12:00:00")
        ended_at = datetime.fromisoformat("2026-07-04T12:05:00")

        client.create_usage_record(
            token="abc123",
            app_name="Manual Test App",
            started_at=started_at,
            ended_at=ended_at,
            client_record_id="client-record-0001",
        )

        session.post.assert_called_once_with(
            "http://localhost:8080/usage-records",
            headers={
                "Authorization": "Bearer abc123",
            },
            json={
                "appName": "Manual Test App",
                "startedAt": "2026-07-04T12:00:00",
                "endedAt": "2026-07-04T12:05:00",
                "clientRecordId": "client-record-0001",
            },
            timeout=10,
        )

    @staticmethod
    def _json_response(status_code: int, payload: dict) -> requests.Response:
        import json

        response = requests.Response()
        response.status_code = status_code
        response.url = "http://localhost:8080/auth/login"
        response.headers["Content-Type"] = "application/json"
        response._content = json.dumps(payload).encode("utf-8")
        return response


if __name__ == "__main__":
    unittest.main()
