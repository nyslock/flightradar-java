# flightradar-java

A simple **FlightRadar analog**: a JavaFX desktop app that shows live aircraft on an
OpenStreetMap map. Live data comes from the [OpenSky Network](https://opensky-network.org/)
REST API (`/states/all`), used anonymously.

![architecture](https://img.shields.io/badge/JavaFX-WebView%20%2B%20Leaflet-blue)

## How it works

```
FlightRadarApp (JavaFX window)
   └─ WebView ── map.html (Leaflet + OpenStreetMap tiles)
        ▲  executeScript("updateAircraft(...)")  on the JavaFX thread
        │
   AircraftPoller (background, every ~12s)
        └─ OpenSkyClient ── GET /states/all?<bbox> ── List<AircraftState>
```

The poller fetches aircraft states off-thread, then the app marshals back onto the JavaFX
Application Thread and calls the page's `updateAircraft(list)` JS function. Leaflet renders a
rotated, gold plane marker per aircraft, with a popup showing callsign, country, altitude,
speed, and heading.

### Performance: zoom-based level of detail

To stay smooth over busy regions, the map does **not** draw every aircraft at once. In
`map.html`:

- **Altitude level-of-detail** — when zoomed out, only the highest-altitude flights are shown;
  the closer you zoom, the lower the altitude threshold (`minAltitudeForZoom`), until at
  zoom ≥ 9 everything (including on-ground aircraft) appears.
- **Viewport culling** — only aircraft within the visible map area (plus a small margin) are
  drawn, so panning/zooming re-filters the already-fetched data without a new request.
- **Marker cap** — a hard limit (`MAX_MARKERS`) keeps the highest flights if a region is still
  dense. Tune `MAX_MARKERS` / `VIEWPORT_PADDING` at the top of the `map.html` script.

A small overlay (top-right) shows how many of the fetched aircraft are currently displayed.

## Prerequisites

- **JDK 17+**
- **Maven 3.8+**

JavaFX is pulled in as Maven dependencies — no separate SDK install needed.

## Run

```bash
mvn javafx:run
```

A window opens with an OpenStreetMap map centered on Europe. Within ~12 seconds, plane markers
appear and refresh on each poll. Click a marker for flight details. The window title shows the
current aircraft count and last update time.

To target the whole world instead of Europe, change `OpenSkyClient.EUROPE` to
`OpenSkyClient.WORLD` in `FlightRadarApp` (heavier payload — see the rate-limit note below).

## Test

```bash
mvn test
```

`OpenSkyClientTest` feeds a captured sample `/states/all` response to the parser and asserts the
field mapping (and that rows without a position are skipped).

## Notes & caveats

- **OpenSky rate limits (anonymous):** without an account, requests are rate-limited and may
  return HTTP 429. The app handles this gracefully — a failed fetch just skips that update, it
  never crashes. For higher limits, register a free OpenSky account and add OAuth2 client
  credentials (not implemented here).
- **OpenStreetMap tile policy:** the public OSM tile server is fine for personal/demo use with
  correct attribution. For heavier use, switch to a dedicated tile provider in `map.html`.

## Project layout

```
pom.xml
src/main/java/com/example/flightradar/
  FlightRadarApp.java     JavaFX app: window, WebView, wiring
  OpenSkyClient.java      HTTP fetch + JSON parsing of /states/all
  AircraftPoller.java     background polling loop
  AircraftState.java      immutable aircraft model (record)
src/main/resources/
  map.html                Leaflet + OSM map, updateAircraft(list)
src/test/java/com/example/flightradar/
  OpenSkyClientTest.java  parser unit tests
```
