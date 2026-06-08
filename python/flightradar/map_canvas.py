"""A native Tkinter "slippy map": draws OpenStreetMap tiles and live aircraft onto a single
``tkinter.Canvas``, with mouse pan, scroll-wheel zoom, and click-to-select. No WebView/HTML/JS.

Tiles come from :class:`TileLoader`; geographic projection is :mod:`web_mercator`. Aircraft are drawn
as rotated gold planes (grey when on the ground). To stay smooth over busy regions it applies an
altitude level-of-detail: zoomed out, only the highest flights show.
"""

import math
import tkinter as tk

from . import web_mercator as wm
from .tile_loader import TileLoader

_TILE = 256
_MIN_ZOOM = 2
_MAX_ZOOM = 18
_MAX_PLANES = 1500

_GOLD = "#f8d000"
_GREY = "#b0b0b0"
_OUTLINE = "#5a4500"
_SELECT = "#ff3300"
_SEA = "#aadaff"

# Plane silhouette, pre-centered on the origin and scaled (px), drawn rotated by heading.
_PLANE = [
    (0, -6.2), (1.24, -1.86), (6.2, 0.62), (6.2, 1.86), (1.24, 0.62),
    (0.62, 4.34), (2.48, 5.58), (2.48, 6.2), (0, 5.58), (-2.48, 6.2),
    (-2.48, 5.58), (-0.62, 4.34), (-1.24, 0.62), (-6.2, 1.86),
    (-6.2, 0.62), (-1.24, -1.86),
]


