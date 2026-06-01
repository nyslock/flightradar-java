package com.example.flightradar;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Periodically polls an {@link OpenSkyClient} on a background thread and delivers each batch of
 * aircraft states to a callback.
 *
 * <p>The callback runs on the poller's own (non-UI) thread. Callers that touch JavaFX must marshal
 * back onto the JavaFX Application Thread themselves (e.g. via {@code Platform.runLater}).
 */
public final class AircraftPoller {

    private final OpenSkyClient client;
    private final long periodSeconds;
    private final Consumer<List<AircraftState>> onUpdate;

    private ScheduledExecutorService scheduler;

    public AircraftPoller(OpenSkyClient client, long periodSeconds,
                          Consumer<List<AircraftState>> onUpdate) {
        this.client = client;
        this.periodSeconds = periodSeconds;
        this.onUpdate = onUpdate;
    }

    /** Starts polling immediately, then every {@code periodSeconds}. Idempotent-safe per instance. */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "aircraft-poller");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::poll, 0, periodSeconds, TimeUnit.SECONDS);
    }

    private void poll() {
        try {
            List<AircraftState> states = client.fetchStates();
            onUpdate.accept(states);
        } catch (Exception e) {
            // Never let an exception kill the scheduled task.
            System.err.println("[Poller] update failed: " + e.getMessage());
        }
    }

    /** Stops polling and releases the background thread. */
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
