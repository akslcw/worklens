import unittest
from unittest.mock import Mock
from unittest.mock import patch

from worklens_desktop_client.windows_activity import elapsed_millis_32bit
from worklens_desktop_client.windows_activity import Win32ActivityProbe


class WindowsActivityTests(unittest.TestCase):

    def test_elapsed_millis_handles_get_tick_count_wraparound(self) -> None:
        self.assertEqual(32, elapsed_millis_32bit(0x00000010, 0xFFFFFFF0))

    @patch("worklens_desktop_client.windows_activity.win32gui.GetForegroundWindow", return_value=0)
    def test_locked_screen_reports_locked(self, _get_foreground_window: Mock) -> None:
        probe = Win32ActivityProbe()

        self.assertEqual("Locked", probe.get_foreground_process_name())

    @patch("worklens_desktop_client.windows_activity.win32gui.GetForegroundWindow", return_value=123)
    @patch("worklens_desktop_client.windows_activity.win32gui.GetWindowText", return_value="Windows 设置")
    @patch("worklens_desktop_client.windows_activity.win32process.GetWindowThreadProcessId", return_value=(None, 4242))
    def test_uwp_host_window_uses_window_title(
        self,
        _get_window_thread_process_id: Mock,
        _get_window_text: Mock,
        _get_foreground_window: Mock,
    ) -> None:
        process = Mock()
        process.name.return_value = "ApplicationFrameHost.exe"
        with patch("worklens_desktop_client.windows_activity.psutil.Process", return_value=process):
            probe = Win32ActivityProbe()

            self.assertEqual("Windows 设置", probe.get_foreground_process_name())


if __name__ == "__main__":
    unittest.main()
