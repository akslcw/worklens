import unittest
from datetime import datetime

from worklens_desktop_client.activity_tracker import ActivityTracker


class ActivityTrackerTests(unittest.TestCase):

    def test_consecutive_samples_for_same_app_merge_into_one_record(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:00"))
        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:05"))
        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:10"))
        records = tracker.finish(datetime.fromisoformat("2026-07-04T13:00:15"))

        self.assertEqual(1, len(records))
        self.assertEqual("chrome.exe", records[0].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:00"), records[0].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:15"), records[0].ended_at)

    def test_switching_apps_closes_previous_record_and_starts_next(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:00"))
        tracker.observe("slack.exe", datetime.fromisoformat("2026-07-04T13:00:05"))
        records = tracker.finish(datetime.fromisoformat("2026-07-04T13:00:10"))

        self.assertEqual(2, len(records))
        self.assertEqual("chrome.exe", records[0].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:00"), records[0].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:05"), records[0].ended_at)
        self.assertEqual("slack.exe", records[1].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:05"), records[1].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:10"), records[1].ended_at)

    def test_idle_samples_are_recorded_as_idle(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:00"))
        tracker.observe("Idle", datetime.fromisoformat("2026-07-04T13:05:00"))
        tracker.observe("Idle", datetime.fromisoformat("2026-07-04T13:05:05"))
        records = tracker.finish(datetime.fromisoformat("2026-07-04T13:05:10"))

        self.assertEqual(2, len(records))
        self.assertEqual("chrome.exe", records[0].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:05:00"), records[0].ended_at)
        self.assertEqual("Idle", records[1].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:05:00"), records[1].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:05:10"), records[1].ended_at)

    def test_cutoff_splits_current_record_and_keeps_tracking_same_app(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:00"))
        flushed_records = tracker.cutoff(datetime.fromisoformat("2026-07-04T13:05:00"))
        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:05:05"))
        remaining_records = tracker.finish(datetime.fromisoformat("2026-07-04T13:05:10"))

        self.assertEqual(1, len(flushed_records))
        self.assertEqual("chrome.exe", flushed_records[0].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:00:00"), flushed_records[0].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:05:00"), flushed_records[0].ended_at)

        self.assertEqual(1, len(remaining_records))
        self.assertEqual("chrome.exe", remaining_records[0].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:05:00"), remaining_records[0].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-04T13:05:10"), remaining_records[0].ended_at)

    def test_finish_skips_zero_length_record(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:00:00"))
        records = tracker.finish(datetime.fromisoformat("2026-07-04T13:00:00"))

        self.assertEqual([], records)

    def test_clock_rollback_clamps_instead_of_dropping_record(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T13:05:00"))
        tracker.observe("slack.exe", datetime.fromisoformat("2026-07-04T13:04:00"))
        records = tracker.finish(datetime.fromisoformat("2026-07-04T13:04:05"))

        self.assertEqual(2, len(records))
        self.assertEqual("chrome.exe", records[0].app_name)
        self.assertLess(records[0].started_at, records[0].ended_at)
        self.assertEqual("slack.exe", records[1].app_name)

    def test_cutoff_splits_record_crossing_midnight(self) -> None:
        tracker = ActivityTracker()

        tracker.observe("chrome.exe", datetime.fromisoformat("2026-07-04T23:55:00"))
        records = tracker.cutoff(datetime.fromisoformat("2026-07-05T00:10:00"))

        self.assertEqual(2, len(records))
        self.assertEqual("chrome.exe", records[0].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-04T23:55:00"), records[0].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-05T00:00:00"), records[0].ended_at)
        self.assertEqual("chrome.exe", records[1].app_name)
        self.assertEqual(datetime.fromisoformat("2026-07-05T00:00:00"), records[1].started_at)
        self.assertEqual(datetime.fromisoformat("2026-07-05T00:10:00"), records[1].ended_at)
        self.assertNotEqual(records[0].client_record_id, records[1].client_record_id)


if __name__ == "__main__":
    unittest.main()
