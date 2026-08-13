import threading
import time
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from worklens_desktop_client.api_client import LoginError
from worklens_desktop_client.api_client import LoginResult
from worklens_desktop_client.sync_runtime import SyncRuntime
from worklens_desktop_client.sync_runtime import SyncRuntimeConfig


class FakeApiClient:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url

    def login(self, username: str, password: str) -> LoginResult:
        return LoginResult(
            token="token-1",
            username=username,
            display_name="Alice Chen",
            role="EMPLOYEE",
        )


class FakePasswordChangeApiClient:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url

    def login(self, username: str, password: str) -> LoginResult:
        return LoginResult(
            token="token-1",
            username=username,
            display_name="Alice Chen",
            role="EMPLOYEE",
            must_change_password=True,
        )


class FakeReLoginApiClient:
    """First login returns token-1; every re-login returns the next token."""

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url
        self.login_calls: list[str] = []
        self.tokens = iter(["token-1", "token-2", "token-3"])

    def login(self, username: str, password: str) -> LoginResult:
        self.login_calls.append(username)
        return LoginResult(
            token=next(self.tokens),
            username=username,
            display_name="Alice Chen",
            role="EMPLOYEE",
        )


class FakeFailingReloginApiClient:
    """Initial login succeeds; all later logins raise LoginError."""

    def __init__(self, base_url: str) -> None:
        self.base_url = base_url
        self.login_calls: list[str] = []

    def login(self, username: str, password: str) -> LoginResult:
        if self.login_calls:
            raise LoginError("用户名或密码错误，请重新输入。")
        self.login_calls.append(username)
        return LoginResult(
            token="token-1",
            username=username,
            display_name="Alice Chen",
            role="EMPLOYEE",
        )


class FakeSyncService:
    def __init__(self, client, store) -> None:
        self.client = client
        self.store = store

    def upload_batch(self, token, records):
        return SimpleNamespace(
            uploaded_count=0,
            cached_count=0,
            failure_code=None,
            failure_message=None,
        )


class FakeAuthFailingSyncService:
    """Fails the upload_batch call at index fail_on_call (1-based) with
    AUTHENTICATION_FAILED; all other calls succeed. Records tokens used."""

    def __init__(self, client, store, fail_on_call: int) -> None:
        self.client = client
        self.store = store
        self.fail_on_call = fail_on_call
        self.call_count = 0
        self.tokens_used: list[str] = []

    def upload_batch(self, token, records):
        self.call_count += 1
        self.tokens_used.append(token)
        if self.call_count == self.fail_on_call:
            return SimpleNamespace(
                uploaded_count=0,
                cached_count=0,
                failure_code="AUTHENTICATION_FAILED",
                failure_message="The WorkLens login session is invalid or expired.",
            )
        return SimpleNamespace(
            uploaded_count=0,
            cached_count=0,
            failure_code=None,
            failure_message=None,
        )


class FakeAlwaysAuthFailingSyncService:
    def __init__(self, client, store, failure_code: str = "AUTHENTICATION_FAILED") -> None:
        self.client = client
        self.store = store
        self.failure_code = failure_code

    def upload_batch(self, token, records):
        return SimpleNamespace(
            uploaded_count=0,
            cached_count=0,
            failure_code=self.failure_code,
            failure_message=None,
        )


class FakeActivityTracker:
    def observe(self, app_name, observed_at) -> None:
        pass

    def cutoff(self, flush_at):
        return []


class FakeActivityProbe:
    def __init__(self, idle_threshold_seconds: int) -> None:
        self.idle_threshold_seconds = idle_threshold_seconds

    def sample_app_name(self) -> str:
        return "Chrome"


