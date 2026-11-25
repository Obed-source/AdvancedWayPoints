package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.input.InputRequest;
import io.github.obedsource.advancedwaypoints.service.ActionResult;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Items;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** The waypoint list: left click shows/hides, right click edits. Always paginated. */
final class MainMenu extends Menu {

    private final int page;

    MainMenu(MenuService service, Player viewer, OwnerData owner, boolean admin, int page) {
        super(service, viewer, owner, admin, service.layout(MenuService.MAIN));
        this.page = page;
    }

    private List<Waypoint> waypoints() {
        return owner.sorted(service.settings().newestFirst);
    }

    @Override
    protected String title() {
        int pages = pages(owner.count());
        int current = Math.min(page, pages - 1) + 1;
        Map<String, String> ph = Text.merge(service.ownerPlaceholders(owner), Text.ph("page", current, "pages", pages));
        return service.messages().format(service.messages().raw(admin ? "menu.main.admin-title" : "menu.main.title"), ph);
    }

    @Override
    protected void render() {
        Settings settings = service.settings();
        Messages messages = service.messages();
        filler();
        List<Waypoint> list = waypoints();
        List<Integer> slots = layout.contentSlots();
        int pages = pages(list.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        int from = current * slots.size();

        if (list.isEmpty()) {
            button("empty", null, null);
        }
        Material normal = layout.material("waypoint.material", Material.MAP, null);
        Material favorite = layout.material("waypoint.favorite-material", Material.FILLED_MAP, null);
        boolean glowVisible = layout.bool("waypoint.glow-when-visible", true);
        for (int i = 0; i < slots.size() && from + i < list.size(); i++) {
            Waypoint waypoint = list.get(from + i);
            boolean visible = owner.isVisible(waypoint.getCode());
            Map<String, String> ph = WaypointFormat.placeholders(waypoint, settings);
            ph.put("status", messages.raw(visible ? "menu.main.waypoint.visible" : "menu.main.waypoint.hidden"));
            ph.put("favorite", waypoint.isFavorite() ? messages.raw("menu.main.waypoint.favorite") : "");
            OptionalDouble distance = owner.owner().equals(viewer.getUniqueId())
                    ? DisplayManager.distance(viewer, waypoint) : OptionalDouble.empty();
            ph.put("distance", distance.isPresent()
                    ? WaypointFormat.distance(distance.getAsDouble(), settings)
                    : messages.raw("menu.main.waypoint.other-world"));
            // Dynamic hints: left click shows or hides; shift + left click teleports when allowed.
            ph.put("left_click", messages.format(messages.raw(visible
                    ? "menu.main.waypoint.left-click-hide" : "menu.main.waypoint.left-click-show"), ph));
            ph.putAll(service.pricePlaceholders(viewer, waypoint));
            ph.put("teleport", canShortcutTeleport(waypoint)
                    ? messages.format(messages.raw(service.teleports().costOf(waypoint) != null
                    && service.waypoints().costs().isCharged(viewer, service.teleports().costOf(waypoint))
                    ? "menu.main.waypoint.teleport-paid" : "menu.main.waypoint.teleport"), ph)
                    : "");
            ph.put("death", waypoint.isDeath()
                    ? messages.format(messages.raw(waypoint.getExpiresAt() > 0
                    ? "menu.main.waypoint.death-expires" : "menu.main.waypoint.death"), ph)
                    : "");
            String name = messages.get("menu.main.waypoint.name", ph);
            List<String> lore = new java.util.ArrayList<>();
            for (String line : messages.rawList(admin ? "menu.main.waypoint.admin-lines" : "menu.main.waypoint.lines")) {
                String formatted = messages.format(line, ph);
                // Lines made only of an empty placeholder (e.g. {favorite}) are dropped.
                if (line.contains("{") && Text.strip(formatted).trim().isEmpty()) {
                    continue;
                }
                lore.add(formatted);
            }
            set(slots.get(i), Items.build(waypoint.isFavorite() ? favorite : normal, name, lore, null, glowVisible && visible),
                    click -> onWaypointClick(waypoint.getCode(), click));
        }

        pagination(current, pages, newPage -> {
            service.sound(viewer, SoundService.MENU_PAGE);
            service.reopenMain(viewer, owner, admin, newPage);
        });

        Map<String, String> ph = Text.merge(service.ownerPlaceholders(owner),
                Text.ph("count", owner.count(), "visible", owner.visible().size()));
        if (!admin) {
            guardedButton(owner.accumulate() ? "view-mode-accumulate" : "view-mode-single", Permissions.MODE, ph, click -> {
                feedbackAndRefresh(service.waypoints().toggleMode(owner));
            });
        }
        guardedButton("hide-all", admin ? null : Permissions.HIDE, ph, click -> feedbackAndRefresh(service.waypoints().hideAll(owner)));
        guardedButton("create", admin ? null : Permissions.CREATE, ph, click -> askCreate(current));
        button("info", ph, null);
        button("close", ph, click -> {
            service.sound(viewer, SoundService.MENU_BACK);
            viewer.closeInventory();
        });
    }

    /** Shift + left click teleport: enabled in config and allowed for this viewer. */
    private boolean canShortcutTeleport(Waypoint waypoint) {
        if (!admin && !service.settings().teleportShortcut) {
            return false;
        }
        return service.teleports().canTeleport(viewer, waypoint, admin);
    }

    private void onWaypointClick(String code, ClickType click) {
        Waypoint target = owner.get(code).orElse(null);
        if (click == ClickType.SHIFT_LEFT && target != null && canShortcutTeleport(target)) {
            ActionResult result = service.teleports().teleport(viewer, owner, code, admin);
            if (result.success()) {
                service.scheduler().runAtEntityLater(viewer, viewer::closeInventory, 1L);
            }
            service.feedback().send(viewer, result);
            return;
        }
        if (click.isRightClick()) {
            if (!admin && !hasAnyEditPermission()) {
                service.feedback().noPermission(viewer);
                return;
            }
            service.sound(viewer, SoundService.MENU_CLICK);
            service.openEdit(viewer, owner, admin, code, page);
            return;
        }
        if (admin && !owner.owner().equals(viewer.getUniqueId())) {
            // Admins edit other players' waypoints; visibility is personal.
            service.sound(viewer, SoundService.MENU_CLICK);
            service.openEdit(viewer, owner, true, code, page);
            return;
        }
        if (!allowed(Permissions.VIEW)) {
            service.feedback().noPermission(viewer);
            return;
        }
        feedbackAndRefresh(service.waypoints().select(owner, code));
    }

    private boolean hasAnyEditPermission() {
        for (String permission : new String[]{Permissions.RENAME, Permissions.DELETE, Permissions.SHARE, Permissions.FAVORITE,
                Permissions.ICON, Permissions.TELEPORT, Permissions.color("text"), Permissions.color("icon"), Permissions.color("background")}) {
            if (viewer.hasPermission(permission)) {
                return true;
            }
        }
        return !service.settings().hideItemsWithoutPermission;
    }

    private void feedbackAndRefresh(ActionResult result) {
        service.feedback().send(viewer, result);
        refresh();
    }

    private void askCreate(int currentPage) {
        Location location = viewer.getLocation();
        String suggestion = admin
                ? "/" + service.command() + " admin create " + service.ownerName(owner)
                : "/" + service.command() + " create";
        service.input().request(viewer, new InputRequest("create", "", suggestion,
                text -> {
                    ActionResult result = service.waypoints().create(viewer, owner, text, location, !admin);
                    service.feedback().send(viewer, result);
                    service.reopenMain(viewer, owner, admin, currentPage);
                },
                () -> {
                    service.sound(viewer, SoundService.INPUT_CANCEL);
                    service.reopenMain(viewer, owner, admin, currentPage);
                }));
    }
}
