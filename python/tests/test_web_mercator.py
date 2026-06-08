"""Projection unit tests -- round-trips lat/lon and checks known anchor values."""

import unittest

from flightradar import web_mercator as wm


class WebMercatorTest(unittest.TestCase):
    def test_longitude_anchors(self):
        self.assertAlmostEqual(wm.x_norm(-180.0), 0.0, places=9)
        self.assertAlmostEqual(wm.x_norm(0.0), 0.5, places=9)
        self.assertAlmostEqual(wm.x_norm(180.0), 1.0, places=9)

    def test_equator_is_mid_y(self):
        self.assertAlmostEqual(wm.y_norm(0.0), 0.5, places=9)

    def test_north_is_smaller_y_than_south(self):
        # y grows downward: a northern latitude maps above (smaller y) a southern one.
        self.assertLess(wm.y_norm(60.0), wm.y_norm(-60.0))

    def test_lon_round_trip(self):
        for lon in (-179.0, -90.0, 0.0, 12.34, 150.0):
            self.assertAlmostEqual(wm.lon(wm.x_norm(lon)), lon, places=9)

    def test_lat_round_trip(self):
        for lat in (-80.0, -45.0, 0.0, 33.7, 70.0):
            self.assertAlmostEqual(wm.lat(wm.y_norm(lat)), lat, places=6)

    def test_clamp_lat(self):
        self.assertEqual(wm.clamp_lat(90.0), wm.MAX_LATITUDE)
        self.assertEqual(wm.clamp_lat(-90.0), -wm.MAX_LATITUDE)
        self.assertEqual(wm.clamp_lat(10.0), 10.0)


if __name__ == "__main__":
    unittest.main()
