from __future__ import annotations

import argparse
import threading
from tkinter import Tk, messagebox, simpledialog
from urllib.parse import urlparse

import pystray
from PIL import Image
from PIL import ImageDraw

from worklens_desktop_client.background_runner import BackgroundRunner
from worklens_desktop_client.api_client import LoginError
from worklens_desktop_client.api_client import WorkLensApiClient
from worklens_desktop_client.autostart import AutostartError
from worklens_desktop_client.autostart import is_autostart_enabled
from worklens_desktop_client.autostart import set_autostart_enabled
from worklens_desktop_client.client_config import ClientConfigError
from worklens_desktop_client.client_config import load_client_config
from worklens_desktop_client.client_logging import create_client_logger
from worklens_desktop_client.runtime_paths import default_cache_path
from worklens_desktop_client.sync_runtime import SyncRuntime
from worklens_desktop_client.sync_runtime import SyncRuntimeConfig
from worklens_desktop_client.tray_status import format_status_menu_text
from worklens_desktop_client.tray_status import format_tray_title


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the WorkLens desktop collector in the Windows system tray."
    )
    parser.add_argument("--base-url", default=None)
    parser.add_argument("--sample-interval-seconds", type=int, default=5)
    parser.add_argument("--idle-threshold-seconds", type=int, default=300)
    parser.add_argument("--upload-interval-seconds", type=int, default=300)
    parser.add_argument("--cache-db", default=None)
    parser.add_argument("--username", default=None)
    return parser.parse_args()


def create_status_image(status: str) -> Image.Image:
    color = "#2e7d32" if status == "RUNNING" else "#c62828"
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((8, 8, 56, 56), fill=color)
    draw.ellipse((18, 18, 46, 46), fill="white")
    return image


def prompt_credentials(initial_username: str | None) -> tuple[str, str]:
    root = Tk()
    root.withdraw()
    username = initial_username or simpledialog.askstring("WorkLens", "Username:", parent=root)
    password = simpledialog.askstring("WorkLens", "Password:", parent=root, show="*")
    root.destroy()

    if not username or not password:
        raise SystemExit("Username and password are required.")
    return username.strip(), password


def show_login_error(message: str) -> None:
    root = Tk()
    root.withdraw()
    try:
        messagebox.showerror("WorkLens 登录失败", message, parent=root)
    finally:
        root.destroy()


def show_configuration_error(message: str) -> None:
    root = Tk()
    root.withdraw()
    try:
        messagebox.showerror("WorkLens 配置错误", message, parent=root)
    finally:
        root.destroy()


def show_autostart_error(message: str) -> None:
    root = Tk()
    root.withdraw()
    try:
        messagebox.showerror("WorkLens 开机自启动", message, parent=root)
    finally:
        root.destroy()


def main() -> None:
    args = parse_args()
    logger = create_client_logger()
    try:
        if args.base_url:
            base_url = args.base_url
            config_path = "<command line>"
        else:
            config = load_client_config()
            base_url = config.base_url
            config_path = str(config.config_path)
    except ClientConfigError as error:
        logger.error("Configuration failed: %s", error)
        show_configuration_error(str(error))
        return
    cache_db = args.cache_db or str(default_cache_path())
    logger.info("Starting WorkLens desktop client. base_url=%s config=%s", base_url, config_path)
    if urlparse(base_url).scheme == "http":
        logger.warning(
            "正在通过明文 HTTP 连接服务器（仅限本机演示环境）。"
            "局域网或远程部署请务必使用 HTTPS，否则登录凭据与采集数据可能被窃听。"
        )
    try:
        WorkLensApiClient(base_url)
    except ValueError as error:
        logger.error("Invalid base_url configuration: %s", error)
        show_configuration_error(str(error))
        return
    username, password = prompt_credentials(args.username)

    status_holder = {"value": "STOPPED"}
    display_name_holder = {"value": username}
    icon_holder: dict[str, pystray.Icon] = {}

    def refresh_icon() -> None:
        icon = icon_holder.get("icon")
        if icon is not None:
            status = status_holder["value"]
            icon.icon = create_status_image(status)
            icon.title = format_tray_title(status, display_name_holder["value"])
            icon.update_menu()

    def update_login_user(login_result) -> None:
        display_name_holder["value"] = login_result.display_name
        refresh_icon()

    def notify_upload_stopped() -> None:
        status_holder["value"] = "STOPPED"
        refresh_icon()
        icon = icon_holder.get("icon")
        if icon is not None:
            icon.notify(
                "登录会话已失效且自动重登失败。采集已暂停，数据仍保存在本机缓存中；"
                "请在网页端检查账号状态后重启客户端以恢复上传。",
                "WorkLens",
            )

    runtime = SyncRuntime(
        SyncRuntimeConfig(
            base_url=base_url,
            sample_interval_seconds=args.sample_interval_seconds,
            idle_threshold_seconds=args.idle_threshold_seconds,
            upload_interval_seconds=args.upload_interval_seconds,
            cache_db=cache_db,
        ),
        logger=logger.info,
        on_login=update_login_user,
        on_upload_stopped=notify_upload_stopped,
    )

    try:
        login_result = runtime.login(username, password)
    except LoginError as error:
        logger.warning("Login failed for username=%s: %s", username, error)
        show_login_error(str(error))
        return

    display_name_holder["value"] = login_result.display_name
    logger.info("Login accepted for username=%s display_name=%s", username, login_result.display_name)

    def update_status(status: str) -> None:
        status_holder["value"] = status
        refresh_icon()

    def worker(stop_event: threading.Event) -> None:
        try:
            runtime.run(
                username=username,
                password=password,
                stop_event=stop_event,
                login_result=login_result,
            )
        except Exception:
            logger.exception("Background collection stopped unexpectedly.")
            raise

    def notify_collection_error(error: Exception) -> None:
        status_holder["value"] = "STOPPED"
        refresh_icon()
        logger.error("Background collection stopped: %s", error)
        icon = icon_holder.get("icon")
        if icon is not None:
            icon.notify(f"采集已停止：{error}", "WorkLens")

    runner = BackgroundRunner(worker=worker, on_status_change=update_status, on_error=notify_collection_error)

    def status_text(_) -> str:
        return format_status_menu_text(status_holder["value"], display_name_holder["value"])

    def quit_app(icon: pystray.Icon, item) -> None:
        logger.info("Exit requested from tray menu.")
        runner.request_stop()
        runner.join(timeout=10)
        icon.stop()

    def autostart_checked(_) -> bool:
        try:
            return is_autostart_enabled()
        except (AutostartError, OSError):
            return False

    def toggle_autostart(icon: pystray.Icon, item) -> None:
        try:
            set_autostart_enabled(not is_autostart_enabled())
        except (AutostartError, OSError) as error:
            show_autostart_error(str(error))
        icon.update_menu()

    menu = pystray.Menu(
        pystray.MenuItem(status_text, None, enabled=False),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("开机自动启动", toggle_autostart, checked=autostart_checked),
        pystray.MenuItem("退出", quit_app),
    )
    icon = pystray.Icon(
        "worklens-desktop-client",
        create_status_image("STOPPED"),
        format_tray_title("STOPPED", display_name_holder["value"]),
        menu,
    )
    icon_holder["icon"] = icon
    runner.start()
    icon.run()


if __name__ == "__main__":
    main()
