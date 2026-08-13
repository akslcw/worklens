from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from uuid import uuid4


@dataclass(frozen=True)
class ActivityRecord:
    app_name: str
    started_at: datetime
    ended_at: datetime
    client_record_id: str = field(default_factory=lambda: uuid4().hex)


class ActivityTracker:
    def __init__(self) -> None:
        self._completed_records: list[ActivityRecord] = []
        self._current_app_name: str | None = None
        self._current_started_at: datetime | None = None

    def observe(self, app_name: str, observed_at: datetime) -> None:
        if self._current_app_name is None:
            self._current_app_name = app_name
            self._current_started_at = observed_at
            return

        if app_name == self._current_app_name:
            return

        completed_record = self._build_record(self._current_app_name, self._current_started_at, observed_at)
        if completed_record is not None:
            self._completed_records.append(completed_record)
        self._current_app_name = app_name
        self._current_started_at = observed_at

    def cutoff(self, cutoff_at: datetime) -> list[ActivityRecord]:
        records = list(self._completed_records)
        self._completed_records = []
        if self._current_app_name is not None and self._current_started_at is not None:
            current_record = self._build_record(self._current_app_name, self._current_started_at, cutoff_at)
            if current_record is not None:
                records.append(current_record)
            self._current_started_at = cutoff_at
        return records

    def finish(self, finished_at: datetime) -> list[ActivityRecord]:
        records = list(self._completed_records)
        if self._current_app_name is not None and self._current_started_at is not None:
            current_record = self._build_record(self._current_app_name, self._current_started_at, finished_at)
            if current_record is not None:
                records.append(current_record)
        return records

    def _build_record(self, app_name: str, started_at: datetime, ended_at: datetime) -> ActivityRecord | None:
        if ended_at <= started_at:
            return None
        return ActivityRecord(
            app_name=app_name,
            started_at=started_at,
            ended_at=ended_at,
        )
