import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import requests

from worklens_desktop_client.activity_tracker import ActivityRecord
from worklens_desktop_client.local_store import LocalRecordStore
from worklens_desktop_client.sync_service import SyncService


class FakeApiClient:
    def __init__(self, failures_before_success: int = 0) -> None:
        self.failures_before_success = failures_before_success
        self.calls: list[tuple[str, str, datetime, datetime, str | None]] = []

    def create_usage_record(
        self,
        token: str,
        app_name: str,
        started_at: datetime,
        ended_at: datetime,
        client_record_id: str | None = None,
    ) -> dict:
        self.calls.append((token, app_name, started_at, ended_at, client_record_id))
        if self.failures_before_success > 0:
            self.failures_before_success -= 1
            raise requests.ConnectionError("network down")
        return {
            "id": len(self.calls),
            "appName": app_name,
            "startedAt": started_at.isoformat(timespec="seconds"),
            "endedAt": ended_at.isoformat(timespec="seconds"),
        }


class PasswordChangeRequiredApiClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, datetime, datetime, str | None]] = []

    def create_usage_record(
        self,
        token: str,
        app_name: str,
        started_at: datetime,
        ended_at: datetime,
        client_record_id: str | None = None,
    ) -> dict:
        self.calls.append((token, app_name, started_at, ended_at, client_record_id))
        response = requests.Response()
        response.status_code = 403
        response._content = b'{"message":"Password change required"}'
        raise requests.HTTPError("403 Client Error: Forbidden", response=response)


class SyncServiceTests(unittest.TestCase):

    def test_failure_classification_reports_common_network_and_http_errors(self) -> None:
        service = SyncService(object(), object())

        self.assertEqual(
            ("NETWORK_ERROR", "Unable to reach the WorkLens server."),
            service._classify_failure(requests.ConnectionError("network down")),
        )
        expected_codes = {
            401: "AUTHENTICATION_FAILED",
            429: "RATE_LIMITED",
            500: "SERVER_ERROR",
            503: "SERVER_ERROR",
        }
        for status_code, expected_code in expected_codes.items():
            with self.subTest(status_code=status_code):
                response = requests.Response()
                response.status_code = status_code
                response._content = b""
                error = requests.HTTPError(response=response)
                code, message = service._classify_failure(error)
                self.assertEqual(expected_code, code)
                self.assertTrue(message)

    def test_failed_upload_caches_new_records(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            api_client = FakeApiClient(failures_before_success=1)
            service = SyncService(api_client, store)
            records = [
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                )
            ]

            report = service.upload_batch("token-1", records)

            self.assertEqual(0, report.uploaded_count)
            self.assertEqual(1, report.cached_count)
            pending_records = store.list_pending_records()
            self.assertEqual(1, len(pending_records))
            self.assertEqual("chrome.exe", pending_records[0].app_name)

    def test_successful_upload_flushes_pending_before_new_records(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            store.add_records([
                ActivityRecord(
                    app_name="Idle",
                    started_at=datetime.fromisoformat("2026-07-04T13:50:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T13:55:00"),
                )
            ])
            api_client = FakeApiClient()
            service = SyncService(api_client, store)
            new_records = [
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                )
            ]

            report = service.upload_batch("token-2", new_records)

            self.assertEqual(2, report.uploaded_count)
            self.assertEqual(0, report.cached_count)
            self.assertEqual(0, len(store.list_pending_records()))
            self.assertEqual(
                [
                    ("token-2", "Idle", datetime.fromisoformat("2026-07-04T13:50:00"), datetime.fromisoformat("2026-07-04T13:55:00")),
                    ("token-2", "chrome.exe", datetime.fromisoformat("2026-07-04T14:00:00"), datetime.fromisoformat("2026-07-04T14:05:00")),
                ],
                [call[:4] for call in api_client.calls],
            )

    def test_retry_after_failure_reuses_same_client_record_id(self) -> None:
        """C3: a record cached after a network failure keeps its client record
        id, so the retry is idempotent on the server side."""
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            api_client = FakeApiClient(failures_before_success=1)
            service = SyncService(api_client, store)
            records = [
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                    client_record_id="client-record-0001",
                )
            ]

            first_report = service.upload_batch("token-1", records)
            self.assertEqual(0, first_report.uploaded_count)
            self.assertEqual(1, first_report.cached_count)

            retry_report = service.upload_batch("token-1", [])
            self.assertEqual(1, retry_report.uploaded_count)
            self.assertEqual(0, retry_report.cached_count)

            self.assertEqual(2, len(api_client.calls))
            self.assertEqual("client-record-0001", api_client.calls[0][4])
            self.assertEqual("client-record-0001", api_client.calls[1][4])

    def test_partial_failure_keeps_unsent_pending_and_caches_remaining_new_records(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            store.add_records([
                ActivityRecord(
                    app_name="Idle",
                    started_at=datetime.fromisoformat("2026-07-04T13:50:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T13:55:00"),
                )
            ])
            api_client = FakeApiClient(failures_before_success=1)
            service = SyncService(api_client, store)
            new_records = [
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                )
            ]

            report = service.upload_batch("token-3", new_records)

            self.assertEqual(0, report.uploaded_count)
            self.assertEqual(2, report.cached_count)
            pending_records = store.list_pending_records()
            self.assertEqual(2, len(pending_records))
            self.assertEqual("Idle", pending_records[0].app_name)
            self.assertEqual("chrome.exe", pending_records[1].app_name)

    def test_password_change_required_failure_is_reported_and_records_stay_cached(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            api_client = PasswordChangeRequiredApiClient()
            service = SyncService(api_client, store)
            records = [
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                )
            ]

            report = service.upload_batch("token-4", records)

            self.assertEqual(0, report.uploaded_count)
            self.assertEqual(1, report.cached_count)
            self.assertEqual("PASSWORD_CHANGE_REQUIRED", report.failure_code)
            self.assertIn("must change password", report.failure_message)
            pending_records = store.list_pending_records()
            self.assertEqual(1, len(pending_records))
            self.assertEqual("chrome.exe", pending_records[0].app_name)


if __name__ == "__main__":
    unittest.main()
