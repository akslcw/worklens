import sqlite3
import tempfile
import unittest
from contextlib import closing
from datetime import datetime
from pathlib import Path

from worklens_desktop_client.activity_tracker import ActivityRecord
from worklens_desktop_client.local_store import LocalRecordStore


class LocalRecordStoreTests(unittest.TestCase):

    def test_pending_round_trip_preserves_client_record_id(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            store.add_records([
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                    client_record_id="client-record-0001",
                )
            ])

            pending = store.list_pending_records()

            self.assertEqual(1, len(pending))
            self.assertEqual("client-record-0001", pending[0].client_record_id)

    def test_legacy_database_without_client_record_id_column_is_migrated(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            database_path = str(Path(temp_dir) / "cache.sqlite3")
            with closing(sqlite3.connect(database_path)) as connection:
                connection.execute(
                    """
                    CREATE TABLE pending_usage_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        app_name TEXT NOT NULL,
                        started_at TEXT NOT NULL,
                        ended_at TEXT NOT NULL
                    )
                    """
                )
                connection.execute(
                    "INSERT INTO pending_usage_records (app_name, started_at, ended_at) VALUES (?, ?, ?)",
                    ("Idle", "2026-07-04T13:50:00", "2026-07-04T13:55:00"),
                )
                connection.commit()

            store = LocalRecordStore(database_path)
            pending = store.list_pending_records()

            self.assertEqual(1, len(pending))
            self.assertTrue(pending[0].client_record_id)
            self.assertEqual(32, len(pending[0].client_record_id))
            self.assertEqual(0, store.rejected_count())

    def test_mark_rejected_quarantines_record_from_retries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = LocalRecordStore(str(Path(temp_dir) / "cache.sqlite3"))
            store.add_records([
                ActivityRecord(
                    app_name="chrome.exe",
                    started_at=datetime.fromisoformat("2026-07-04T14:00:00"),
                    ended_at=datetime.fromisoformat("2026-07-04T14:05:00"),
                    client_record_id="client-record-0001",
                )
            ])
            pending = store.list_pending_records()
            store.mark_rejected(pending[0].local_id)

            self.assertEqual(0, len(store.list_pending_records()))
            self.assertEqual(1, store.rejected_count())


if __name__ == "__main__":
    unittest.main()
