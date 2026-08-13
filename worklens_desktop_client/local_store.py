from __future__ import annotations

import sqlite3
from contextlib import closing
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from worklens_desktop_client.activity_tracker import ActivityRecord


@dataclass(frozen=True)
class PendingRecord:
    local_id: int
    app_name: str
    started_at: datetime
    ended_at: datetime
    client_record_id: str


class LocalRecordStore:
    def __init__(self, database_path: str) -> None:
        self._database_path = Path(database_path)
        self._database_path.parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    def add_records(self, records: list[ActivityRecord]) -> None:
        if not records:
            return
        with closing(sqlite3.connect(self._database_path)) as connection:
            connection.executemany(
                """
                INSERT INTO pending_usage_records (app_name, started_at, ended_at, client_record_id)
                VALUES (?, ?, ?, ?)
                """,
                [
                    (
                        record.app_name,
                        record.started_at.isoformat(timespec="seconds"),
                        record.ended_at.isoformat(timespec="seconds"),
                        record.client_record_id,
                    )
                    for record in records
                ],
            )
            connection.commit()

    def list_pending_records(self) -> list[PendingRecord]:
        with closing(sqlite3.connect(self._database_path)) as connection:
            rows = connection.execute(
                """
                SELECT id, app_name, started_at, ended_at, client_record_id
                FROM pending_usage_records
                WHERE rejected = 0
                ORDER BY id ASC
                """
            ).fetchall()
        return [
            PendingRecord(
                local_id=row[0],
                app_name=row[1],
                started_at=datetime.fromisoformat(row[2]),
                ended_at=datetime.fromisoformat(row[3]),
                client_record_id=row[4] or uuid4().hex,
            )
            for row in rows
        ]

    def mark_rejected(self, local_id: int) -> None:
        """Quarantines a record the server permanently rejected (4xx), so it
        is no longer retried automatically."""
        with closing(sqlite3.connect(self._database_path)) as connection:
            connection.execute(
                "UPDATE pending_usage_records SET rejected = 1 WHERE id = ?",
                (local_id,),
            )
            connection.commit()

    def rejected_count(self) -> int:
        with closing(sqlite3.connect(self._database_path)) as connection:
            row = connection.execute(
                "SELECT COUNT(*) FROM pending_usage_records WHERE rejected = 1"
            ).fetchone()
        return int(row[0])

    def delete_records(self, local_ids: list[int]) -> None:
        if not local_ids:
            return
        placeholders = ",".join("?" for _ in local_ids)
        with closing(sqlite3.connect(self._database_path)) as connection:
            connection.execute(
                f"DELETE FROM pending_usage_records WHERE id IN ({placeholders})",
                local_ids,
            )
            connection.commit()

    def _initialize(self) -> None:
        with closing(sqlite3.connect(self._database_path)) as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS pending_usage_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    app_name TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    ended_at TEXT NOT NULL
                )
                """
            )
            connection.commit()
            self._migrate_to_client_record_id(connection)

    def _migrate_to_client_record_id(self, connection: sqlite3.Connection) -> None:
        columns = {
            row[1]
            for row in connection.execute("PRAGMA table_info(pending_usage_records)").fetchall()
        }
        if "client_record_id" not in columns:
            connection.execute(
                "ALTER TABLE pending_usage_records ADD COLUMN client_record_id TEXT"
            )
            connection.commit()
        if "rejected" not in columns:
            connection.execute(
                "ALTER TABLE pending_usage_records ADD COLUMN rejected INTEGER NOT NULL DEFAULT 0"
            )
            connection.commit()
        connection.execute(
            "UPDATE pending_usage_records SET client_record_id = lower(hex(randomblob(16))) "
            "WHERE client_record_id IS NULL OR client_record_id = ''"
        )
        connection.commit()
