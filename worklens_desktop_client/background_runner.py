from __future__ import annotations

import threading
from collections.abc import Callable


class BackgroundRunner:
    def __init__(
        self,
        worker: Callable[[threading.Event], None],
        on_status_change: Callable[[str], None],
        on_error: Callable[[Exception], None] | None = None,
    ) -> None:
        self._worker = worker
        self._on_status_change = on_status_change
        self._on_error = on_error
        self._stop_event = threading.Event()
        self._running_event = threading.Event()
        self._thread: threading.Thread | None = None
        self.last_error: Exception | None = None

    def start(self) -> None:
        if self._thread is not None and self._thread.is_alive():
            return
        self.last_error = None
        self._stop_event = threading.Event()
        self._running_event = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True, name="WorkLensBackgroundRunner")
        self._thread.start()

    def request_stop(self) -> None:
        self._stop_event.set()

    def join(self, timeout: float | None = None) -> None:
        if self._thread is not None:
            self._thread.join(timeout=timeout)

    def is_alive(self) -> bool:
        return self._thread is not None and self._thread.is_alive()

    def wait_until_running(self, timeout: float | None = None) -> bool:
        return self._running_event.wait(timeout=timeout)

    def _run(self) -> None:
        self._running_event.set()
        self._on_status_change("RUNNING")
        try:
            self._worker(self._stop_event)
        except Exception as error:  # pragma: no cover - exercised in tests
            self.last_error = error
            if self._on_error is not None:
                self._on_error(error)
        finally:
            self._on_status_change("STOPPED")
