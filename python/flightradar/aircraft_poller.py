"""Periodically polls an :class:`OpenSkyClient` on a background thread and delivers each batch of
aircraft states to a callback.

The callback runs on the poller's own (non-UI) thread. Callers that touch Tkinter must marshal back
onto the Tk main thread themselves (e.g. via ``root.after(0, ...)``).
"""

import threading


class AircraftPoller:
    def __init__(self, client, period_seconds, on_update):
        self._client = client
        self._period = period_seconds
        self._on_update = on_update
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        """Start polling immediately, then every ``period_seconds``. Idempotent per instance."""
        if self._thread is not None:
            return
        self._thread = threading.Thread(target=self._run, name="aircraft-poller", daemon=True)
        self._thread.start()

    def _run(self):
        while not self._stop.is_set():
            try:
                states = self._client.fetch_states()
                self._on_update(states)
            except Exception as exc:  # noqa: BLE001 - never let an exception kill the loop
                print(f"[Poller] update failed: {exc}")
            self._stop.wait(self._period)

    def stop(self):
        """Stop polling and release the background thread."""
        self._stop.set()
