"""Loads OpenStreetMap raster tiles over HTTP and decodes them into Tkinter images, with an
in-memory LRU cache.

Tiles are downloaded and decoded on a small background thread pool (never on the Tk main thread).
Each request sends a descriptive ``User-Agent`` as required by the OSM tile usage policy -- default
user agents are blocked. Because Tkinter ``PhotoImage`` objects may only be created and used on the
main thread, the background worker only produces a Pillow image; the conversion to ``PhotoImage``,
the cache write, and the repaint callback are all marshalled back onto the Tk thread via the
``schedule`` function supplied by the caller.

``get`` returns a cached tile immediately or ``None`` while a fetch is in flight.
"""

import threading
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from urllib.request import Request, urlopen

from PIL import Image, ImageTk

_URL_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
_USER_AGENT = "flightradar-python/1.0 (personal desktop app)"

#: Max decoded tiles kept in memory (~256 KB each -> a few tens of MB).
_MAX_CACHE = 256


class TileLoader:
    def __init__(self, schedule, on_tile_loaded):
        # schedule(fn): run fn on the Tk main thread (e.g. root.after(0, fn)).
        self._schedule = schedule
        self._on_tile_loaded = on_tile_loaded
        self._pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="tile-loader")
        self._cache = OrderedDict()  # key -> PhotoImage; only touched on the Tk thread
        self._in_flight = set()
        self._in_flight_lock = threading.Lock()
        self._closed = False

    def get(self, z, x, y):
        """Return the tile image if cached, otherwise ``None`` and kick off an async fetch (the
        repaint callback fires once it arrives)."""
        key = (z, x, y)
        photo = self._cache.get(key)
        if photo is not None:
            self._cache.move_to_end(key)
            return photo
        with self._in_flight_lock:
            if key in self._in_flight:
                return None
            self._in_flight.add(key)
        self._pool.submit(self._fetch, z, x, y, key)
        return None

    def _fetch(self, z, x, y, key):
        try:
            url = _URL_TEMPLATE.format(z=z, x=x, y=y)
            request = Request(url, headers={"User-Agent": _USER_AGENT})
            with urlopen(request, timeout=15) as response:
                status = getattr(response, "status", response.getcode())
                if status == 200:
                    data = response.read()
                    image = Image.open(BytesIO(data)).convert("RGBA")
                    image.load()
                    self._schedule(lambda: self._store(key, image))
        except Exception:  # noqa: BLE001 - tile stays blank, may be retried on a later pan
            pass
        finally:
            with self._in_flight_lock:
                self._in_flight.discard(key)

    def _store(self, key, pil_image):
        if self._closed:
            return
        self._cache[key] = ImageTk.PhotoImage(pil_image)
        self._cache.move_to_end(key)
        while len(self._cache) > _MAX_CACHE:
            self._cache.popitem(last=False)
        self._on_tile_loaded()

    def shutdown(self):
        self._closed = True
        self._pool.shutdown(wait=False)
