package io.github.obedsource.advancedwaypoints.storage;

import io.github.obedsource.advancedwaypoints.api.Waypoint;

import java.util.List;
import java.util.UUID;

/**
 * Persistence of waypoints. Implementations are synchronous and are only called from the
 * storage thread ({@link DataStore}); business code never sees SQL.
 */
public interface WaypointRepository {

    List<Waypoint> findByOwner(UUID owner) throws StorageException;

    /** Inserts the waypoint and sets its generated id. */
    void insert(Waypoint waypoint) throws StorageException;

    void update(Waypoint waypoint) throws StorageException;

    void delete(UUID owner, String code) throws StorageException;

    void deleteAll(UUID owner) throws StorageException;
}
