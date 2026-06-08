# flightradar-java

A simple **FlightRadar analog**: a JavaFX desktop app that shows live aircraft on an
OpenStreetMap map. Live data comes from the [OpenSky Network](https://opensky-network.org/)
REST API (`/states/all`), used anonymously.

The map is rendered **natively in JavaFX** — OpenStreetMap tiles and aircraft are drawn directly
on a `Canvas`. There is no WebView, HTML, or JavaScript.

![architecture](https://img.shields.io/badge/JavaFX-native%20Canvas-blue)

## How it works

```
FlightRadarApp (JavaFX window: BorderPane)
   ├─ MapCanvas (center) ── draws OSM tiles + gold planes on a Canvas
   │     └─ TileLoader ── GET tile.openstreetmap.org/{z}/{x}/{y}.png  (HttpClient, cached)
   ├─ Info panel (right) ── details of the selected aircraft
   ▲
   │  Platform.runLater(map.setAircraft(states))
   │
   AircraftPoller (background, every ~12s)
        └─ OpenSkyClient ── GET /states/all?<bbox> ── List<AircraftState>
```

The poller fetches aircraft states on a background thread; the callback marshals onto the JavaFX
Application Thread to hand them to the map. `MapCanvas` projects each aircraft with
`WebMercator` and paints it as a rotated, gold plane (grey when on the ground). Map tiles are
fetched and decoded off-thread by `TileLoader` and cached in memory.

**Controls:** drag to pan · scroll to zoom · click a plane to select it (details appear in the
side panel).

### Performance

Rendering is the bottleneck for a FlightRadar-style map, so:

- **Everything on one `Canvas`** — tiles and all planes are drawn with a single `GraphicsContext`,
  not as one node/marker per aircraft. This is what keeps hundreds of aircraft fluid.
- **Altitude level-of-detail** — zoomed out, only the highest flights show; the closer you zoom,
  the lower the altitude threshold (`minAltitudeForZoom`), until at zoom ≥ 9 everything (including
  on-ground aircraft) appears.
- **Viewport culling** — only tiles and aircraft inside the visible area are drawn.
- **Plane cap** — a hard limit (`MAX_PLANES`) keeps the highest flights if a view is still dense.
- **In-memory tile cache** — an LRU cache (`TileLoader.MAX_CACHE`) avoids refetching tiles while
  panning/zooming around.

Tune `MAX_PLANES` in `MapCanvas` and `MAX_CACHE` in `TileLoader`.

## Prerequisites

- **JDK 17+**
- **Maven 3.8+**

JavaFX is pulled in as Maven dependencies — no separate SDK install needed.

## Run

```bash
mvn javafx:run
```

A window opens with an OpenStreetMap map centered on Europe. Within ~12 seconds, gold planes
appear and refresh on each poll. Click one for its details; the window title shows the current
aircraft count and last update time.

To target the whole world instead of Europe, change `OpenSkyClient.EUROPE` to
`OpenSkyClient.WORLD` in `FlightRadarApp` (heavier payload — see the rate-limit note below).

## Test

```bash
mvn test
```

- `OpenSkyClientTest` — feeds a captured `/states/all` response to the parser and asserts the
  field mapping (and that rows without a position are skipped).
- `WebMercatorTest` — round-trips lat/lon through the projection and checks known anchor values.

## Notes & caveats

- **OpenSky rate limits (anonymous):** without an account, requests are rate-limited and may
  return HTTP 429. The app handles this gracefully — a failed fetch just skips that update, it
  never crashes. For higher limits, register a free OpenSky account and add OAuth2 client
  credentials (not implemented here).
- **OpenStreetMap tile policy:** `TileLoader` sends a descriptive `User-Agent` (required by OSM)
  and the map shows attribution. The public OSM tile server is fine for personal use; for heavier
  use, switch the URL template in `TileLoader` to a dedicated tile provider.

## Project layout

```
pom.xml
src/main/java/com/example/flightradar/
  FlightRadarApp.java     JavaFX app: window, info panel, wiring
  MapCanvas.java          native Canvas map: tiles + planes, pan/zoom/click
  TileLoader.java         async OSM tile fetch (HttpClient) + LRU image cache
  WebMercator.java        lat/lon <-> Web Mercator projection
  OpenSkyClient.java      HTTP fetch + JSON parsing of /states/all
  AircraftPoller.java     background polling loop
  AircraftState.java      immutable aircraft model (record)
src/test/java/com/example/flightradar/
  OpenSkyClientTest.java  parser unit tests
  WebMercatorTest.java    projection unit tests
```
