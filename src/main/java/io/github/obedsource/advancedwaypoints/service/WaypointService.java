package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.api.ColorTarget;
import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.api.event.WaypointCreateEvent;
import io.github.obedsource.advancedwaypoints.api.event.WaypointDeleteEvent;
import io.github.obedsource.advancedwaypoints.api.event.WaypointShareEvent;
import io.github.obedsource.advancedwaypoints.api.event.WaypointUpdateEvent;
import io.github.obedsource.advancedwaypoints.api.event.WaypointVisibilityEvent;
import io.github.obedsource.advancedwaypoints.config.ColorPalette;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import io.github.obedsource.advancedwaypoints.storage.DataStore;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import io.github.obedsource.advancedwaypoints.api.WaypointType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Business rules of waypoints. Works on {@link OwnerData} (memory) and queues persistence on the
 * {@link DataStore}. Returns {@link ActionResult}s instead of messaging players, so commands, menus
 * and the API share the same rules.
 */
public final class WaypointService {

    /** Re-renders the labels of an online owner after a change. */
    public interface ViewRefresher {
        void refresh(UUID owner);
    }

    /** Tells an online receiver that a waypoint was shared with them. */
    public interface ShareNotifier {
        void received(Player receiver, Waypoint copy, String senderName);
    }

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Supplier<Settings> settings;
    private final SessionManager sessions;
    private final DataStore store;
    private final LimitService limits;
    private final RestrictionService restrictions;
    private final CostService costs;
    private final NameValidator names;
    private final PlatformScheduler scheduler;
    private final Function<String, String> colorNames;
    private final Supplier<String> deathName;
    private ViewRefresher refresher = owner -> { };
    private ShareNotifier shareNotifier = (receiver, copy, sender) -> { };

    public WaypointService(Supplier<Settings> settings, SessionManager sessions, DataStore store, LimitService limits,
                           RestrictionService restrictions, CostService costs, NameValidator names,
                           PlatformScheduler scheduler, Function<String, String> colorNames, Supplier<String> deathName) {
        this.settings = settings;
        this.sessions = sessions;
        this.store = store;
        this.limits = limits;
        this.restrictions = restrictions;
        this.costs = costs;
        this.names = names;
        this.scheduler = scheduler;
        this.colorNames = colorNames;
        this.deathName = deathName;
    }

    public void setRefresher(ViewRefresher refresher) {
        this.refresher = refresher;
    }

    public void setShareNotifier(ShareNotifier shareNotifier) {
        this.shareNotifier = shareNotifier;
    }

    public LimitService limits() {
        return limits;
    }

    public CostService costs() {
        return costs;
    }

    /** Waypoints of the owner that count toward their limit. */
    public int countForLimit(OwnerData owner) {
        return owner.countForLimit(settings.get().deathCountsTowardLimit);
    }

    private Map<String, String> ph(Waypoint waypoint, Object... extra) {
        Map<String, String> map = WaypointFormat.placeholders(waypoint, settings.get());
        if (extra.length > 0) {
            map = Text.merge(map, Text.ph(extra));
        }
        return map;
    }

    private static boolean call(Event event) {
        Bukkit.getPluginManager().callEvent(event);
        return !(event instanceof org.bukkit.event.Cancellable) || !((org.bukkit.event.Cancellable) event).isCancelled();
    }

    // ------------------------------------------------------------------ lookup

    /** Finds a waypoint by its 4 character code, or by its exact name (ignoring case and colors). */
    public Optional<Waypoint> resolve(OwnerData owner, String input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        Optional<Waypoint> byCode = owner.get(input);
        if (byCode.isPresent()) {
            return byCode;
        }
        Waypoint match = null;
        for (Waypoint waypoint : owner.all()) {
            if (Text.strip(waypoint.getName()).equalsIgnoreCase(input)) {
                if (match != null) {
                    return Optional.empty();
                }
                match = waypoint;
            }
        }
        return Optional.ofNullable(match);
    }

    private ActionResult notFound(String input) {
        return ActionResult.fail("messages.not-found", Text.ph("input", input));
    }

