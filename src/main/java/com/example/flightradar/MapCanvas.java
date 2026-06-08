package com.example.flightradar;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A native JavaFX "slippy map": draws OpenStreetMap tiles and live aircraft onto a single
 * {@link Canvas}, with mouse pan, scroll-wheel zoom, and click-to-select. No WebView/HTML/JS.
 *
 * <p>Tiles come from {@link TileLoader}; geographic projection is {@link WebMercator}. Aircraft are
 * drawn as rotated gold planes (grey when on the ground). To stay smooth over busy regions it
 * applies the same altitude level-of-detail as before: zoomed out, only the highest flights show.
 */
public class MapCanvas extends Pane {

    private static final int TILE = 256;
    private static final int MIN_ZOOM = 2;
    private static final int MAX_ZOOM = 18;
    private static final int MAX_PLANES = 1500;

    private static final Color GOLD = Color.web("#f8d000");
    private static final Color GREY = Color.web("#b0b0b0");
    private static final Color OUTLINE = Color.web("#5a4500");
    private static final Color SELECT = Color.web("#ff3300");
    private static final Color SEA = Color.web("#aadaff");

    // Plane silhouette, pre-centered on the origin and scaled (px), drawn rotated by heading.
    private static final double[][] PLANE = {
            {0, -6.2}, {1.24, -1.86}, {6.2, 0.62}, {6.2, 1.86}, {1.24, 0.62},
            {0.62, 4.34}, {2.48, 5.58}, {2.48, 6.2}, {0, 5.58}, {-2.48, 6.2},
            {-2.48, 5.58}, {-0.62, 4.34}, {-1.24, 0.62}, {-6.2, 1.86},
            {-6.2, 0.62}, {-1.24, -1.86}
    };

    private final Canvas canvas = new Canvas();
    private final TileLoader tiles;

    private double centerLat = 50.0;
    private double centerLon = 10.0;
    private int zoom = 5;

    private List<AircraftState> aircraft = List.of();
    private AircraftState selected;
    private Consumer<AircraftState> onSelect = a -> { };

    // Drag state.
    private double pressX, pressY, pressCenterWorldX, pressCenterWorldY;
    private boolean dragged;

    public MapCanvas() {
        tiles = new TileLoader(this::redraw);
        getChildren().add(canvas);

        widthProperty().addListener((obs, ov, nv) -> { canvas.setWidth(nv.doubleValue()); redraw(); });
        heightProperty().addListener((obs, ov, nv) -> { canvas.setHeight(nv.doubleValue()); redraw(); });

        setOnMousePressed(this::onPress);
        setOnMouseDragged(this::onDrag);
        setOnMouseReleased(this::onRelease);
        setOnScroll(e -> onScroll(e.getX(), e.getY(), e.getDeltaY()));
    }

    /** Registers a callback invoked with the selected aircraft (or {@code null} when cleared). */
    public void setOnAircraftSelected(Consumer<AircraftState> callback) {
        this.onSelect = callback;
    }

    /** Replaces the displayed aircraft and repaints. Re-resolves the current selection by icao24. */
    public void setAircraft(List<AircraftState> list) {
        this.aircraft = list;
        if (selected != null) {
            AircraftState found = null;
            for (AircraftState a : list) {
                if (a.icao24() != null && a.icao24().equals(selected.icao24())) {
                    found = a;
                    break;
                }
            }
            selected = found;
            onSelect.accept(selected);
        }
        redraw();
    }

    public void shutdown() {
        tiles.shutdown();
    }

    private double mapSize() {
        return TILE * Math.pow(2, zoom);
    }

    // ---- Interaction -------------------------------------------------------

    private void onPress(MouseEvent e) {
        pressX = e.getX();
        pressY = e.getY();
        double ms = mapSize();
        pressCenterWorldX = WebMercator.xNorm(centerLon) * ms;
        pressCenterWorldY = WebMercator.yNorm(centerLat) * ms;
        dragged = false;
    }

    private void onDrag(MouseEvent e) {
        double dx = e.getX() - pressX;
        double dy = e.getY() - pressY;
        if (Math.abs(dx) + Math.abs(dy) > 3) {
            dragged = true;
        }
        double ms = mapSize();
        centerLon = WebMercator.lon((pressCenterWorldX - dx) / ms);
        centerLat = WebMercator.clampLat(WebMercator.lat((pressCenterWorldY - dy) / ms));
        redraw();
    }

    private void onRelease(MouseEvent e) {
        if (!dragged) {
            selectAt(e.getX(), e.getY());
        }
    }

