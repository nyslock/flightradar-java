"""Fetches live aircraft states from the OpenSky Network REST API (``/states/all``).

Used anonymously (no account / OAuth), which is the simplest path but is subject to stricter rate
limits. Any failure (HTTP 429/5xx, timeout, parse error) yields an empty list rather than an
exception, so the UI keeps running and simply skips that update.
"""

import json
from dataclasses import dataclass
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from .aircraft_state import AircraftState

_BASE_URL = "https://opensky-network.org/api/states/all"
_USER_AGENT = "flightradar-python/1.0 (personal demo)"
_TIMEOUT_SECONDS = 10


@dataclass(frozen=True)
class BoundingBox:
    """Bounding box (lamin, lomin, lamax, lomax) in degrees, used to scope and shrink the query."""
    la_min: float
    lo_min: float
    la_max: float
    lo_max: float


#: Roughly continental Europe -- a sensible, traffic-dense default.
EUROPE = BoundingBox(35.0, -12.0, 60.0, 30.0)

#: The whole planet. Heavier payload and higher rate-limit cost; use with care.
WORLD = BoundingBox(-90.0, -180.0, 90.0, 180.0)


class OpenSkyClient:
    def __init__(self, bounding_box=EUROPE):
        self._bbox = bounding_box

    def fetch_states(self):
        """Fetch the current aircraft states within the configured bounding box.

        Returns the live states, or an empty list on any error (never ``None``).
        """
        try:
            request = Request(self._build_url(), headers={"User-Agent": _USER_AGENT})
            with urlopen(request, timeout=_TIMEOUT_SECONDS) as response:
                status = getattr(response, "status", response.getcode())
                if status != 200:
                    print(f"[OpenSky] HTTP {status} -- skipping this update.")
                    return []
                body = response.read().decode("utf-8")
            return self.parse_states(body)
        except Exception as exc:  # noqa: BLE001 - never let a bad fetch kill the app
            print(f"[OpenSky] request failed: {exc} -- skipping this update.")
            return []

    def _build_url(self):
        query = urlencode({
            "lamin": self._bbox.la_min,
            "lomin": self._bbox.lo_min,
            "lamax": self._bbox.la_max,
            "lomax": self._bbox.lo_max,
        })
        return f"{_BASE_URL}?{query}"

    @staticmethod
    def parse_states(body):
        """Parse an OpenSky ``/states/all`` response body into aircraft states.

        Rows without a usable position are skipped. Static so it can be unit tested without HTTP.
        """
        result = []
        try:
            root = json.loads(body)
            states = root.get("states")
            if not isinstance(states, list):
                return result
            for row in states:
                state = AircraftState.from_state_array(row)
                if state is not None:
                    result.append(state)
        except Exception as exc:  # noqa: BLE001
            print(f"[OpenSky] failed to parse response: {exc}")
        return result
