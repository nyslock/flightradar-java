"""Parser unit tests -- feeds a captured /states/all response to the parser and asserts the field
mapping (and that rows without a position are skipped)."""

import json
import unittest

from flightradar.opensky_client import OpenSkyClient


class OpenSkyParseTest(unittest.TestCase):
    def test_maps_fields_and_skips_positionless_rows(self):
        body = json.dumps({
            "time": 1700000000,
            "states": [
                # icao, callsign, country, time_pos, last_contact, lon, lat, baro_alt,
                # on_ground, velocity, true_track, vertical_rate, sensors, geo_alt
                ["abc123", "DLH123  ", "Germany", 0, 0, 8.5, 50.1, 11000.0,
                 False, 250.0, 90.0, 0.0, None, 11200.0],
                # No position (lon/lat null) -> must be skipped.
                ["def456", "AFR45   ", "France", 0, 0, None, None, None,
                 True, None, None, None, None, None],
            ],
        })

        states = OpenSkyClient.parse_states(body)

        self.assertEqual(len(states), 1)
        a = states[0]
        self.assertEqual(a.icao24, "abc123")
        self.assertEqual(a.callsign, "DLH123")  # trimmed
        self.assertEqual(a.origin_country, "Germany")
        self.assertAlmostEqual(a.longitude, 8.5)
        self.assertAlmostEqual(a.latitude, 50.1)
        self.assertAlmostEqual(a.baro_altitude, 11000.0)
        self.assertFalse(a.on_ground)
        self.assertAlmostEqual(a.velocity, 250.0)
        self.assertAlmostEqual(a.true_track, 90.0)

    def test_empty_or_missing_states(self):
        self.assertEqual(OpenSkyClient.parse_states('{"states": null}'), [])
        self.assertEqual(OpenSkyClient.parse_states("{}"), [])
        self.assertEqual(OpenSkyClient.parse_states("not json"), [])


if __name__ == "__main__":
    unittest.main()
