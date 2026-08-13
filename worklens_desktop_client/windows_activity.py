from __future__ import annotations

import ctypes
from ctypes import Structure, byref, sizeof

import psutil
import win32gui
import win32process


def elapsed_millis_32bit(current_tick: int, last_input_tick: int) -> int:
    return (current_tick - last_input_tick) & 0xFFFFFFFF


class LastInputInfo(Structure):
    _fields_ = [
        ("cbSize", ctypes.c_uint),
        ("dwTime", ctypes.c_uint),
    ]


class Win32ActivityProbe:
    def __init__(self, idle_threshold_seconds: int = 300) -> None:
        self._idle_threshold_seconds = idle_threshold_seconds

    def sample_app_name(self) -> str:
        if self.get_idle_seconds() >= self._idle_threshold_seconds:
            return "Idle"
        return self.get_foreground_process_name()

    def get_idle_seconds(self) -> float:
        last_input_info = LastInputInfo()
        last_input_info.cbSize = sizeof(LastInputInfo)
        if not ctypes.windll.user32.GetLastInputInfo(byref(last_input_info)):
            raise OSError("GetLastInputInfo failed")
        elapsed_millis = elapsed_millis_32bit(
            ctypes.windll.kernel32.GetTickCount(),
            last_input_info.dwTime,
        )
        return elapsed_millis / 1000.0

    def get_foreground_process_name(self) -> str:
        foreground_window = win32gui.GetForegroundWindow()
        if foreground_window == 0:
            return "Locked"

        title = win32gui.GetWindowText(foreground_window)
        _, process_id = win32process.GetWindowThreadProcessId(foreground_window)
        try:
            name = psutil.Process(process_id).name()
        except (psutil.Error, OSError):
            return f"pid-{process_id}"

        # UWP apps all report ApplicationFrameHost.exe; use the window title as
        # the stable app identity instead of lumping them together.
        if name.lower() == "applicationframehost.exe" and title.strip():
            return title.strip()
        return name
