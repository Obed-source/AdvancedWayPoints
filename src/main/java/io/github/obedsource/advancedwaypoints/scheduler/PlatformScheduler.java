package io.github.obedsource.advancedwaypoints.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduler abstraction so the same code runs on Spigot/Paper (one main thread)
 * and on Folia (region threads).
 */
public interface PlatformScheduler {

    boolean isFolia();

    void runAsync(Runnable task);

    void runGlobal(Runnable task);

    void runGlobalLater(Runnable task, long delayTicks);

    /** Runs on the thread that owns the entity (main thread outside Folia). */
    void runAtEntity(Entity entity, Runnable task);

    void runAtEntityLater(Entity entity, Runnable task, long delayTicks);

    TaskHandle runAtEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks);

    void runAtLocation(Location location, Runnable task);

    /** Teleports an entity using the safe method of the platform (teleportAsync on Folia). */
    CompletableFuture<Boolean> teleport(Entity entity, Location location);

    /** Cancels every task owned by the plugin. */
    void cancelAll();

    static PlatformScheduler create(org.bukkit.plugin.Plugin plugin) {
        if (FoliaScheduler.isSupported()) {
            return new FoliaScheduler(plugin);
        }
        return new BukkitPlatformScheduler(plugin);
    }
}
