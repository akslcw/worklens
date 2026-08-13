import tempfile
import unittest
from pathlib import Path

from worklens_desktop_client.client_config import ClientConfigError
from worklens_desktop_client.client_config import DEFAULT_BASE_URL
from worklens_desktop_client.client_config import load_client_config


class ClientConfigTests(unittest.TestCase):

    def test_missing_config_uses_local_default(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            missing_path = Path(temporary_directory) / "config.ini"
            config = load_client_config(missing_path)

        self.assertEqual(DEFAULT_BASE_URL, config.base_url)
        self.assertEqual(missing_path, config.config_path)

    def test_base_url_is_loaded_and_trailing_slash_is_removed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            config_path = Path(temporary_directory) / "config.ini"
            config_path.write_text(
                "[backend]\nbase_url=https://worklens.example.com/\n",
                encoding="utf-8",
            )
            config = load_client_config(config_path)

        self.assertEqual("https://worklens.example.com", config.base_url)

    def test_invalid_base_url_has_clear_error(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            config_path = Path(temporary_directory) / "config.ini"
            config_path.write_text("[backend]\nbase_url=not-a-url\n", encoding="utf-8")

            with self.assertRaisesRegex(ClientConfigError, "有效的 HTTP 或 HTTPS 地址"):
                load_client_config(config_path)

    def test_blank_base_url_has_clear_error(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            config_path = Path(temporary_directory) / "config.ini"
            config_path.write_text("[backend]\nbase_url=\n", encoding="utf-8")

            with self.assertRaisesRegex(ClientConfigError, "base_url 为空"):
                load_client_config(config_path)


if __name__ == "__main__":
    unittest.main()
