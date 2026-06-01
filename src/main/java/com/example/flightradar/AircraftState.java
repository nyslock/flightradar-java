package com.example.flightradar;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One aircraft's live state, mapped from a single OpenSky {@code /states/all} row.
 *
 * <p>OpenSky returns each state vector as a positional JSON array (not an object). The relevant
 * indices are:
 * <pre>
 *  0 icao24 | 1 callsign | 2 origin_country | 5 longitude | 6 latitude | 7 baro_altitude |
 *  8 on_ground | 9 velocity(m/s) | 10 true_track(heading deg) | 11 vertical_rate | 13 geo_altitude
 * </pre>
 *
 * <p>This is an immutable value type. Use {@link #fromStateArray(JsonNode)} to build one from a row.
 */
public record AircraftState(
        String icao24,
        String callsign,
        String originCountry,
        Double longitude,
        Double latitude,
        Double baroAltitude,
        boolean onGround,
        Double velocity,
        Double trueTrack,
        Double verticalRate) {

    /**
     * Maps a single OpenSky state-vector array into an {@link AircraftState}.
     *
     * @param row a JSON array node from the {@code states} list
     * @return the mapped state, or {@code null} if the row has no usable position (lat/lon missing)
     */
    public static AircraftState fromStateArray(JsonNode row) {
        if (row == null || !row.isArray() || row.size() < 11) {
            return null;
        }

        Double longitude = asDouble(row.get(5));
        Double latitude = asDouble(row.get(6));
        if (longitude == null || latitude == null) {
            return null; // no position => nothing to plot
        }

        String callsign = asText(row.get(1));
        if (callsign != null) {
            callsign = callsign.trim();
        }

        return new AircraftState(
                asText(row.get(0)),
                callsign,
                asText(row.get(2)),
                longitude,
                latitude,
                asDouble(row.get(7)),
                asBoolean(row.get(8)),
                asDouble(row.get(9)),
                asDouble(row.get(10)),
                asDouble(row.get(11)));
    }

    private static String asText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Double asDouble(JsonNode node) {
        return node == null || node.isNull() ? null : node.asDouble();
    }

    private static boolean asBoolean(JsonNode node) {
        return node != null && !node.isNull() && node.asBoolean();
    }
}
