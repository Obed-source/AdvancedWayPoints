package io.github.obedsource.advancedwaypoints.display;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.scheduler.TaskHandle;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.service.WaypointService;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Draws the visible waypoints of each player with TextDisplay entities that only that player can see.
 * <p>
 * Clients only render entities close to them, so a far waypoint is drawn at {@code max-render-distance}
 * in its direction and scaled up; a near one is drawn at its real position. The label always faces the
 * player (billboard CENTER). Labels of waypoints in another world are removed but stay selected, and come
 * back as soon as the player enters that world. One light task runs per player with visible waypoints,
 * on that player's own thread (region thread on Folia).
 */
public final class DisplayManager implements WaypointService.ViewRefresher {

    private static final class Rendered {
        final TextDisplay entity;
        String text;
        int background;
        float scale;

        Rendered(TextDisplay entity) {
            this.entity = entity;
        }
    }

    private static final class Viewer {
        TaskHandle task;
        final Map<String, Rendered> labels = new HashMap<>();
        /** Feet height used for far labels; ignores jumps so the labels do not bounce. */
        double baseY = Double.NaN;
    }

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final SessionManager sessions;
    private final Supplier<Settings> settings;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final Method setTeleportDuration;
    private final Method spawnWithFunction;
    private final Method spawnWithLegacyConsumer;
    private final Class<?> legacyConsumerType;

    public DisplayManager(Plugin plugin, PlatformScheduler scheduler, SessionManager sessions, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.sessions = sessions;
        this.settings = settings;
        Method method;
        try {
            method = Display.class.getMethod("setTeleportDuration", int.class);
        } catch (NoSuchMethodException e) {
            method = null; // before 1.20.2
        }
        this.setTeleportDuration = method;
        Method modern = null;
        Method legacy = null;
        Class<?> legacyType = null;
        try {
            modern = World.class.getMethod("spawn", Location.class, Class.class, java.util.function.Consumer.class);
        } catch (NoSuchMethodException e) {
            try {
                legacyType = Class.forName("org.bukkit.util.Consumer");
                legacy = World.class.getMethod("spawn", Location.class, Class.class, legacyType);
            } catch (ReflectiveOperationException ignored) {
                legacyType = null;
            }
        }
        this.spawnWithFunction = modern;
        this.spawnWithLegacyConsumer = legacy;
        this.legacyConsumerType = legacyType;
    }