class SyncRuntimeTests(unittest.TestCase):

    def make_runtime(self, **kwargs) -> SyncRuntime:
        return SyncRuntime(
            SyncRuntimeConfig(
                base_url="http://localhost:8080",
                sample_interval_seconds=1,
                idle_threshold_seconds=300,
                upload_interval_seconds=1,
                cache_db=":memory:",
            ),
            logger=lambda message: None,
            **kwargs,
        )

    def test_run_rejects_password_change_requirement_before_login_callback(self) -> None:
        login_results: list[LoginResult] = []
        runtime = self.make_runtime(on_login=login_results.append)

        with patch("worklens_desktop_client.sync_runtime.WorkLensApiClient", FakePasswordChangeApiClient), \
                patch("worklens_desktop_client.sync_runtime.SyncService", FakeSyncService), \
                patch("worklens_desktop_client.sync_runtime.ActivityTracker", FakeActivityTracker), \
                patch("worklens_desktop_client.sync_runtime.Win32ActivityProbe", FakeActivityProbe), \
                patch("worklens_desktop_client.sync_runtime.LocalRecordStore", lambda cache_db: object()):
            with self.assertRaisesRegex(LoginError, "必须先在网页端修改密码"):
                runtime.run(
                    username="employee.alice",
                    password="Password123!",
                    stop_event=threading.Event(),
                    duration_seconds=0,
                )

        self.assertEqual([], login_results)

    def test_run_reports_logged_in_display_name(self) -> None:
        login_results: list[LoginResult] = []
        runtime = self.make_runtime(on_login=login_results.append)

        with patch("worklens_desktop_client.sync_runtime.WorkLensApiClient", FakeApiClient), \
                patch("worklens_desktop_client.sync_runtime.SyncService", FakeSyncService), \
                patch("worklens_desktop_client.sync_runtime.ActivityTracker", FakeActivityTracker), \
                patch("worklens_desktop_client.sync_runtime.Win32ActivityProbe", FakeActivityProbe), \
                patch("worklens_desktop_client.sync_runtime.LocalRecordStore", lambda cache_db: object()):
            runtime.run(
                username="employee.alice",
                password="Password123!",
                stop_event=threading.Event(),
                duration_seconds=0,
            )

        self.assertEqual(1, len(login_results))
        self.assertEqual("Alice Chen", login_results[0].display_name)

    def test_run_relogs_in_and_resumes_uploads_after_auth_failure(self) -> None:
        """C2: an AUTHENTICATION_FAILED flush triggers re-login; uploads then
        resume with the refreshed token."""
        runtime = self.make_runtime()
        sync_service = FakeAuthFailingSyncService(None, None, fail_on_call=2)

        with patch("worklens_desktop_client.sync_runtime.WorkLensApiClient", FakeReLoginApiClient), \
                patch("worklens_desktop_client.sync_runtime.SyncService", lambda client, store: sync_service), \
                patch("worklens_desktop_client.sync_runtime.ActivityTracker", FakeActivityTracker), \
                patch("worklens_desktop_client.sync_runtime.Win32ActivityProbe", FakeActivityProbe), \
                patch("worklens_desktop_client.sync_runtime.LocalRecordStore", lambda cache_db: object()):
            runtime.run(
                username="employee.alice",
                password="Password123!",
                stop_event=threading.Event(),
                duration_seconds=3,
            )

        # token-1 (startup + first periodic flush that fails), then token-2
        self.assertIn("token-2", sync_service.tokens_used)
        self.assertNotIn("token-1", sync_service.tokens_used[2:])

    def test_run_notifies_stop_once_when_relogin_keeps_failing(self) -> None:
        """C2: persistent re-login failure pauses uploads, notifies the tray
        exactly once, and keeps the loop alive (records stay cached)."""
        stopped_events: list[None] = []
        runtime = self.make_runtime(on_upload_stopped=lambda: stopped_events.append(None))
        sync_service = FakeAlwaysAuthFailingSyncService(None, None)

        with patch("worklens_desktop_client.sync_runtime.WorkLensApiClient", FakeFailingReloginApiClient), \
                patch("worklens_desktop_client.sync_runtime.SyncService", lambda client, store: sync_service), \
                patch("worklens_desktop_client.sync_runtime.ActivityTracker", FakeActivityTracker), \
                patch("worklens_desktop_client.sync_runtime.Win32ActivityProbe", FakeActivityProbe), \
                patch("worklens_desktop_client.sync_runtime.LocalRecordStore", lambda cache_db: object()):
            runtime.run(
                username="employee.alice",
                password="Password123!",
                stop_event=threading.Event(),
                duration_seconds=3,
            )

        self.assertEqual(1, len(stopped_events))

    def test_run_relogs_in_when_password_change_required(self) -> None:
        """C2: PASSWORD_CHANGE_REQUIRED also triggers a re-login attempt."""
        stopped_events: list[None] = []
        runtime = self.make_runtime(on_upload_stopped=lambda: stopped_events.append(None))
        sync_service = FakeAlwaysAuthFailingSyncService(None, None, failure_code="PASSWORD_CHANGE_REQUIRED")

        with patch("worklens_desktop_client.sync_runtime.WorkLensApiClient", FakeFailingReloginApiClient), \
                patch("worklens_desktop_client.sync_runtime.SyncService", lambda client, store: sync_service), \
                patch("worklens_desktop_client.sync_runtime.ActivityTracker", FakeActivityTracker), \
                patch("worklens_desktop_client.sync_runtime.Win32ActivityProbe", FakeActivityProbe), \
                patch("worklens_desktop_client.sync_runtime.LocalRecordStore", lambda cache_db: object()):
            runtime.run(
                username="employee.alice",
                password="Password123!",
                stop_event=threading.Event(),
                duration_seconds=3,
            )

        self.assertEqual(1, len(stopped_events))

    def test_sampling_continues_while_upload_is_blocked(self) -> None:
        """H8: uploads run on a separate thread, so slow HTTP does not stop
        sampling."""
        runtime = self.make_runtime()
        upload_started = threading.Event()
        release_upload = threading.Event()

        class BlockingSyncService:
            def __init__(self, client, store) -> None:
                self.client = client
                self.store = store
                self.upload_calls = 0

            def upload_batch(self, token, records):
                self.upload_calls += 1
                if self.upload_calls == 2:
                    upload_started.set()
                    release_upload.wait(timeout=5)
                return SimpleNamespace(
                    uploaded_count=0,
                    cached_count=0,
                    failure_code=None,
                    failure_message=None,
                )

        class CountingTracker:
            def __init__(self) -> None:
                self.observe_calls = 0

            def observe(self, app_name, observed_at) -> None:
                self.observe_calls += 1

            def cutoff(self, flush_at):
                return []

        blocking_service = BlockingSyncService(None, None)
        tracker = CountingTracker()

        with patch("worklens_desktop_client.sync_runtime.WorkLensApiClient", FakeApiClient), \
                patch("worklens_desktop_client.sync_runtime.SyncService", lambda client, store: blocking_service), \
                patch("worklens_desktop_client.sync_runtime.ActivityTracker", lambda: tracker), \
                patch("worklens_desktop_client.sync_runtime.Win32ActivityProbe", FakeActivityProbe), \
                patch("worklens_desktop_client.sync_runtime.LocalRecordStore", lambda cache_db: object()):
            thread = threading.Thread(
                target=runtime.run,
                kwargs={
                    "username": "employee.alice",
                    "password": "Password123!",
                    "stop_event": threading.Event(),
                    "duration_seconds": 4,
                },
            )
            thread.start()

            self.assertTrue(upload_started.wait(timeout=3))
            observe_calls_while_blocked = tracker.observe_calls
            time.sleep(1.2)
            self.assertGreater(tracker.observe_calls, observe_calls_while_blocked)
            release_upload.set()
            thread.join(timeout=5)
            self.assertFalse(thread.is_alive())


if __name__ == "__main__":
    unittest.main()
