"""One aircraft's live state, mapped from a single OpenSky ``/states/all`` row.

OpenSky returns each state vector as a positional JSON array (not an object). The relevant indices:

    0 icao24 | 1 callsign | 2 origin_country | 5 longitude | 6 latitude | 7 baro_altitude |
    8 on_ground | 9 velocity(m/s) | 10 true_track(heading deg) | 11 vertical_rate | 13 geo_altitude

This is an immutable value type. Use :meth:`AircraftState.from_state_array` to build one from a row.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class AircraftState:
    icao24: Optional[str]
    callsign: Optional[str]
    origin_country: Optional[str]
    longitude: Optional[float]
    latitude: Optional[float]
    baro_altitude: Optional[float]
    on_ground: bool
    velocity: Optional[float]
    true_track: Optional[float]
    vertical_rate: Optional[float]

    @staticmethod
    def from_state_array(row):
        """Map a single OpenSky state-vector array into an :class:`AircraftState`.

        Returns ``None`` if the row has no usable position (lat/lon missing).
        """
        if not isinstance(row, (list, tuple)) or len(row) < 11:
            return None

        longitude = _as_float(row[5])
        latitude = _as_float(row[6])
        if longitude is None or latitude is None:
            return None  # no position => nothing to plot

        callsign = _as_text(row[1])
        if callsign is not None:
            callsign = callsign.strip()

        return AircraftState(
            icao24=_as_text(row[0]),
            callsign=callsign,
            origin_country=_as_text(row[2]),
            longitude=longitude,
            latitude=latitude,
            baro_altitude=_as_float(row[7]),
            on_ground=_as_bool(row[8]),
            velocity=_as_float(row[9]),
            true_track=_as_float(row[10]),
            vertical_rate=_as_float(row[11]) if len(row) > 11 else None,
        )


def _as_text(value):
    return None if value is None else str(value)


def _as_float(value):
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _as_bool(value):
    return bool(value) if value is not None else False
