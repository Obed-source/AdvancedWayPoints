package io.github.obedsource.advancedwaypoints.session;

import io.github.obedsource.advancedwaypoints.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the data of online players in memory: loaded while they log in (off the server thread),
 * dropped when they leave. Data of offline players is loaded on demand and not cached.
 */
public final class SessionManager {

    private final DataStore store;
    private final Logger logger;
    private final Map<UUID, OwnerData> online = new ConcurrentHashMap<>();
    private final Map<UUID, OwnerData> preloaded = new ConcurrentHashMap<>();

    public SessionManager(DataStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
    }

    /** Called from AsyncPlayerPreLoginEvent (already a background thread). */
    public void preload(UUID uuid, String name) {
        try {
            OwnerData data = store.loadOwner(uuid, name).get(10, TimeUnit.SECONDS);
            preloaded.put(uuid, data);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not preload waypoints of " + name + ", they will load after join", e);
        }
    }

    /** Called on join. Uses the preloaded data, or loads it now. */
    public CompletableFuture<OwnerData> attach(Player player) {
        UUID uuid = player.getUniqueId();
        OwnerData data = preloaded.remove(uuid);
        CompletableFuture<OwnerData> future = data != null
                ? CompletableFuture.completedFuture(data)
                : store.loadOwner(uuid, player.getName());
        return future.thenApply(loaded -> {
            loaded.name(player.getName());
            if (!player.isOnline()) {
                return loaded; // left while loading: nothing to keep in memory
            }
            online.put(uuid, loaded);
            store.saveProfile(uuid, player.getName(), loaded.accumulate());
            return loaded;
        });
    }

    public void detach(UUID uuid) {
        online.remove(uuid);
        preloaded.remove(uuid);
    }

    public OwnerData getOnline(UUID uuid) {
        return online.get(uuid);
    }

    public Collection<OwnerData> onlineData() {
        return online.values();
    }

    /** Online data when loaded, otherwise a fresh copy from the database. */
    public CompletableFuture<OwnerData> getOrLoad(UUID uuid, String knownName) {
        OwnerData data = online.get(uuid);
        if (data != null) {
            return CompletableFuture.completedFuture(data);
        }
        return store.loadOwner(uuid, knownName);
    }

    /**
     * Resolves a player by name: online players first, then the plugin's own name records,
     * then the server's player cache. Completes with empty when the player never joined.
     */
    public CompletableFuture<Optional<OwnerData>> getOrLoadByName(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            OwnerData data = online.get(player.getUniqueId());
            if (data != null) {
                return CompletableFuture.completedFuture(Optional.of(data));
            }
            return store.loadOwner(player.getUniqueId(), player.getName()).thenApply(Optional::of);
        }
        return store.findByName(name).thenCompose(found -> {
            if (found.isPresent()) {
                return getOrLoad(found.get(), name).thenApply(Optional::of);
            }
            return CompletableFuture.supplyAsync(() -> lookupOffline(name))
                    .thenCompose(uuid -> uuid == null
                            ? CompletableFuture.completedFuture(Optional.<OwnerData>empty())
                            : getOrLoad(uuid, name).thenApply(Optional::of));
        });
    }

    @SuppressWarnings("deprecation")
    private static UUID lookupOffline(String name) {
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline.getUniqueId();
            }
        }
        return null;
    }
}
