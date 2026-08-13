from __future__ import annotations

import queue
import threading
import time
from dataclasses import dataclass
from datetime import datetime

from worklens_desktop_client.activity_tracker import ActivityRecord
from worklens_desktop_client.activity_tracker import ActivityTracker
from worklens_desktop_client.api_client import LoginError
from worklens_desktop_client.api_client import LoginResult
from worklens_desktop_client.api_client import WorkLensApiClient
from worklens_desktop_client.local_store import LocalRecordStore
from worklens_desktop_client.sync_service import SyncService
from worklens_desktop_client.windows_activity import Win32ActivityProbe


@dataclass(frozen=True)
class SyncRuntimeConfig:
    base_url: str
    sample_interval_seconds: int
    idle_threshold_seconds: int
    upload_interval_seconds: int
    cache_db: str


class SyncRuntime:
    # Bounded in-memory hand-off between the sampling loop and the upload
    # worker; overflow spills to the local cache instead of blocking sampling.
    MAX_QUEUED_RECORDS = 2000
    AUTH_FAILURE_CODES = ("AUTHENTICATION_FAILED", "PASSWORD_CHANGE_REQUIRED")

    def __init__(self, config: SyncRuntimeConfig, logger=None, on_login=None, on_upload_stopped=None) -> None:
        self._config = config
        self._logger = logger or (lambda message: print(message))
        self._on_login = on_login or (lambda login_result: None)
        self._on_upload_stopped = on_upload_stopped

    def login(self, username: str, password: str) -> LoginResult:
        login_result = WorkLensApiClient(self._config.base_url).login(username, password)
        self._validate_login_result(login_result)
        return login_result

    def run(
        self,
        username: str,
        password: str,
        stop_event: threading.Event,
        duration_seconds: int | None = None,
        login_result: LoginResult | None = None,
    ) -> None:
        client = WorkLensApiClient(self._config.base_url)
        tracker = ActivityTracker()
        probe = Win32ActivityProbe(idle_threshold_seconds=self._config.idle_threshold_seconds)
        store = LocalRecordStore(self._config.cache_db)
        sync_service = SyncService(client, store)
        upload_queue: queue.Queue[ActivityRecord] = queue.Queue(maxsize=self.MAX_QUEUED_RECORDS)

        if login_result is None:
            login_result = client.login(username, password)
        self._validate_login_result(login_result)

        self._on_login(login_result)
        self._logger(f"Login succeeded for {login_result.username} ({login_result.role}).")

        login_result_holder = [login_result]
        relogin_state = {
            "last_attempt": 0.0,
            "backoff_seconds": 60.0,
            "stopped_notified": False,
        }
        rejected_state = {"notified": False}

        def reauthenticate() -> bool:
            now = time.time()
            if now - relogin_state["last_attempt"] < relogin_state["backoff_seconds"]:
                return False
            relogin_state["last_attempt"] = now
            try:
                refreshed = client.login(username, password)
                self._validate_login_result(refreshed)
            except LoginError as error:
                relogin_state["backoff_seconds"] = min(relogin_state["backoff_seconds"] * 2, 900.0)
                self._logger(
                    f"Re-login failed: {error}. Uploads remain paused; records stay cached locally. "
                    f"Next attempt in {relogin_state['backoff_seconds']}s."
                )
                if not relogin_state["stopped_notified"]:
                    relogin_state["stopped_notified"] = True
                    if self._on_upload_stopped is not None:
                        self._on_upload_stopped(
                            "登录会话已失效且自动重登失败。采集已暂停，数据仍保存在本机缓存中；"
                            "请在网页端检查账号状态后重启客户端以恢复上传。"
                        )
                return False
            relogin_state["backoff_seconds"] = 60.0
            login_result_holder[0] = refreshed
            self._on_login(refreshed)
            self._logger(f"Session refreshed for {refreshed.username}. Uploads resume.")
            return True

        def process_report(report) -> None:
            self._log_upload_failure(report)
            if report.failure_code in self.AUTH_FAILURE_CODES:
                reauthenticate()
            elif report.failure_code == "UPLOAD_REJECTED":
                self._logger(
                    "ERROR: the server permanently rejected uploaded records (HTTP 4xx). "
                    "Rejected records are quarantined locally; please check the client version and server logs."
                )
                if not rejected_state["notified"]:
                    rejected_state["notified"] = True
                    if self._on_upload_stopped is not None:
                        self._on_upload_stopped(
                            "部分采集数据被服务器拒绝（HTTP 4xx），已隔离在本机缓存中。"
                            "请检查客户端版本或联系管理员。"
                        )

        def queue_records(records: list[ActivityRecord]) -> None:
            if not records:
                return
            overflow: list[ActivityRecord] = []
            for record in records:
                try:
                    upload_queue.put_nowait(record)
                except queue.Full:
                    overflow.append(record)
            if overflow:
                store.add_records(overflow)
                self._logger(f"Upload queue full; cached {len(overflow)} records locally.")

        def upload_worker() -> None:
            pending: list[ActivityRecord] = []
            next_upload_at = time.time() + self._config.upload_interval_seconds
            while not stop_event.is_set():
                try:
                    pending.append(upload_queue.get(timeout=0.5))
                except queue.Empty:
                    pass
                if time.time() >= next_upload_at:
                    next_upload_at += self._config.upload_interval_seconds
                    process_report(self._upload(sync_service, login_result_holder[0].token, pending))
                    pending = []
            while True:
                try:
                    pending.append(upload_queue.get_nowait())
                except queue.Empty:
                    break
            if pending:
                process_report(self._upload(sync_service, login_result_holder[0].token, pending))

        startup_report = sync_service.upload_batch(login_result_holder[0].token, [])
        self._logger(
            f"Startup retry complete: uploaded={startup_report.uploaded_count}, cached={startup_report.cached_count}"
        )
        process_report(startup_report)

        self._logger(
            "Running sync client. "
            f"sample_interval={self._config.sample_interval_seconds}s, "
            f"idle_threshold={self._config.idle_threshold_seconds}s, "
            f"upload_interval={self._config.upload_interval_seconds}s."
        )

        upload_thread = threading.Thread(target=upload_worker, daemon=True, name="WorkLensUploader")
        upload_thread.start()

        started_at = time.time()
        next_flush_at = time.time() + self._config.upload_interval_seconds
        next_sample_log_at = time.time() + 300.0
        sample_log_count = 0
        while not stop_event.is_set():
            observed_at = datetime.now().replace(microsecond=0)
            app_name = probe.sample_app_name()
            tracker.observe(app_name, observed_at)
            sample_log_count += 1
            if time.time() >= next_sample_log_at:
                self._logger(
                    f"Sampling active: {sample_log_count} samples since last log (current app {app_name})."
                )
                sample_log_count = 0
                next_sample_log_at = time.time() + 300.0

            if time.time() >= next_flush_at:
                next_flush_at = time.time() + self._config.upload_interval_seconds
                queue_records(tracker.cutoff(observed_at))

            if duration_seconds is not None and time.time() - started_at >= duration_seconds:
                break
            if stop_event.wait(self._config.sample_interval_seconds):
                break

        finished_at = datetime.now().replace(microsecond=0)
        queue_records(tracker.cutoff(finished_at))
        stop_event.set()
        upload_thread.join()

    def _upload(self, sync_service: SyncService, token: str, records: list[ActivityRecord]):
        report = sync_service.upload_batch(token, records)
        self._logger(
            f"flush complete: uploaded={report.uploaded_count}, cached={report.cached_count}"
        )
        return report

    def _log_upload_failure(self, report) -> None:
        failure_code = report.failure_code
        if failure_code == "PASSWORD_CHANGE_REQUIRED":
            self._logger(
                "Upload blocked: current account must change password in the web app before desktop uploads can continue. "
                "Records remain cached locally and will retry after the password is changed."
            )
        elif failure_code == "AUTHENTICATION_FAILED":
            self._logger(
                "Upload blocked: login session is invalid or expired. Attempting to re-login automatically. "
                "Records remain cached locally."
            )
        elif failure_code == "RATE_LIMITED":
            self._logger("Upload throttled by the server (HTTP 429). Uploads will retry later.")
        elif failure_code == "NETWORK_TIMEOUT":
            self._logger("Upload paused: the server did not respond in time. Records remain cached locally.")
        elif failure_code == "NETWORK_ERROR":
            self._logger("Upload paused: unable to reach the WorkLens server. Records remain cached locally.")
        elif failure_code == "SERVER_ERROR":
            self._logger("Upload paused: the WorkLens server is temporarily unavailable. Records remain cached locally.")
        elif failure_code == "UPLOAD_REJECTED":
            self._logger(
                f"Upload rejected by the server: {report.failure_message or 'unknown reason'}. "
                f"Records remain cached locally and will retry."
            )

    @staticmethod
    def _validate_login_result(login_result: LoginResult) -> None:
        if login_result.must_change_password:
            raise LoginError("该账号必须先在网页端修改密码，然后才能启动桌面采集。")
        if login_result.role != "EMPLOYEE":
            raise LoginError("只有员工账号可以运行桌面采集客户端。")
