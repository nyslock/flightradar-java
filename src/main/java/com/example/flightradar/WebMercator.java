package com.example.flightradar;

/**
 * Web Mercator (EPSG:3857) projection helpers — the same projection used by OpenStreetMap's
 * standard tiles.
 *
 * <p>Coordinates here are <em>normalized</em> to the unit square [0,1): multiply by the map size in
 * pixels ({@code 256 * 2^zoom}) to get world-pixel coordinates. Longitude maps linearly to x;
 * latitude maps to y through the Mercator transform. Both directions are provided so the map view
 * can project aircraft to the screen and convert mouse positions back to lat/lon.
 */
public final class WebMercator {

    /** OSM/Web-Mercator latitude limit (beyond this the projection diverges). */
    public static final double MAX_LATITUDE = 85.05112878;

    private WebMercator() {}

    /** Longitude (deg) -> normalized x in [0,1). */
    public static double xNorm(double lon) {
        return (lon + 180.0) / 360.0;
    }

    /** Latitude (deg) -> normalized y in [0,1) (0 = north edge, 1 = south edge). */
    public static double yNorm(double lat) {
        double r = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0;
    }

    /** Normalized x -> longitude (deg). */
    public static double lon(double xNorm) {
        return xNorm * 360.0 - 180.0;
    }

    /** Normalized y -> latitude (deg). */
    public static double lat(double yNorm) {
        double n = Math.PI * (1.0 - 2.0 * yNorm);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /** Clamps a latitude to the projection's valid range. */
    public static double clampLat(double lat) {
        return Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, lat));
    }
}
