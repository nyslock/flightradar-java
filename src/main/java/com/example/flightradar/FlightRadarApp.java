package com.example.flightradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Entry point: a JavaFX desktop window embedding a Leaflet/OpenStreetMap map (via {@link WebView}),
 * fed by live OpenSky aircraft data.
 *
 * <p>Data flow: {@link AircraftPoller} fetches states off-thread, then we marshal back onto the
 * JavaFX Application Thread and call the page's {@code updateAircraft(list)} JS function.
 */
public class FlightRadarApp extends Application {

    private static final long POLL_PERIOD_SECONDS = 12;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObjectMapper mapper = new ObjectMapper();
    private AircraftPoller poller;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        Scene scene = new Scene(webView, 1100, 750);
        primaryStage.setTitle("Flight Radar — starting…");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start polling only once the map page has finished loading and updateAircraft() exists.
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                startPolling(engine);
            } else if (newState == Worker.State.FAILED) {
                primaryStage.setTitle("Flight Radar — failed to load map");
            }
        });

        var mapUrl = getClass().getResource("/map.html");
        if (mapUrl == null) {
            primaryStage.setTitle("Flight Radar — map.html not found on classpath");
            return;
        }
        engine.load(mapUrl.toExternalForm());
    }

    private void startPolling(WebEngine engine) {
        OpenSkyClient client = new OpenSkyClient(OpenSkyClient.EUROPE);
        poller = new AircraftPoller(client, POLL_PERIOD_SECONDS, states ->
                Platform.runLater(() -> pushToMap(engine, states)));
        poller.start();
    }

    private void pushToMap(WebEngine engine, List<AircraftState> states) {
        try {
            String json = mapper.writeValueAsString(states);
            engine.executeScript("updateAircraft(" + json + ")");
            stage.setTitle("Flight Radar — " + states.size() + " aircraft · updated "
                    + LocalTime.now().format(TIME_FMT));
        } catch (Exception e) {
            System.err.println("[App] failed to push update to map: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (poller != null) {
            poller.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