    /** Zoom one level toward/away from the cursor, keeping the point under the cursor fixed. */
    private void onScroll(double mx, double my, double deltaY) {
        double ms = mapSize();
        double originX = WebMercator.xNorm(centerLon) * ms - canvas.getWidth() / 2;
        double originY = WebMercator.yNorm(centerLat) * ms - canvas.getHeight() / 2;
        double cursorLon = WebMercator.lon((originX + mx) / ms);
        double cursorLat = WebMercator.lat((originY + my) / ms);

        int newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + (deltaY > 0 ? 1 : -1)));
        if (newZoom == zoom) {
            return;
        }
        zoom = newZoom;

        double ms2 = mapSize();
        double newOriginX = WebMercator.xNorm(cursorLon) * ms2 - mx;
        double newOriginY = WebMercator.yNorm(cursorLat) * ms2 - my;
        centerLon = WebMercator.lon((newOriginX + canvas.getWidth() / 2) / ms2);
        centerLat = WebMercator.clampLat(WebMercator.lat((newOriginY + canvas.getHeight() / 2) / ms2));
        redraw();
    }

    private void selectAt(double sx, double sy) {
        double ms = mapSize();
        double originX = WebMercator.xNorm(centerLon) * ms - canvas.getWidth() / 2;
        double originY = WebMercator.yNorm(centerLat) * ms - canvas.getHeight() / 2;

        AircraftState best = null;
        double bestDist = 14 * 14; // within ~14 px
        for (AircraftState a : visible()) {
            double px = WebMercator.xNorm(a.longitude()) * ms - originX;
            double py = WebMercator.yNorm(a.latitude()) * ms - originY;
            double dx = px - sx;
            double dy = py - sy;
            double d = dx * dx + dy * dy;
            if (d < bestDist) {
                bestDist = d;
                best = a;
            }
        }
        selected = best;
        onSelect.accept(best);
        redraw();
    }

    // ---- Filtering (altitude level-of-detail + cap) ------------------------

    private int minAltitudeForZoom(int z) {
        if (z <= 4) return 10000;
        if (z == 5) return 7500;
        if (z == 6) return 5000;
        if (z == 7) return 3000;
        if (z == 8) return 1500;
        return Integer.MIN_VALUE;
    }

    private static double altOf(AircraftState a) {
        return a.baroAltitude() == null ? Double.NEGATIVE_INFINITY : a.baroAltitude();
    }

    private List<AircraftState> visible() {
        int minAlt = minAltitudeForZoom(zoom);
        List<AircraftState> out = new ArrayList<>();
        for (AircraftState a : aircraft) {
            if (a.latitude() == null || a.longitude() == null) {
                continue;
            }
            if (altOf(a) < minAlt) {
                continue;
            }
            out.add(a);
        }
        if (out.size() > MAX_PLANES) {
            out.sort((x, y) -> Double.compare(altOf(y), altOf(x)));
            out = out.subList(0, MAX_PLANES);
        }
        return out;
    }

    // ---- Rendering ---------------------------------------------------------

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(SEA);
        gc.fillRect(0, 0, w, h);

        double ms = mapSize();
        int n = 1 << zoom;
        double originX = WebMercator.xNorm(centerLon) * ms - w / 2;
        double originY = WebMercator.yNorm(centerLat) * ms - h / 2;

        // Tiles overlapping the viewport.
        int xStart = (int) Math.floor(originX / TILE);
        int xEnd = (int) Math.floor((originX + w) / TILE);
        int yStart = (int) Math.floor(originY / TILE);
        int yEnd = (int) Math.floor((originY + h) / TILE);
        for (int ty = yStart; ty <= yEnd; ty++) {
            if (ty < 0 || ty >= n) {
                continue; // no vertical wrap
            }
            for (int tx = xStart; tx <= xEnd; tx++) {
                int wx = ((tx % n) + n) % n; // wrap horizontally
                Image img = tiles.get(zoom, wx, ty);
                if (img != null) {
                    gc.drawImage(img, tx * TILE - originX, ty * TILE - originY);
                }
            }
        }

        // Aircraft.
        for (AircraftState a : visible()) {
            double px = WebMercator.xNorm(a.longitude()) * ms - originX;
            double py = WebMercator.yNorm(a.latitude()) * ms - originY;
            if (px < -20 || py < -20 || px > w + 20 || py > h + 20) {
                continue;
            }
            drawPlane(gc, px, py, a.trueTrack(), a.onGround(), a == selected);
        }

        // OSM attribution (required by the tile usage policy).
        gc.setFill(Color.rgb(0, 0, 0, 0.6));
        gc.fillText("© OpenStreetMap contributors", 6, h - 6);
    }

    private void drawPlane(GraphicsContext gc, double x, double y, Double heading,
                           boolean onGround, boolean isSelected) {
        if (isSelected) {
            gc.setStroke(SELECT);
            gc.setLineWidth(2);
            gc.strokeOval(x - 11, y - 11, 22, 22);
        }
        gc.save();
        gc.translate(x, y);
        gc.rotate(heading == null ? 0 : heading);
        gc.beginPath();
        gc.moveTo(PLANE[0][0], PLANE[0][1]);
        for (int i = 1; i < PLANE.length; i++) {
            gc.lineTo(PLANE[i][0], PLANE[i][1]);
        }
        gc.closePath();
        gc.setFill(onGround ? GREY : GOLD);
        gc.fill();
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.7);
        gc.stroke();
        gc.restore();
    }
}