    private String newCode(OwnerData owner) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10_000; attempt++) {
            char[] chars = new char[4];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length()));
            }
            String code = new String(chars);
            if (!owner.hasCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No free waypoint code for " + owner.owner());
    }

    // ------------------------------------------------------------------ create

    /**
     * Creates a waypoint at the location.
     *
     * @param actor        who runs the action (used for permissions, money and limits), may be null for the API
     * @param enforceRules apply limits, world/region rules and prices (false for admins and the API)
     */
    public ActionResult create(Player actor, OwnerData owner, String rawName, Location location, boolean enforceRules) {
        Settings s = settings.get();
        boolean allowFormat = actor == null || actor.hasPermission(Permissions.NAME_FORMAT);
        NameValidator.Result name = names.validate(rawName, allowFormat);
        if (!name.ok()) {
            return ActionResult.fail(name.errorKey(), Text.ph("max", s.nameMaxLength));
        }
        if (location.getWorld() == null) {
            return ActionResult.fail("messages.world-not-allowed");
        }
        boolean pay = false;
        if (enforceRules && actor != null) {
            int limit = limits.limitOf(actor);
            if (countForLimit(owner) >= limit) {
                return ActionResult.fail("messages.limit-reached", Text.ph("limit", LimitService.display(limit)));
            }
            if (!actor.hasPermission(Permissions.BYPASS_RESTRICTIONS)) {
                Optional<String> denied = restrictions.check(location);
                if (denied.isPresent()) {
                    return ActionResult.fail(denied.get(), Text.ph("world", location.getWorld().getName()));
                }
            }
            pay = costs.isCharged(actor, s.createCost);
            if (pay && !costs.canAfford(actor, s.createCost)) {
                return ActionResult.fail("messages.cost.not-enough", costs.placeholders(s.createCost));
            }
        }

        ColorPalette palette = s.palette;
        String icon = s.randomIcon ? s.icons.get(ThreadLocalRandom.current().nextInt(s.icons.size())) : s.defaultIcon;
        String iconColor = s.randomIconColor ? palette.random().legacy() : legacy(s.defaultIconColorKey, "&f");
        String textColor = s.randomTextColor ? palette.random().legacy() : legacy(s.defaultTextColorKey, "&f");
        int background = argb(s.defaultBackgroundKey);

        Waypoint waypoint = new Waypoint(0L, owner.owner(), newCode(owner), name.name(), location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), textColor, icon, iconColor, background,
                false, 0L, System.currentTimeMillis());
        if (!call(new WaypointCreateEvent(waypoint.copy()))) {
            return ActionResult.fail("messages.cancelled");
        }
        // Affordability was checked above in the same tick; a failure means the inventory changed meanwhile.
        if (pay && !costs.charge(actor, s.createCost)) {
            return ActionResult.fail("messages.cost.not-enough", costs.placeholders(s.createCost));
        }
        owner.put(waypoint);
        store.insert(waypoint);
        if (s.showOnCreate) {
            applyShow(owner, waypoint.getCode());
        }
        return ActionResult.ok(pay ? "messages.created-paid" : "messages.created",
                Text.merge(ph(waypoint), costs.placeholders(s.createCost)), SoundService.CREATE, waypoint);
    }

    private String legacy(String paletteKey, String fallback) {
        return settings.get().palette.get(paletteKey).map(ColorPalette.Entry::legacy).orElse(fallback);
    }

    private int argb(String paletteKey) {
        Settings s = settings.get();
        return s.palette.get(paletteKey).map(e -> e.argb(s.backgroundAlpha)).orElse(s.backgroundAlpha << 24);
    }

    // ------------------------------------------------------------------ death waypoints

    /**
     * Creates the death waypoint of a player where they died, removing their oldest death waypoints
     * beyond death-waypoint.keep. Returns null when death waypoints do not apply.
     */
    public ActionResult createDeath(Player player, OwnerData owner, Location location) {
        Settings s = settings.get();
        if (!s.deathEnabled || location.getWorld() == null || !player.hasPermission(Permissions.DEATH)) {
            return null;
        }
        String world = location.getWorld().getName().toLowerCase(Locale.ROOT);
        if ((s.deathWorldMode == Settings.RestrictionMode.WHITELIST && !s.deathWorlds.contains(world))
                || (s.deathWorldMode == Settings.RestrictionMode.BLACKLIST && s.deathWorlds.contains(world))) {
            return null;
        }
        List<Waypoint> previous = new ArrayList<>();
        for (Waypoint waypoint : owner.all()) {
            if (waypoint.isDeath()) {
                previous.add(waypoint);
            }
        }
        previous.sort(Comparator.comparingLong(Waypoint::getCreatedAt));
        while (previous.size() >= s.deathKeep) {
            Waypoint oldest = previous.remove(0);
            owner.remove(oldest.getCode());
            store.delete(owner.owner(), oldest.getCode());
        }
        long now = System.currentTimeMillis();
        long expires = s.deathDurationSeconds > 0 ? now + s.deathDurationSeconds * 1000L : 0L;
        Waypoint waypoint = new Waypoint(0L, owner.owner(), newCode(owner), deathName.get(), location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), legacy(s.deathTextColorKey, "&c"), s.deathIcon,
                legacy(s.deathIconColorKey, "&c"), argb(s.deathBackgroundKey), false, 0L, now, WaypointType.DEATH, expires);
        if (!call(new WaypointCreateEvent(waypoint.copy()))) {
            return null;
        }
        owner.put(waypoint);
        store.insert(waypoint);
        if (s.deathShow) {
            applyShow(owner, waypoint.getCode());
        } else {
            store.saveVisible(owner.owner(), owner.visible());
            refresher.refresh(owner.owner());
        }
        return ActionResult.ok(expires > 0 ? "messages.death-created" : "messages.death-created-permanent",
                ph(waypoint), SoundService.CREATE, waypoint);
    }

    /** Removes the expired waypoints of an owner and returns them. */
    public List<Waypoint> removeExpired(OwnerData owner) {
        long now = System.currentTimeMillis();
        List<Waypoint> removed = new ArrayList<>();
        for (Waypoint waypoint : owner.all()) {
            if (waypoint.isExpired(now)) {
                removed.add(waypoint);
            }
        }
        if (removed.isEmpty()) {
            return removed;
        }
        for (Waypoint waypoint : removed) {
            owner.remove(waypoint.getCode());
            store.delete(owner.owner(), waypoint.getCode());
        }
        store.saveVisible(owner.owner(), owner.visible());
        refresher.refresh(owner.owner());
        return removed;
    }

    // ------------------------------------------------------------------ teleport

    /**
     * Teleports a player to a waypoint of the owner.
     *
     * @param free no price (admins)
     */
    public ActionResult teleport(Player player, OwnerData owner, String input, boolean free) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        Location location = waypoint.toLocation();
        if (location == null) {
            return ActionResult.fail("messages.world-not-loaded", ph(waypoint));
        }
        Settings s = settings.get();
        boolean pay = !free && costs.isCharged(player, s.teleportCost);
        if (pay && !costs.charge(player, s.teleportCost)) {
            return ActionResult.fail("messages.cost.not-enough", costs.placeholders(s.teleportCost));
        }
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        scheduler.teleport(player, location);
        return ActionResult.ok(pay ? "messages.teleported-paid" : "messages.teleported",
                Text.merge(ph(waypoint), costs.placeholders(s.teleportCost)), SoundService.TELEPORT, waypoint);
    }

    // ------------------------------------------------------------------ edit

    public ActionResult rename(CommandSender actor, OwnerData owner, String input, String rawName, boolean bypass) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        boolean allowFormat = bypass || actor == null || actor.hasPermission(Permissions.NAME_FORMAT);
        NameValidator.Result name = names.validate(rawName, allowFormat);
        if (!name.ok()) {
            return ActionResult.fail(name.errorKey(), Text.ph("max", settings.get().nameMaxLength));
        }
        String old = waypoint.getName();
        waypoint.setName(name.name());
        changed(owner, waypoint, WaypointUpdateEvent.Change.NAME);
        return ActionResult.ok("messages.renamed", ph(waypoint, "old", old), SoundService.RENAME, waypoint);
    }

    /**
     * Changes a color. The input is a palette key (e.g. "red") or, with wp.color.&lt;target&gt;.custom,
     * a custom value: "#RRGGBB", "&amp;c" or "&amp;#RRGGBB" (and "#AARRGGBB" for backgrounds).
     */
    public ActionResult setColor(CommandSender actor, OwnerData owner, String input, ColorTarget target, String value, boolean bypass) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        Settings s = settings.get();
        String colorValue = value == null ? "" : value.trim();
        Optional<ColorPalette.Entry> entry = s.palette.get(colorValue);
        String shownName;
        if (entry.isPresent()) {
            ColorPalette.Entry color = entry.get();
            if (!bypass && !isFreeColor(target, color.key()) && !actor.hasPermission(Permissions.color(target.key(), color.key()))) {
                return ActionResult.fail("messages.color-no-permission", Text.ph("color", colorNames.apply(color.key())));
            }
            shownName = colorNames.apply(color.key());
            apply(waypoint, target, color.legacy(), color.argb(s.backgroundAlpha));
        } else {
            if (!bypass && !actor.hasPermission(Permissions.color(target.key(), "custom"))) {
                return ActionResult.fail("messages.color-no-permission-custom");
            }
            if (target == ColorTarget.BACKGROUND) {
                Integer parsed = ColorPalette.parseHex(colorValue);
                if (parsed == null) {
                    return ActionResult.fail("messages.color-invalid", Text.ph("color", colorValue));
                }
                String digits = colorValue.replace("&", "").replace("#", "");
                int argb = digits.length() == 8 ? parsed : ((s.backgroundAlpha << 24) | (parsed & 0xFFFFFF));
                apply(waypoint, target, null, argb);
                shownName = WaypointFormat.hex(argb);
            } else {
                String legacy;
                if (colorValue.matches("#[A-Fa-f0-9]{6}")) {
                    legacy = "&" + colorValue.toUpperCase(java.util.Locale.ROOT);
                } else if (Text.isColorCode(colorValue)) {
                    legacy = colorValue;
                } else {
                    return ActionResult.fail("messages.color-invalid", Text.ph("color", colorValue));
                }
                apply(waypoint, target, legacy, 0);
                shownName = legacy + "■■■";
            }
        }
        WaypointUpdateEvent.Change change = target == ColorTarget.TEXT ? WaypointUpdateEvent.Change.TEXT_COLOR
                : target == ColorTarget.ICON ? WaypointUpdateEvent.Change.ICON_COLOR : WaypointUpdateEvent.Change.BACKGROUND_COLOR;
        changed(owner, waypoint, change);
        return ActionResult.ok("messages.color-changed." + target.key(), ph(waypoint, "color", shownName), SoundService.COLOR, waypoint);
    }

    /** Default colors are always allowed. */
    public boolean isFreeColor(ColorTarget target, String key) {
        Settings s = settings.get();
        switch (target) {
            case TEXT:
                return key.equals(s.defaultTextColorKey);
            case BACKGROUND:
                return key.equals(s.defaultBackgroundKey);
            default:
                return key.equals(s.defaultIconColorKey);
        }
    }

    private static void apply(Waypoint waypoint, ColorTarget target, String legacy, int argb) {
        switch (target) {
            case TEXT:
                waypoint.setTextColor(legacy);
                break;
            case ICON:
                waypoint.setIconColor(legacy);
                break;
            default:
                waypoint.setBackgroundArgb(argb);
                break;
        }
    }

    public ActionResult setIcon(OwnerData owner, String input, String icon, boolean bypass) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        if (!bypass && !settings.get().icons.contains(icon)) {
            return ActionResult.fail("messages.icon-invalid");
        }
        Waypoint waypoint = found.get();
        waypoint.setIcon(icon);
        changed(owner, waypoint, WaypointUpdateEvent.Change.ICON);
        return ActionResult.ok("messages.icon-changed", ph(waypoint), SoundService.ICON, waypoint);
    }

    public ActionResult toggleFavorite(OwnerData owner, String input) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        waypoint.setFavorite(!waypoint.isFavorite());
        changed(owner, waypoint, WaypointUpdateEvent.Change.FAVORITE);
        return waypoint.isFavorite()
                ? ActionResult.ok("messages.favorite-added", ph(waypoint), SoundService.FAVORITE_ADD, waypoint)
                : ActionResult.ok("messages.favorite-removed", ph(waypoint), SoundService.FAVORITE_REMOVE, waypoint);
    }

    private void changed(OwnerData owner, Waypoint waypoint, WaypointUpdateEvent.Change change) {
        store.update(waypoint);
        refresher.refresh(owner.owner());
        call(new WaypointUpdateEvent(waypoint.copy(), change));
    }

    // ------------------------------------------------------------------ delete

    /** Deletes a waypoint. Money or items paid for it are not given back. */
    public ActionResult delete(OwnerData owner, String input) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        if (!call(new WaypointDeleteEvent(waypoint.copy()))) {
            return ActionResult.fail("messages.cancelled");
        }
        boolean wasVisible = owner.isVisible(waypoint.getCode());
        owner.remove(waypoint.getCode());
        store.delete(owner.owner(), waypoint.getCode());
        if (wasVisible) {
            refresher.refresh(owner.owner());
        }
        return ActionResult.ok("messages.deleted", ph(waypoint), SoundService.DELETE, waypoint);
    }

    /** Deletes every waypoint of the owner (admin). */
    public CompletableFuture<Void> clear(OwnerData owner) {
        owner.clear();
        refresher.refresh(owner.owner());
        return store.deleteAll(owner.owner());
    }

    // ------------------------------------------------------------------ share

    /**
     * Gives a copy of the waypoint to another player. Completes on the actor's thread.
     * The receiver owns the copy; the sender keeps no link to it.
     */
    public CompletableFuture<ActionResult> share(Player actor, OwnerData owner, String input, String targetName) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return CompletableFuture.completedFuture(notFound(input));
        }
        if (targetName.equalsIgnoreCase(actor.getName()) && owner.owner().equals(actor.getUniqueId())) {
            return CompletableFuture.completedFuture(ActionResult.fail("messages.share-self"));
        }
        Settings s = settings.get();
        boolean charge = costs.isCharged(actor, s.shareCost);
        if (charge && !costs.canAfford(actor, s.shareCost)) {
            return CompletableFuture.completedFuture(
                    ActionResult.fail("messages.cost.not-enough", costs.placeholders(s.shareCost)));
        }
        Waypoint source = found.get();
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        sessions.getOrLoadByName(targetName).whenComplete((target, error) ->
                scheduler.runAtEntity(actor, () -> {
                    try {
                        result.complete(finishShare(actor, owner, source, targetName, error == null ? target : Optional.empty(), charge));
                    } catch (RuntimeException e) {
                        result.completeExceptionally(e);
                    }
                }));
        return result;
    }

    private ActionResult finishShare(Player actor, OwnerData owner, Waypoint source, String targetName,
                                     Optional<OwnerData> found, boolean charge) {
        Settings s = settings.get();
        if (!found.isPresent()) {
            return ActionResult.fail("messages.player-not-found", Text.ph("player", targetName));
        }
        OwnerData target = found.get();
        if (target.owner().equals(owner.owner())) {
            return ActionResult.fail("messages.share-self");
        }
        Player receiver = Bukkit.getPlayer(target.owner());
        String receiverName = receiver != null ? receiver.getName() : (target.name() != null ? target.name() : targetName);
        if (receiver == null && !s.shareAllowOffline) {
            return ActionResult.fail("messages.player-offline", Text.ph("player", receiverName));
        }
        if (receiver != null && s.shareCheckTargetLimit) {
            int limit = limits.limitOf(receiver);
            if (target.countForLimit(s.deathCountsTowardLimit) >= limit) {
                return ActionResult.fail("messages.share-target-limit", Text.ph("player", receiverName));
            }
        }
        Waypoint copy = new Waypoint(0L, target.owner(), newCode(target), source.getName(), source.getWorld(),
                source.getX(), source.getY(), source.getZ(), source.getTextColor(), source.getIcon(), source.getIconColor(),
                source.getBackgroundArgb(), false, 0L, System.currentTimeMillis());
        if (!call(new WaypointShareEvent(copy.copy(), target.owner()))) {
            return ActionResult.fail("messages.cancelled");
        }
        if (charge && !costs.charge(actor, s.shareCost)) {
            return ActionResult.fail("messages.cost.not-enough", costs.placeholders(s.shareCost));
        }
        target.put(copy);
        store.insert(copy);
        applyShow(target, copy.getCode());
        if (receiver != null) {
            shareNotifier.received(receiver, copy, actor.getName());
        }
        return ActionResult.ok(charge ? "messages.shared-paid" : "messages.shared",
                Text.merge(ph(source, "player", receiverName), costs.placeholders(s.shareCost)), SoundService.SHARE_SEND, source);
    }

    // ------------------------------------------------------------------ visibility

    /** Click on a waypoint: shows it, or hides it when it was already visible. */
    public ActionResult select(OwnerData owner, String input) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        if (owner.isVisible(waypoint.getCode())) {
            owner.removeVisible(waypoint.getCode());
            visibilityChanged(owner, waypoint, false);
            return ActionResult.ok("messages.hidden", ph(waypoint), SoundService.HIDE, waypoint);
        }
        applyShow(owner, waypoint.getCode());
        return ActionResult.ok(owner.accumulate() ? "messages.shown-accumulate" : "messages.shown", ph(waypoint),
                SoundService.SHOW, waypoint);
    }

    public ActionResult show(OwnerData owner, String input) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        applyShow(owner, found.get().getCode());
        return ActionResult.ok("messages.shown", ph(found.get()), SoundService.SHOW, found.get());
    }

    private void applyShow(OwnerData owner, String code) {
        if (owner.accumulate()) {
            owner.addVisible(code);
        } else {
            owner.setOnlyVisible(code);
        }
        owner.get(code).ifPresent(waypoint -> visibilityChanged(owner, waypoint, true));
    }

    public ActionResult hide(OwnerData owner, String input) {
        Optional<Waypoint> found = resolve(owner, input);
        if (!found.isPresent()) {
            return notFound(input);
        }
        Waypoint waypoint = found.get();
        if (!owner.removeVisible(waypoint.getCode())) {
            return ActionResult.fail("messages.not-visible", ph(waypoint));
        }
        visibilityChanged(owner, waypoint, false);
        return ActionResult.ok("messages.hidden", ph(waypoint), SoundService.HIDE, waypoint);
    }

    public ActionResult hideAll(OwnerData owner) {
        if (!owner.hasVisible()) {
            return ActionResult.fail("messages.nothing-visible");
        }
        owner.clearVisible();
        store.saveVisible(owner.owner(), owner.visible());
        refresher.refresh(owner.owner());
        return ActionResult.ok("messages.hidden-all", null, SoundService.HIDE_ALL, null);
    }

    private void visibilityChanged(OwnerData owner, Waypoint waypoint, boolean visible) {
        store.saveVisible(owner.owner(), owner.visible());
        refresher.refresh(owner.owner());
        Player player = Bukkit.getPlayer(owner.owner());
        if (player != null) {
            call(new WaypointVisibilityEvent(waypoint.copy(), player, visible));
        }
    }

    /** Switches between "single" (only the last selected waypoint is visible) and "accumulate". */
    public ActionResult toggleMode(OwnerData owner) {
        owner.accumulate(!owner.accumulate());
        if (!owner.accumulate()) {
            Optional<Waypoint> last = owner.lastSelected();
            owner.clearVisible();
            last.ifPresent(waypoint -> owner.addVisible(waypoint.getCode()));
            store.saveVisible(owner.owner(), owner.visible());
            refresher.refresh(owner.owner());
        }
        store.saveProfile(owner.owner(), owner.name(), owner.accumulate());
        return ActionResult.ok(owner.accumulate() ? "messages.mode-accumulate" : "messages.mode-single", null,
                SoundService.MODE, null);
    }
}
