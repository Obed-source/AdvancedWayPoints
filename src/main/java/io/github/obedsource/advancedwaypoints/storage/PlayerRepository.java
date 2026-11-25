package io.github.obedsource.advancedwaypoints.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of per player settings and of which waypoints are visible. */
public interface PlayerRepository {

    /** Stored view mode, empty when the player has no row yet. */
    Optional<Boolean> findAccumulate(UUID uuid) throws StorageException;

    /** Creates or updates the player's row (last known name and view mode). */
    void save(UUID uuid, String name, boolean accumulate) throws StorageException;

    /** Finds a player by last known name, case insensitive. */
    Optional<UUID> findByName(String name) throws StorageException;

    /** Visible waypoint codes, in selection order. */
    List<String> findVisible(UUID uuid) throws StorageException;

    /** Replaces the visible waypoint codes. */
    void replaceVisible(UUID uuid, List<String> codes) throws StorageException;
}
