package com.example.flightradar;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Entry point: a pure-JavaFX desktop flight radar. The map is drawn natively on a {@link MapCanvas}
 * (OpenStreetMap tiles + aircraft) — no WebView, HTML, or JavaScript. A side panel shows details of
 * the selected aircraft.
 *
 * <p>{@link AircraftPoller} fetches live states off-thread; the callback marshals onto the JavaFX
 * Application Thread to hand the data to the map and update the window title.
 */
public class FlightRadarApp extends Application {

    private static final long POLL_PERIOD_SECONDS = 12;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private AircraftPoller poller;
    private MapCanvas map;
    private Stage stage;

    // Info-panel labels.
    private Label hint;
    private Label callsign;
    private Label country;
    private Label altitude;
    private Label speed;
    private Label heading;
    private Label ground;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        map = new MapCanvas();
        map.setOnAircraftSelected(this::showAircraft);

        BorderPane root = new BorderPane();
        root.setCenter(map);
        root.setRight(buildInfoPanel());

        Scene scene = new Scene(root, 1200, 780);
        primaryStage.setTitle("Flight Radar — starting…");
        primaryStage.setScene(scene);
        primaryStage.show();

        startPolling();
    }

    private VBox buildInfoPanel() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(14));
        box.setPrefWidth(240);
        box.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #dddddd; -fx-border-width: 0 0 0 1;");

        Label title = new Label("Aircraft");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        hint = new Label();
        hint.setWrapText(true);
        callsign = new Label();
        country = new Label();
        altitude = new Label();
        speed = new Label();
        heading = new Label();
        ground = new Label();

        box.getChildren().addAll(title, hint, callsign, country, altitude, speed, heading, ground);
        clearAircraft();
        return box;
    }

    private void showAircraft(AircraftState a) {
        if (a == null) {
            clearAircraft();
            return;
        }
        hint.setText("");
        callsign.setText("Callsign: " + (isBlank(a.callsign()) ? "—" : a.callsign()));
        country.setText("Country: " + (a.originCountry() == null ? "—" : a.originCountry()));
        altitude.setText("Altitude: " + fmt(a.baroAltitude(), " m"));
        speed.setText("Speed: " + fmt(a.velocity(), " m/s"));
        heading.setText("Heading: " + fmt(a.trueTrack(), "°"));
        ground.setText(a.onGround() ? "On ground" : "Airborne");
    }

    private void clearAircraft() {
        hint.setText("Click a plane to see details.\nDrag to pan · scroll to zoom.");
        callsign.setText("");
        country.setText("");
        altitude.setText("");
        speed.setText("");
        heading.setText("");
        ground.setText("");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    private static String fmt(Double value, String suffix) {
        return value == null ? "—" : (Math.round(value) + suffix);
    }

    private void startPolling() {
        OpenSkyClient client = new OpenSkyClient(OpenSkyClient.EUROPE);
        poller = new AircraftPoller(client, POLL_PERIOD_SECONDS, states ->
                Platform.runLater(() -> onStates(states)));
        poller.start();
    }

    private void onStates(List<AircraftState> states) {
        map.setAircraft(states);
        stage.setTitle("Flight Radar — " + states.size() + " aircraft · updated "
                + LocalTime.now().format(TIME_FMT));
    }

    @Override
    public void stop() {
        if (poller != null) {
            poller.stop();
        }
        if (map != null) {
            map.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
