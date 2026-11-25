package io.github.obedsource.advancedwaypoints.session;

import io.github.obedsource.advancedwaypoints.api.Waypoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state of one player: their waypoints, which ones are visible and their view mode.
 * Thread safe. Online players keep one instance until they leave; offline players (admin actions)
 * get a short lived instance loaded from the database.
 */
public final class OwnerData {

    private final UUID owner;
    private volatile String name;
    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();
    private final List<String> visible = new ArrayList<>();
    private volatile boolean accumulate;

    public OwnerData(UUID owner, String name, Collection<Waypoint> list, List<String> visibleCodes, boolean accumulate) {
        this.owner = owner;
        this.name = name;
        for (Waypoint waypoint : list) {
            waypoints.put(waypoint.getCode(), waypoint);
        }
        for (String code : visibleCodes) {
            if (waypoints.containsKey(code) && !visible.contains(code)) {
                visible.add(code);
            }
        }
        this.accumulate = accumulate;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    // ---- waypoints ----

    public Optional<Waypoint> get(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(waypoints.get(code));
    }

    public boolean hasCode(String code) {
        return waypoints.containsKey(code);
    }

    public int count() {
        return waypoints.size();
    }

    /** Waypoints that count toward the limit (death points only when configured so). */
    public int countForLimit(boolean includeDeath) {
        if (includeDeath) {
            return waypoints.size();
        }
        int count = 0;
        for (Waypoint waypoint : waypoints.values()) {
            if (!waypoint.isDeath()) {
                count++;
            }
        }
        return count;
    }

    public Collection<Waypoint> all() {
        return Collections.unmodifiableCollection(waypoints.values());
    }

    public void put(Waypoint waypoint) {
        waypoints.put(waypoint.getCode(), waypoint);
    }

    public Waypoint remove(String code) {
        synchronized (this) {
            visible.remove(code);
        }
        return waypoints.remove(code);
    }

    public void clear() {
        synchronized (this) {
            visible.clear();
        }
        waypoints.clear();
    }

    /** Death points first, then favorites (in the order they were marked), then by creation date. */
    public List<Waypoint> sorted(boolean newestFirst) {
        List<Waypoint> list = new ArrayList<>(waypoints.values());
        Comparator<Waypoint> byDate = Comparator.comparingLong(Waypoint::getCreatedAt);
        if (newestFirst) {
            byDate = byDate.reversed();
        }
        Comparator<Waypoint> finalByDate = byDate;
        list.sort((a, b) -> {
            // Death points always come first, newest first; then favorites; then the rest by date.
            if (a.isDeath() != b.isDeath()) {
                return a.isDeath() ? -1 : 1;
            }
            if (a.isDeath()) {
                return Long.compare(b.getCreatedAt(), a.getCreatedAt());
            }
            if (a.isFavorite() != b.isFavorite()) {
                return a.isFavorite() ? -1 : 1;
            }
            if (a.isFavorite()) {
                return Long.compare(a.getFavoriteAt(), b.getFavoriteAt());
            }
            return finalByDate.compare(a, b);
        });
        return list;
    }

    // ---- visibility ----

    public synchronized List<String> visible() {
        return new ArrayList<>(visible);
    }

    public synchronized boolean isVisible(String code) {
        return visible.contains(code);
    }

    public synchronized boolean hasVisible() {
        return !visible.isEmpty();
    }

    public synchronized void addVisible(String code) {
        if (!visible.contains(code)) {
            visible.add(code);
        }
    }

    public synchronized boolean removeVisible(String code) {
        return visible.remove(code);
    }

    public synchronized void setOnlyVisible(String code) {
        visible.clear();
        visible.add(code);
    }

    public synchronized void clearVisible() {
        visible.clear();
    }

    /** The last waypoint that was made visible. */
    public synchronized Optional<Waypoint> lastSelected() {
        for (int i = visible.size() - 1; i >= 0; i--) {
            Waypoint waypoint = waypoints.get(visible.get(i));
            if (waypoint != null) {
                return Optional.of(waypoint);
            }
        }
        return Optional.empty();
    }

    // ---- settings ----

    public boolean accumulate() {
        return accumulate;
    }

    public void accumulate(boolean accumulate) {
        this.accumulate = accumulate;
    }
}
