package com.example.flightradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches live aircraft states from the OpenSky Network REST API ({@code /states/all}).
 *
 * <p>Used anonymously (no account / OAuth), which is the simplest path but is subject to stricter
 * rate limits. Any failure (HTTP 429/5xx, timeout, parse error) yields an empty list rather than an
 * exception, so the UI keeps running and simply skips that update.
 */
public final class OpenSkyClient {

    /** Bounding box (lamin, lomin, lamax, lomax) in degrees, used to scope and shrink the query. */
    public record BoundingBox(double laMin, double loMin, double laMax, double loMax) {}

    /** Roughly continental Europe — a sensible, traffic-dense default. */
    public static final BoundingBox EUROPE = new BoundingBox(35.0, -12.0, 60.0, 30.0);

    /** The whole planet. Heavier payload and higher rate-limit cost; use with care. */
    public static final BoundingBox WORLD = new BoundingBox(-90.0, -180.0, 90.0, 180.0);

    private static final String BASE_URL = "https://opensky-network.org/api/states/all";
    private static final String USER_AGENT = "flightradar-java/1.0 (personal demo)";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BoundingBox boundingBox;

    public OpenSkyClient(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetches the current aircraft states within the configured bounding box.
     *
     * @return the live states, or an empty list on any error (never {@code null})
     */
    public List<AircraftState> fetchStates() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildUrl()))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[OpenSky] HTTP " + response.statusCode()
                        + " — skipping this update.");
                return Collections.emptyList();
            }

            return parseStates(response.body());
        } catch (Exception e) {
            System.err.println("[OpenSky] request failed: " + e.getMessage()
                    + " — skipping this update.");
            return Collections.emptyList();
        }
    }

    private String buildUrl() {
        return BASE_URL
                + "?lamin=" + boundingBox.laMin()
                + "&lomin=" + boundingBox.loMin()
                + "&lamax=" + boundingBox.laMax()
                + "&lomax=" + boundingBox.loMax();
    }

    /**
     * Parses an OpenSky {@code /states/all} response body into aircraft states. Rows without a
     * usable position are skipped. Package-visible and static so it can be unit tested without HTTP.
     *
     * @param json the raw response body
     * @return the parsed states (never {@code null})
     */
    List<AircraftState> parseStates(String json) {
        List<AircraftState> result = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode states = root.get("states");
            if (states == null || !states.isArray()) {
                return result;
            }
            for (JsonNode row : states) {
                AircraftState state = AircraftState.fromStateArray(row);
                if (state != null) {
                    result.add(state);
                }
            }
        } catch (Exception e) {
            System.err.println("[OpenSky] failed to parse response: " + e.getMessage());
        }
        return result;
    }
}
