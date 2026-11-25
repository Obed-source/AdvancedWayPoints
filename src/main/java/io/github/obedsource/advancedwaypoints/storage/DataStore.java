package io.github.obedsource.advancedwaypoints.storage;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.session.OwnerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous facade over the repositories. Every call is queued on the storage thread and returns
 * immediately, so the server thread never blocks. Writes keep their order because the queue has one thread.
 */
public final class DataStore {

    @FunctionalInterface
    private interface StorageCall<T> {
        T call() throws StorageException;
    }

    private final WaypointRepository waypoints;
    private final PlayerRepository players;
    private final ExecutorService executor;
    private final Logger logger;

    public DataStore(WaypointRepository waypoints, PlayerRepository players, ExecutorService executor, Logger logger) {
        this.waypoints = waypoints;
        this.players = players;
        this.executor = executor;
        this.logger = logger;
    }

    private <T> CompletableFuture<T> submit(StorageCall<T> call) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call.call();
            } catch (StorageException e) {
                logger.log(Level.SEVERE, e.getMessage(), e.getCause());
                throw new CompletionException(e);
            }
        }, executor);
    }

    private void write(StorageCall<Void> call) {
        submit(call);
    }

    /** Loads everything a player owns. */
    public CompletableFuture<OwnerData> loadOwner(UUID owner, String knownName) {
        return submit(() -> {
            List<Waypoint> list = new ArrayList<>(waypoints.findByOwner(owner));
            // Expired waypoints (death points) are removed while loading.
            long now = System.currentTimeMillis();
            for (Waypoint waypoint : List.copyOf(list)) {
                if (waypoint.isExpired(now)) {
                    waypoints.delete(owner, waypoint.getCode());
                    list.remove(waypoint);
                }
            }
            List<String> visible = players.findVisible(owner);
            boolean accumulate = players.findAccumulate(owner).orElse(false);
            return new OwnerData(owner, knownName, list, visible, accumulate);
        });
    }

    public CompletableFuture<Optional<UUID>> findByName(String name) {
        return submit(() -> players.findByName(name));
    }

    public CompletableFuture<Waypoint> insert(Waypoint waypoint) {
        return submit(() -> {
            waypoints.insert(waypoint);
            return waypoint;
        });
    }

    public void update(Waypoint waypoint) {
        Waypoint snapshot = waypoint.copy();
        write(() -> {
            waypoints.update(snapshot);
            return null;
        });
    }

    public void delete(UUID owner, String code) {
        write(() -> {
            waypoints.delete(owner, code);
            return null;
        });
    }

    public CompletableFuture<Void> deleteAll(UUID owner) {
        return submit(() -> {
            waypoints.deleteAll(owner);
            return null;
        });
    }

    public void saveProfile(UUID owner, String name, boolean accumulate) {
        write(() -> {
            players.save(owner, name, accumulate);
            return null;
        });
    }

    public void saveVisible(UUID owner, List<String> codes) {
        List<String> snapshot = new ArrayList<>(codes);
        write(() -> {
            players.replaceVisible(owner, snapshot);
            return null;
        });
    }
}
