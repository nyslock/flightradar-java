# flightradar-python

A simple **FlightRadar analog**: a **Tkinter** desktop app that shows live aircraft on an
OpenStreetMap map. Live data comes from the [OpenSky Network](https://opensky-network.org/)
REST API (`/states/all`), used anonymously.

This is a faithful Python port of the JavaFX app in the parent folder. The map is rendered
**natively on a Tkinter `Canvas`** — OpenStreetMap tiles and aircraft are drawn directly. There is
no WebView, HTML, or JavaScript.

## How it works

```
FlightRadarApp (Tk window)
   ├─ MapCanvas (left) ── draws OSM tiles + gold planes on a tk.Canvas
   │     └─ TileLoader ── GET tile.openstreetmap.org/{z}/{x}/{y}.png  (urllib, thread pool, LRU cache)
   ├─ Info panel (right) ── details of the selected aircraft
   ▲
   │  root.after(0, map.set_aircraft(states))
   │
   AircraftPoller (background thread, every ~12s)
        └─ OpenSkyClient ── GET /states/all?<bbox> ── list[AircraftState]
```

The poller fetches aircraft states on a background thread; the callback marshals onto the Tk main
thread (via `root.after`) to hand them to the map. `MapCanvas` projects each aircraft with
`web_mercator` and paints it as a rotated, gold plane (grey when on the ground). Map tiles are
downloaded and decoded off-thread by `TileLoader`; the `PhotoImage` conversion happens back on the
Tk thread (a Tkinter requirement) and tiles are cached in memory.

**Controls:** drag to pan · scroll to zoom · click a plane to select it (details appear in the
side panel).

## Prerequisites

- **Python 3.9+**
- **Tkinter** — bundled with the standard python.org Windows/macOS installers. On Linux install it
  with your package manager (e.g. `sudo apt install python3-tk`).
- **Pillow** — the only third-party dependency (decodes the PNG map tiles).

## Setup & run

From this `python/` folder:

```powershell
# (optional) create a virtual environment
python -m venv .venv
.\.venv\Scripts\Activate.ps1      # PowerShell on Windows
# source .venv/bin/activate       # macOS / Linux

pip install -r requirements.txt
python main.py
```

A window opens with an OpenStreetMap map centered on Europe. Within ~12 seconds, gold planes appear
and refresh on each poll. Click one for its details; the window title shows the current aircraft
count and last update time.

To target the whole world instead of Europe, pass `WORLD` to the client in `app.py`:
`OpenSkyClient(WORLD)` (heavier payload — see the rate-limit note below).

## Test

```powershell
python -m unittest discover -s tests -v
```

- `test_opensky_client.py` — feeds a captured `/states/all` response to the parser and asserts the
  field mapping (and that rows without a position are skipped).
- `test_web_mercator.py` — round-trips lat/lon through the projection and checks known anchor values.

## Notes & caveats

- **OpenSky rate limits (anonymous):** without an account, requests are rate-limited and may return
  HTTP 429. The app handles this gracefully — a failed fetch just skips that update, it never
  crashes. For higher limits, register a free OpenSky account and add OAuth2 credentials (not
  implemented here).
- **OpenStreetMap tile policy:** `TileLoader` sends a descriptive `User-Agent` (required by OSM) and
  the map shows attribution. The public OSM tile server is fine for personal use; for heavier use,
  switch the URL template in `TileLoader` to a dedicated tile provider.
- **Rendering:** Tkinter's `Canvas` is retained-mode, so each repaint clears and redraws items. A
  hard `MAX_PLANES` cap plus altitude level-of-detail keep it fluid; tune them in `map_canvas.py`.

## Project layout

```
python/
  main.py                       entry point: python main.py
  requirements.txt              Pillow
  flightradar/
    app.py                      Tk app: window, info panel, wiring
    map_canvas.py               native Canvas map: tiles + planes, pan/zoom/click
    tile_loader.py              async OSM tile fetch (urllib) + LRU PhotoImage cache
    web_mercator.py             lat/lon <-> Web Mercator projection
    opensky_client.py           HTTP fetch + JSON parsing of /states/all
    aircraft_poller.py          background polling thread
    aircraft_state.py           immutable aircraft model (dataclass)
  tests/
    test_opensky_client.py      parser unit tests
    test_web_mercator.py        projection unit tests
```