class MapCanvas:
    def __init__(self, parent, schedule):
        self.canvas = tk.Canvas(parent, bg=_SEA, highlightthickness=0)
        self.tiles = TileLoader(schedule, self.redraw)

        self.center_lat = 50.0
        self.center_lon = 10.0
        self.zoom = 5

        self.aircraft = []
        self.selected = None
        self.on_select = lambda a: None

        self._width = 1
        self._height = 1

        # Repaints are coalesced: many triggers (each arriving tile, drag motion, a poll) collapse
        # into a single paint on the next idle cycle. Without this the canvas clears and redraws
        # once per tile as they stream in, which flickers badly on Tkinter's unbuffered Canvas.
        self._paint_scheduled = False

        # Drag state.
        self._press_x = 0.0
        self._press_y = 0.0
        self._press_center_world_x = 0.0
        self._press_center_world_y = 0.0
        self._dragged = False

        c = self.canvas
        c.bind("<Configure>", self._on_resize)
        c.bind("<ButtonPress-1>", self._on_press)
        c.bind("<B1-Motion>", self._on_drag)
        c.bind("<ButtonRelease-1>", self._on_release)
        c.bind("<MouseWheel>", self._on_wheel)        # Windows / macOS
        c.bind("<Button-4>", self._on_wheel)          # Linux scroll up
        c.bind("<Button-5>", self._on_wheel)          # Linux scroll down

    # ---- Public API --------------------------------------------------------

    def set_aircraft(self, states):
        """Replace the displayed aircraft and repaint. Re-resolve the selection by icao24."""
        self.aircraft = states
        if self.selected is not None:
            found = None
            for a in states:
                if a.icao24 is not None and a.icao24 == self.selected.icao24:
                    found = a
                    break
            self.selected = found
            self.on_select(self.selected)
        self.redraw()

    def shutdown(self):
        self.tiles.shutdown()

    # ---- Geometry ----------------------------------------------------------

    def _map_size(self):
        return _TILE * (2 ** self.zoom)

    # ---- Interaction -------------------------------------------------------

    def _on_resize(self, event):
        self._width = event.width
        self._height = event.height
        self.redraw()

    def _on_press(self, event):
        self._press_x = event.x
        self._press_y = event.y
        ms = self._map_size()
        self._press_center_world_x = wm.x_norm(self.center_lon) * ms
        self._press_center_world_y = wm.y_norm(self.center_lat) * ms
        self._dragged = False

    def _on_drag(self, event):
        dx = event.x - self._press_x
        dy = event.y - self._press_y
        if abs(dx) + abs(dy) > 3:
            self._dragged = True
        ms = self._map_size()
        self.center_lon = wm.lon((self._press_center_world_x - dx) / ms)
        self.center_lat = wm.clamp_lat(wm.lat((self._press_center_world_y - dy) / ms))
        self.redraw()

    def _on_release(self, event):
        if not self._dragged:
            self._select_at(event.x, event.y)

    def _on_wheel(self, event):
        # Normalize across platforms: Windows/macOS use event.delta, Linux uses Button-4/5.
        if getattr(event, "num", None) == 4:
            direction = 1
        elif getattr(event, "num", None) == 5:
            direction = -1
        else:
            direction = 1 if event.delta > 0 else -1
        self._zoom_at(event.x, event.y, direction)

    def _zoom_at(self, mx, my, direction):
        """Zoom one level toward/away from the cursor, keeping the point under the cursor fixed."""
        ms = self._map_size()
        origin_x = wm.x_norm(self.center_lon) * ms - self._width / 2
        origin_y = wm.y_norm(self.center_lat) * ms - self._height / 2
        cursor_lon = wm.lon((origin_x + mx) / ms)
        cursor_lat = wm.lat((origin_y + my) / ms)

        new_zoom = max(_MIN_ZOOM, min(_MAX_ZOOM, self.zoom + direction))
        if new_zoom == self.zoom:
            return
        self.zoom = new_zoom

        ms2 = self._map_size()
        new_origin_x = wm.x_norm(cursor_lon) * ms2 - mx
        new_origin_y = wm.y_norm(cursor_lat) * ms2 - my
        self.center_lon = wm.lon((new_origin_x + self._width / 2) / ms2)
        self.center_lat = wm.clamp_lat(wm.lat((new_origin_y + self._height / 2) / ms2))
        self.redraw()

    def _select_at(self, sx, sy):
        ms = self._map_size()
        origin_x = wm.x_norm(self.center_lon) * ms - self._width / 2
        origin_y = wm.y_norm(self.center_lat) * ms - self._height / 2

        best = None
        best_dist = 14 * 14  # within ~14 px
        for a in self._visible():
            px = wm.x_norm(a.longitude) * ms - origin_x
            py = wm.y_norm(a.latitude) * ms - origin_y
            dx = px - sx
            dy = py - sy
            d = dx * dx + dy * dy
            if d < best_dist:
                best_dist = d
                best = a
        self.selected = best
        self.on_select(best)
        self.redraw()

    # ---- Filtering (altitude level-of-detail + cap) ------------------------

    @staticmethod
    def _min_altitude_for_zoom(z):
        if z <= 4:
            return 10000
        if z == 5:
            return 7500
        if z == 6:
            return 5000
        if z == 7:
            return 3000
        if z == 8:
            return 1500
        return float("-inf")

    @staticmethod
    def _alt_of(a):
        return float("-inf") if a.baro_altitude is None else a.baro_altitude

    def _visible(self):
        min_alt = self._min_altitude_for_zoom(self.zoom)
        out = [
            a for a in self.aircraft
            if a.latitude is not None and a.longitude is not None and self._alt_of(a) >= min_alt
        ]
        if len(out) > _MAX_PLANES:
            out.sort(key=self._alt_of, reverse=True)
            out = out[:_MAX_PLANES]
        return out

    # ---- Rendering ---------------------------------------------------------

    def redraw(self):
        """Request a repaint. Coalesces bursts of triggers into a single paint per idle cycle."""
        if self._paint_scheduled:
            return
        self._paint_scheduled = True
        self.canvas.after_idle(self._paint)

    def _paint(self):
        self._paint_scheduled = False
        w, h = self._width, self._height
        if w <= 1 or h <= 1:
            return
        c = self.canvas
        c.delete("all")

        ms = self._map_size()
        n = 1 << self.zoom
        origin_x = wm.x_norm(self.center_lon) * ms - w / 2
        origin_y = wm.y_norm(self.center_lat) * ms - h / 2

        # Tiles overlapping the viewport.
        x_start = math.floor(origin_x / _TILE)
        x_end = math.floor((origin_x + w) / _TILE)
        y_start = math.floor(origin_y / _TILE)
        y_end = math.floor((origin_y + h) / _TILE)
        for ty in range(y_start, y_end + 1):
            if ty < 0 or ty >= n:
                continue  # no vertical wrap
            for tx in range(x_start, x_end + 1):
                wx = ((tx % n) + n) % n  # wrap horizontally
                img = self.tiles.get(self.zoom, wx, ty)
                if img is not None:
                    c.create_image(tx * _TILE - origin_x, ty * _TILE - origin_y,
                                   anchor="nw", image=img)

        # Aircraft.
        for a in self._visible():
            px = wm.x_norm(a.longitude) * ms - origin_x
            py = wm.y_norm(a.latitude) * ms - origin_y
            if px < -20 or py < -20 or px > w + 20 or py > h + 20:
                continue
            self._draw_plane(px, py, a.true_track, a.on_ground, a is self.selected)

        # OSM attribution (required by the tile usage policy).
        c.create_text(6, h - 6, anchor="sw", text="© OpenStreetMap contributors",
                      fill="#444444")

    def _draw_plane(self, x, y, heading, on_ground, is_selected):
        if is_selected:
            self.canvas.create_oval(x - 11, y - 11, x + 11, y + 11, outline=_SELECT, width=2)

        angle = math.radians(heading if heading is not None else 0.0)
        cos_a = math.cos(angle)
        sin_a = math.sin(angle)
        points = []
        for px, py in _PLANE:
            # Clockwise screen rotation (y axis points down), matching the JavaFX version.
            rx = px * cos_a - py * sin_a
            ry = px * sin_a + py * cos_a
            points.append(x + rx)
            points.append(y + ry)

        self.canvas.create_polygon(
            points,
            fill=_GREY if on_ground else _GOLD,
            outline=_OUTLINE,
            width=1,
        )
