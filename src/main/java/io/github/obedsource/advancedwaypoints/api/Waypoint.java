package io.github.obedsource.advancedwaypoints.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * A personal waypoint. Its position never changes after creation.
 * <p>
 * Instances returned by {@link AdvancedWaypointsAPI} are copies: change waypoints through the API,
 * not through the setters, which exist for the plugin's own services.
 */
public final class Waypoint {

    private long id;
    private final UUID owner;
    private final String code;
    private String name;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private String textColor;
    private String icon;
    private String iconColor;
    private int backgroundArgb;
    private boolean favorite;
    private long favoriteAt;
    private final long createdAt;

    public Waypoint(long id, UUID owner, String code, String name, String world, double x, double y, double z,
                    String textColor, String icon, String iconColor, int backgroundArgb,
                    boolean favorite, long favoriteAt, long createdAt) {
        this.id = id;
        this.owner = Objects.requireNonNull(owner, "owner");
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.world = Objects.requireNonNull(world, "world");
        this.x = x;
        this.y = y;
        this.z = z;
        this.textColor = textColor;
        this.icon = icon;
        this.iconColor = iconColor;
        this.backgroundArgb = backgroundArgb;
        this.favorite = favorite;
        this.favoriteAt = favoriteAt;
        this.createdAt = createdAt;
    }

    public Waypoint copy() {
        return new Waypoint(id, owner, code, name, world, x, y, z, textColor, icon, iconColor, backgroundArgb,
                favorite, favoriteAt, createdAt);
    }

    /** Database row id (internal). */
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getOwner() {
        return owner;
    }

    /** The 4 character identifier, unique per owner (A-Z, a-z, 0-9). */
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getBlockX() {
        return (int) Math.floor(x);
    }

    public int getBlockY() {
        return (int) Math.floor(y);
    }

    public int getBlockZ() {
        return (int) Math.floor(z);
    }

    /** Legacy color code(s) for the text, for example "&amp;f" or "&amp;#FFAA00". */
    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        this.textColor = textColor;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getIconColor() {
        return iconColor;
    }

    public void setIconColor(String iconColor) {
        this.iconColor = iconColor;
    }

    /** Background color as ARGB. */
    public int getBackgroundArgb() {
        return backgroundArgb;
    }

    public void setBackgroundArgb(int backgroundArgb) {
        this.backgroundArgb = backgroundArgb;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
        this.favoriteAt = favorite ? System.currentTimeMillis() : 0L;
    }

    /** When the waypoint was marked as favorite (epoch millis), or 0. */
    public long getFavoriteAt() {
        return favoriteAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** The location, or null when the world is not loaded. */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    @Override
    public String toString() {
        return "Waypoint{" + owner + "/" + code + " '" + name + "' " + world + " " + getBlockX() + "," + getBlockY() + "," + getBlockZ() + "}";
    }
}
