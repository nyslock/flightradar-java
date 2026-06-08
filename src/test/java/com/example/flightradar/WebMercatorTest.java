package com.example.flightradar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebMercatorTest {

    @Test
    void latitudeRoundTrips() {
        for (double lat : new double[] {-80, -45, -1.5, 0, 12.34, 48.0, 51.5, 80}) {
            double y = WebMercator.yNorm(lat);
            assertEquals(lat, WebMercator.lat(y), 1e-6, "lat round-trip at " + lat);
        }
    }

    @Test
    void longitudeRoundTrips() {
        for (double lon : new double[] {-179, -90, -0.1, 0, 9.99, 100.5, 179}) {
            double x = WebMercator.xNorm(lon);
            assertEquals(lon, WebMercator.lon(x), 1e-9, "lon round-trip at " + lon);
        }
    }

    @Test
    void knownAnchorValues() {
        // Null Island maps to the center of the unit square.
        assertEquals(0.5, WebMercator.xNorm(0.0), 1e-12);
        assertEquals(0.5, WebMercator.yNorm(0.0), 1e-12);
        // Antimeridian / prime edges.
        assertEquals(0.0, WebMercator.xNorm(-180.0), 1e-12);
        assertEquals(1.0, WebMercator.xNorm(180.0), 1e-12);
    }

    @Test
    void clampsLatitudeToProjectionLimit() {
        assertEquals(WebMercator.MAX_LATITUDE, WebMercator.clampLat(89.0), 1e-9);
        assertEquals(-WebMercator.MAX_LATITUDE, WebMercator.clampLat(-89.0), 1e-9);
        assertEquals(45.0, WebMercator.clampLat(45.0), 1e-9);
    }
}
