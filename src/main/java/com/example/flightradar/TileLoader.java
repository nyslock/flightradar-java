package com.example.flightradar;

import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;

/**
 * Loads OpenStreetMap raster tiles over HTTP and decodes them into JavaFX {@link Image}s, with an
 * in-memory LRU cache.
 *
 * <p>Tiles are fetched on a small background thread pool (never on the JavaFX thread). Each request
 * sends a descriptive {@code User-Agent} as required by the OSM tile usage policy — default Java
 * user agents are blocked. {@link #get} returns a cached tile immediately or {@code null} while a
 * fetch is in flight; when a fetch completes, the supplied repaint callback fires on the JavaFX
 * thread so the map can redraw with the newly available tile.
 */
public final class TileLoader {

    private static final String URL_TEMPLATE = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "flightradar-java/1.0 (personal desktop app)";

    /** Max decoded tiles kept in memory (~256 KB each → a few tens of MB). */
    private static final int MAX_CACHE = 256;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ExecutorService pool = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "tile-loader");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Long, Image> cache = Collections.synchronizedMap(
            new LinkedHashMap<Long, Image>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Image> eldest) {
                    return size() > MAX_CACHE;
                }
            });

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final Runnable onTileLoaded;

    public TileLoader(Runnable onTileLoaded) {
        this.onTileLoaded = onTileLoaded;
    }

    /**
     * Returns the tile image if cached, otherwise {@code null} and kicks off an async fetch (the
     * repaint callback will fire once it arrives).
     */
    public Image get(int z, int x, int y) {
        long key = key(z, x, y);
        Image cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (inFlight.add(key)) {
            pool.submit(() -> fetch(z, x, y, key));
        }
        return null;
    }

    private void fetch(int z, int x, int y, long key) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(URL_TEMPLATE, z, x, y)))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<byte[]> response =
                    http.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                Image img = new Image(new ByteArrayInputStream(response.body()));
                if (!img.isError()) {
                    cache.put(key, img);
                    Platform.runLater(onTileLoaded);
                }
            }
        } catch (Exception e) {
            // Tile stays blank; it may be retried on a later pan. Don't spam the log.
        } finally {
            inFlight.remove(key);
        }
    }

    private static long key(int z, int x, int y) {
        return ((long) z << 40) | ((long) x << 20) | (long) y;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
