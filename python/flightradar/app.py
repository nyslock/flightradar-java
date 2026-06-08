"""Entry point: a pure-Tkinter desktop flight radar. The map is drawn natively on a
:class:`MapCanvas` (OpenStreetMap tiles + aircraft) -- no WebView, HTML, or JavaScript. A side panel
shows details of the selected aircraft.

:class:`AircraftPoller` fetches live states off-thread; the callback marshals onto the Tk main
thread (via ``root.after``) to hand the data to the map and update the window title.
"""

import tkinter as tk
from datetime import datetime
from tkinter import ttk

from .aircraft_poller import AircraftPoller
from .map_canvas import MapCanvas
from .opensky_client import EUROPE, OpenSkyClient

_POLL_PERIOD_SECONDS = 12


class FlightRadarApp:
    def __init__(self, root):
        self.root = root
        root.title("Flight Radar — starting…")
        root.geometry("1200x780")

        self.map = MapCanvas(root, schedule=lambda fn: root.after(0, fn))
        self.map.on_select = self.show_aircraft
        self.map.canvas.pack(side="left", fill="both", expand=True)

        self._build_info_panel()
        self.clear_aircraft()

        self.poller = None
        self.start_polling()

        root.protocol("WM_DELETE_WINDOW", self.on_close)

    def _build_info_panel(self):
        panel = tk.Frame(self.root, width=240, bg="#f4f4f4",
                         highlightbackground="#dddddd", highlightthickness=1)
        panel.pack(side="right", fill="y")
        panel.pack_propagate(False)

        inner = tk.Frame(panel, bg="#f4f4f4")
        inner.pack(fill="both", expand=True, padx=14, pady=14)

        tk.Label(inner, text="Aircraft", bg="#f4f4f4",
                 font=("Segoe UI", 16, "bold")).pack(anchor="w", pady=(0, 8))

        def label():
            lbl = tk.Label(inner, text="", bg="#f4f4f4", justify="left",
                           wraplength=210, anchor="w", font=("Segoe UI", 10))
            lbl.pack(anchor="w", pady=2, fill="x")
            return lbl

        self.hint = label()
        self.callsign = label()
        self.country = label()
        self.altitude = label()
        self.speed = label()
        self.heading = label()
        self.ground = label()

    def show_aircraft(self, a):
        if a is None:
            self.clear_aircraft()
            return
        self.hint.config(text="")
        self.callsign.config(text="Callsign: " + ("—" if _is_blank(a.callsign) else a.callsign))
        self.country.config(text="Country: " + ("—" if a.origin_country is None else a.origin_country))
        self.altitude.config(text="Altitude: " + _fmt(a.baro_altitude, " m"))
        self.speed.config(text="Speed: " + _fmt(a.velocity, " m/s"))
        self.heading.config(text="Heading: " + _fmt(a.true_track, "°"))
        self.ground.config(text="On ground" if a.on_ground else "Airborne")

    def clear_aircraft(self):
        self.hint.config(text="Click a plane to see details.\nDrag to pan · scroll to zoom.")
        for lbl in (self.callsign, self.country, self.altitude, self.speed, self.heading, self.ground):
            lbl.config(text="")

    def start_polling(self):
        client = OpenSkyClient(EUROPE)
        self.poller = AircraftPoller(
            client,
            _POLL_PERIOD_SECONDS,
            lambda states: self.root.after(0, lambda: self.on_states(states)),
        )
        self.poller.start()

    def on_states(self, states):
        self.map.set_aircraft(states)
        now = datetime.now().strftime("%H:%M:%S")
        self.root.title(f"Flight Radar — {len(states)} aircraft · updated {now}")

    def on_close(self):
        if self.poller is not None:
            self.poller.stop()
        self.map.shutdown()
        self.root.destroy()


def _is_blank(s):
    return s is None or s == ""


def _fmt(value, suffix):
    return "—" if value is None else f"{round(value)}{suffix}"


def main():
    root = tk.Tk()
    FlightRadarApp(root)
    root.mainloop()
