import unittest
from types import SimpleNamespace
from unittest.mock import Mock
from unittest.mock import patch

from worklens_desktop_client import tray_app
from worklens_desktop_client.api_client import LoginError


class TrayAppTests(unittest.TestCase):

    def make_args(self, base_url="http://localhost:8080"):
        return SimpleNamespace(
            base_url=base_url,
            sample_interval_seconds=5,
            idle_threshold_seconds=300,
            upload_interval_seconds=300,
            cache_db=":memory:",
            username=None,
        )

    def test_parse_args_rejects_password_command_line_argument(self) -> None:
        with patch("sys.argv", ["tray_app.py", "--password", "super-secret"]):
            with self.assertRaises(SystemExit):
                tray_app.parse_args()

    def test_main_reports_login_failure_before_starting_tray(self) -> None:
        args = self.make_args()
        runtime = Mock()
        runtime.login.side_effect = LoginError("用户名或密码错误，请重新输入。")
        logger = Mock()

        with patch.object(tray_app, "parse_args", return_value=args), \
                patch.object(tray_app, "create_client_logger", return_value=logger), \
                patch.object(tray_app, "WorkLensApiClient", create=True) as api_client, \
                patch.object(tray_app, "prompt_credentials", return_value=("missing-user", "wrong-password")), \
                patch.object(tray_app, "SyncRuntime", return_value=runtime), \
                patch.object(tray_app, "show_login_error", create=True) as show_login_error, \
                patch.object(tray_app, "BackgroundRunner") as background_runner, \
                patch.object(tray_app.pystray, "Icon") as tray_icon:
            tray_app.main()

        api_client.assert_called_once_with("http://localhost:8080")
        runtime.login.assert_called_once_with("missing-user", "wrong-password")
        show_login_error.assert_called_once_with("用户名或密码错误，请重新输入。")
        background_runner.assert_not_called()
        tray_icon.assert_not_called()

    def test_main_reports_invalid_base_url_before_prompting_credentials(self) -> None:
        args = self.make_args(base_url="http://evil.example.com")
        logger = Mock()

        def reject_invalid_client(base_url):
            raise ValueError("HTTPS is required when connecting to a non-local WorkLens server.")

        with patch.object(tray_app, "parse_args", return_value=args), \
                patch.object(tray_app, "create_client_logger", return_value=logger), \
                patch.object(tray_app, "WorkLensApiClient", create=True, side_effect=reject_invalid_client), \
                patch.object(tray_app, "prompt_credentials") as prompt_credentials, \
                patch.object(tray_app, "show_configuration_error", create=True) as show_configuration_error, \
                patch.object(tray_app, "BackgroundRunner") as background_runner:
            tray_app.main()

        show_configuration_error.assert_called_once_with(
            "HTTPS is required when connecting to a non-local WorkLens server."
        )
        prompt_credentials.assert_not_called()
        background_runner.assert_not_called()


if __name__ == "__main__":
    unittest.main()
