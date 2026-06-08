"""Web Mercator (EPSG:3857) projection helpers -- the same projection used by OpenStreetMap's
standard tiles.

Coordinates here are *normalized* to the unit square [0, 1): multiply by the map size in pixels
(``256 * 2**zoom``) to get world-pixel coordinates. Longitude maps linearly to x; latitude maps to
y through the Mercator transform. Both directions are provided so the map view can project aircraft
to the screen and convert mouse positions back to lat/lon.
"""

import math

#: OSM/Web-Mercator latitude limit (beyond this the projection diverges).
MAX_LATITUDE = 85.05112878


def x_norm(lon):
    """Longitude (deg) -> normalized x in [0, 1)."""
    return (lon + 180.0) / 360.0


def y_norm(lat):
    """Latitude (deg) -> normalized y in [0, 1) (0 = north edge, 1 = south edge)."""
    r = math.radians(lat)
    return (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0


def lon(x_norm_value):
    """Normalized x -> longitude (deg)."""
    return x_norm_value * 360.0 - 180.0


def lat(y_norm_value):
    """Normalized y -> latitude (deg)."""
    n = math.pi * (1.0 - 2.0 * y_norm_value)
    return math.degrees(math.atan(math.sinh(n)))


def clamp_lat(value):
    """Clamp a latitude to the projection's valid range."""
    return max(-MAX_LATITUDE, min(MAX_LATITUDE, value))
