package com.example.flightradar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSkyClientTest {

    private final OpenSkyClient client = new OpenSkyClient(OpenSkyClient.EUROPE);

    /** A trimmed but realistically-shaped {@code /states/all} response. */
    private static final String SAMPLE_JSON = """
        {
          "time": 1700000000,
          "states": [
            ["abc123", "DLH123  ", "Germany", 1700000000, 1700000000, 8.5, 50.1, 11000.0,
             false, 250.5, 90.0, 0.0, null, 11200.0, "1000", false, 0],
            ["def456", "RYR9 ", "Ireland", 1700000000, 1700000000, null, null, 9000.0,
             false, 230.0, 180.0, -2.0, null, 9100.0, "2000", false, 0],
            ["ghi789", null, "France", 1700000000, 1700000000, 2.3, 48.8, 0.0,
             true, 0.0, 270.0, 0.0, null, 0.0, "3000", false, 0]
          ]
        }
        """;

    @Test
    void parsesValidRowsAndSkipsRowsWithoutPosition() {
        List<AircraftState> states = client.parseStates(SAMPLE_JSON);

        // The middle row has null lat/lon and must be skipped; 2 of 3 remain.
        assertEquals(2, states.size());

        AircraftState first = states.get(0);
        assertEquals("abc123", first.icao24());
        assertEquals("DLH123", first.callsign(), "callsign should be trimmed");
        assertEquals("Germany", first.originCountry());
        assertEquals(8.5, first.longitude());
        assertEquals(50.1, first.latitude());
        assertEquals(11000.0, first.baroAltitude());
        assertEquals(250.5, first.velocity());
        assertEquals(90.0, first.trueTrack());
        assertFalse(first.onGround());

        AircraftState onGround = states.get(1);
        assertEquals("ghi789", onGround.icao24());
        assertNull(onGround.callsign(), "null callsign should stay null");
        assertTrue(onGround.onGround());
    }

    @Test
    void returnsEmptyListForMissingStatesField() {
        assertTrue(client.parseStates("{\"time\": 1700000000, \"states\": null}").isEmpty());
        assertTrue(client.parseStates("{}").isEmpty());
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        assertTrue(client.parseStates("not json at all").isEmpty());
    }
}