    /** Starts (or stops) rendering for an online owner. Safe from any thread. */
    @Override
    public void refresh(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return;
        }
        scheduler.runAtEntity(player, () -> ensure(player));
    }

    private void ensure(Player player) {
        if (!player.isOnline()) {
            return;
        }
        OwnerData data = sessions.getOnline(player.getUniqueId());
        if (data == null) {
            return;
        }
        Viewer viewer = viewers.computeIfAbsent(player.getUniqueId(), id -> new Viewer());
        if (viewer.task == null && data.hasVisible()) {
            long period = settings.get().updateInterval;
            viewer.task = scheduler.runAtEntityTimer(player, () -> tick(player, viewer), 1L, period);
        }
        tick(player, viewer);
    }

    private void tick(Player player, Viewer viewer) {
        if (!player.isOnline()) {
            stop(player.getUniqueId());
            return;
        }
        OwnerData data = sessions.getOnline(player.getUniqueId());
        List<String> visible = data == null ? List.of() : data.visible();
        if (visible.isEmpty()) {
            stop(player.getUniqueId());
            return;
        }
        Iterator<Map.Entry<String, Rendered>> iterator = viewer.labels.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Rendered> entry = iterator.next();
            if (!visible.contains(entry.getKey())) {
                remove(entry.getValue());
                iterator.remove();
            }
        }

        Settings s = settings.get();
        World world = player.getWorld();
        Location eye = stableEye(player, viewer);
        long now = System.currentTimeMillis();
        for (String code : visible) {
            Waypoint waypoint = data.get(code).orElse(null);
            Rendered rendered = viewer.labels.get(code);
            if (waypoint == null || waypoint.isExpired(now) || !waypoint.getWorld().equals(world.getName())) {
                if (rendered != null) {
                    remove(rendered);
                    viewer.labels.remove(code);
                }
                continue;
            }
            Vector toTarget = new Vector(waypoint.getX() - eye.getX(),
                    waypoint.getY() + s.heightOffset - eye.getY(),
                    waypoint.getZ() - eye.getZ());
            double distance = toTarget.length();
            double horizontal = Math.hypot(waypoint.getX() - eye.getX(), waypoint.getZ() - eye.getZ());
            if (s.hideWithin > 0 && horizontal < s.hideWithin) {
                if (rendered != null) {
                    remove(rendered);
                    viewer.labels.remove(code);
                }
                continue;
            }
            double placed = Math.min(distance, s.maxRenderDistance);
            Location position = distance < 0.001
                    ? eye.clone()
                    : eye.clone().add(toTarget.multiply(placed / distance));
            float scale = s.baseScale;
            if (s.scaleWithDistance) {
                scale = (float) Math.min(s.maxScale, s.baseScale * Math.max(1D, placed / s.scaleReferenceDistance));
            }
            String text = WaypointFormat.label(waypoint, s, WaypointFormat.distance(distance, s));

            if (rendered == null || !rendered.entity.isValid() || rendered.entity.getWorld() != world) {
                if (rendered != null) {
                    remove(rendered);
                }
                rendered = spawn(player, position, s);
                viewer.labels.put(code, rendered);
            } else if (rendered.entity.getLocation().distanceSquared(position) > 0.0004D) {
                scheduler.teleport(rendered.entity, position);
            }
            if (!text.equals(rendered.text)) {
                rendered.entity.setText(text);
                rendered.text = text;
            }
            if (rendered.background != waypoint.getBackgroundArgb()) {
                setBackground(rendered.entity, waypoint.getBackgroundArgb());
                rendered.background = waypoint.getBackgroundArgb();
            }
            if (Math.abs(rendered.scale - scale) > 0.01F) {
                rendered.entity.setInterpolationDelay(0);
                rendered.entity.setInterpolationDuration((int) s.updateInterval);
                rendered.entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
                rendered.scale = scale;
            }
        }
    }

    /**
     * Eye position for placing far labels. A far label sits on the line from the eye to the waypoint, so
     * every jump would move it up and down. The height used here only follows the player while they stand
     * on the ground (smoothed, so stairs glide) or fly/swim/fall far, and is ignored during jumps.
     */
    @SuppressWarnings("deprecation")
    private static Location stableEye(Player player, Viewer viewer) {
        Location feet = player.getLocation();
        double y = feet.getY();
        boolean freeMoving = player.isFlying() || player.isGliding() || player.isSwimming() || player.isInsideVehicle();
        if (Double.isNaN(viewer.baseY) || freeMoving || Math.abs(y - viewer.baseY) > 3D) {
            viewer.baseY = y;
        } else if (player.isOnGround()) {
            viewer.baseY += (y - viewer.baseY) * 0.35D;
            if (Math.abs(y - viewer.baseY) < 0.01D) {
                viewer.baseY = y;
            }
        }
        double eyeHeight = player.getEyeLocation().getY() - y;
        return new Location(feet.getWorld(), feet.getX(), viewer.baseY + eyeHeight, feet.getZ());
    }

    /**
     * Spawns the label with its setup applied before it joins the world, so no other player ever
     * receives it. Uses the spawn-with-function method of the running version (java.util.function on
     * 1.20.2+, org.bukkit.util.Consumer before), falling back to a plain spawn.
     */
    private TextDisplay spawnHidden(World world, Location position) {
        java.util.function.Consumer<TextDisplay> setup = display -> {
            display.setVisibleByDefault(false);
            display.setPersistent(false);
        };
        try {
            if (spawnWithFunction != null) {
                return TextDisplay.class.cast(spawnWithFunction.invoke(world, position, TextDisplay.class, setup));
            }
            if (spawnWithLegacyConsumer != null) {
                Object legacy = java.lang.reflect.Proxy.newProxyInstance(legacyConsumerType.getClassLoader(),
                        new Class<?>[]{legacyConsumerType}, (proxy, method, args) -> {
                            if (method.getName().equals("accept")) {
                                setup.accept((TextDisplay) args[0]);
                                return null;
                            }
                            return method.invoke(setup, args);
                        });
                return TextDisplay.class.cast(spawnWithLegacyConsumer.invoke(world, position, TextDisplay.class, legacy));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().fine("Falling back to a plain spawn: " + e);
        }
        TextDisplay display = world.spawn(position, TextDisplay.class);
        setup.accept(display);
        return display;
    }

    private Rendered spawn(Player player, Location position, Settings s) {
        TextDisplay display = spawnHidden(player.getWorld(), position);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(s.seeThrough);
        display.setShadowed(s.shadowed);
        display.setDefaultBackground(false);
        display.setLineWidth(s.lineWidth);
        display.setTextOpacity((byte) s.textOpacity);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setViewRange(4F);
        if (s.fullBright) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
        if (setTeleportDuration != null) {
            try {
                setTeleportDuration.invoke(display, s.teleportDuration);
            } catch (ReflectiveOperationException ignored) {
                // smooth movement is optional
            }
        }
        player.showEntity(plugin, display);
        Rendered rendered = new Rendered(display);
        rendered.background = Integer.MIN_VALUE + 1;
        rendered.scale = -1F;
        return rendered;
    }

    /** Deprecated in the 1.19.4 API only because displays were experimental then; stable since. */
    @SuppressWarnings("deprecation")
    private static void setBackground(TextDisplay display, int argb) {
        display.setBackgroundColor(Color.fromARGB(argb));
    }

    private void remove(Rendered rendered) {
        if (rendered.entity.isValid()) {
            rendered.entity.remove();
        }
    }

    /** Removes every label of a player and stops their task (quit, or nothing visible). */
    public void stop(UUID uuid) {
        Viewer viewer = viewers.remove(uuid);
        if (viewer == null) {
            return;
        }
        if (viewer.task != null) {
            viewer.task.cancel();
        }
        for (Rendered rendered : viewer.labels.values()) {
            try {
                remove(rendered);
            } catch (RuntimeException ignored) {
                // entity owned by another region thread on Folia; it is not persistent anyway
            }
        }
        viewer.labels.clear();
    }

    /** Restarts every task (after /wp restart, to apply a new update interval). */
    public void restartAll() {
        for (UUID uuid : List.copyOf(viewers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                stop(uuid);
                continue;
            }
            scheduler.runAtEntity(player, () -> {
                stop(uuid);
                ensure(player);
            });
        }
    }

    public void shutdown() {
        for (UUID uuid : List.copyOf(viewers.keySet())) {
            stop(uuid);
        }
    }

    /** Distance from the player to one of their waypoints, when in the same world. */
    public static OptionalDouble distance(Player player, Waypoint waypoint) {
        if (!player.getWorld().getName().equals(waypoint.getWorld())) {
            return OptionalDouble.empty();
        }
        Location location = player.getLocation();
        double dx = waypoint.getX() - location.getX();
        double dy = waypoint.getY() - location.getY();
        double dz = waypoint.getZ() - location.getZ();
        return OptionalDouble.of(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }
}
