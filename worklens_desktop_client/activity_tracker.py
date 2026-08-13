from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, time, timedelta
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
        return self._split_at_midnight(records)

    def finish(self, finished_at: datetime) -> list[ActivityRecord]:
        records = list(self._completed_records)
        if self._current_app_name is not None and self._current_started_at is not None:
            current_record = self._build_record(self._current_app_name, self._current_started_at, finished_at)
            if current_record is not None:
                records.append(current_record)
        return self._split_at_midnight(records)

    def _build_record(self, app_name: str, started_at: datetime, ended_at: datetime) -> ActivityRecord | None:
        if ended_at == started_at:
            return None
        if ended_at < started_at:
            # System clock rolled backwards: clamp instead of silently
            # dropping the segment (the backend requires a positive window).
            started_at = ended_at - timedelta(seconds=1)
        return ActivityRecord(
            app_name=app_name,
            started_at=started_at,
            ended_at=ended_at,
        )

    def _split_at_midnight(self, records: list[ActivityRecord]) -> list[ActivityRecord]:
        split_records: list[ActivityRecord] = []
        for record in records:
            start = record.started_at
            end = record.ended_at
            if start.date() == end.date():
                split_records.append(record)
                continue

            cursor = start
            while cursor.date() != end.date():
                day_end = datetime.combine(cursor.date() + timedelta(days=1), time.min)
                if day_end <= cursor:
                    break
                split_records.append(ActivityRecord(record.app_name, cursor, day_end))
                cursor = day_end
            if end > cursor:
                split_records.append(ActivityRecord(record.app_name, cursor, end))
        return split_records
