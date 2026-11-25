package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.api.ColorTarget;
import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.ColorPalette;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.input.InputRequest;
import io.github.obedsource.advancedwaypoints.service.ActionResult;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.entity.Player;

import java.util.Map;

/** Edit options of one waypoint. */
final class EditMenu extends Menu {

    private final String code;
    private final int returnPage;

    EditMenu(MenuService service, Player viewer, OwnerData owner, boolean admin, String code, int returnPage) {
        super(service, viewer, owner, admin, service.layout(MenuService.EDIT));
        this.code = code;
        this.returnPage = returnPage;
    }

    private Waypoint waypoint() {
        return owner.get(code).orElse(null);
    }

    @Override
    protected String title() {
        Waypoint waypoint = waypoint();
        Map<String, String> ph = waypoint == null ? Text.ph() : WaypointFormat.placeholders(waypoint, service.settings());
        return service.messages().format(service.messages().raw("menu.edit.title"),
                Text.merge(ph, service.ownerPlaceholders(owner)));
    }

    @Override
    protected void render() {
        Waypoint waypoint = waypoint();
        if (waypoint == null) {
            redirectToMain(returnPage);
            return;
        }
        Settings settings = service.settings();
        Messages messages = service.messages();
        Map<String, String> ph = WaypointFormat.placeholders(waypoint, settings);
        ph.putAll(service.ownerPlaceholders(owner));
        ph.put("text_color_name", colorName(waypoint.getTextColor(), settings.palette));
        ph.put("icon_color_name", colorName(waypoint.getIconColor(), settings.palette));
        ph.put("background_name", settings.palette.byRgb(waypoint.getBackgroundArgb())
                .map(e -> messages.raw("colors." + e.key())).orElse(WaypointFormat.hex(waypoint.getBackgroundArgb())));

        filler();
        button("preview", ph, null);
        guardedButton("rename", Permissions.RENAME, ph, click -> askRename(waypoint));
        guardedButton("text-color", Permissions.color(ColorTarget.TEXT.key()), ph, click -> openColors(ColorTarget.TEXT));
        guardedButton("icon-color", Permissions.color(ColorTarget.ICON.key()), ph, click -> openColors(ColorTarget.ICON));
        guardedButton("background-color", Permissions.color(ColorTarget.BACKGROUND.key()), ph, click -> openColors(ColorTarget.BACKGROUND));
        guardedButton("icon", Permissions.ICON, ph, click -> {
            service.sound(viewer, SoundService.MENU_CLICK);
            service.openIcon(viewer, owner, admin, code, returnPage, 0);
        });
        guardedButton(waypoint.isFavorite() ? "favorite-remove" : "favorite-add", Permissions.FAVORITE, ph, click -> {
            service.feedback().send(viewer, service.waypoints().toggleFavorite(owner, code));
            refresh();
        });
        if (!admin || owner.owner().equals(viewer.getUniqueId())) {
            guardedButton("share", Permissions.SHARE, ph, click -> askShare());
        }
        guardedButton("delete", Permissions.DELETE, ph, click -> {
            service.sound(viewer, SoundService.MENU_CLICK);
            service.openConfirm(viewer, owner, admin, code, returnPage);
        });
        if (admin) {
            button("teleport", ph, click -> {
                org.bukkit.Location location = waypoint.toLocation();
                if (location == null) {
                    service.feedback().error(viewer, "messages.world-not-loaded", ph);
                    return;
                }
                viewer.closeInventory();
                service.scheduler().teleport(viewer, location);
                service.sound(viewer, SoundService.TELEPORT);
            });
        }
        button("back", ph, click -> {
            service.sound(viewer, SoundService.MENU_BACK);
            service.reopenMain(viewer, owner, admin, returnPage);
        });
    }

    private String colorName(String legacy, ColorPalette palette) {
        return palette.byLegacy(legacy)
                .map(e -> service.messages().raw("colors." + e.key()))
                .orElse((legacy == null ? "" : legacy) + "■■■");
    }

    private void openColors(ColorTarget target) {
        service.sound(viewer, SoundService.MENU_CLICK);
        service.openColor(viewer, owner, admin, code, target, returnPage, 0);
    }

    private String commandPrefix() {
        return admin
                ? "/" + service.command() + " admin rename " + service.ownerName(owner) + " " + code
                : "/" + service.command() + " rename " + code;
    }

    private void askRename(Waypoint waypoint) {
        service.input().request(viewer, new InputRequest("rename", Text.strip(waypoint.getName()), commandPrefix(),
                text -> {
                    ActionResult result = service.waypoints().rename(viewer, owner, code, text, admin);
                    service.feedback().send(viewer, result);
                    service.openEdit(viewer, owner, admin, code, returnPage);
                },
                this::backHere));
    }

    private void askShare() {
        service.input().request(viewer, new InputRequest("share", "", "/" + service.command() + " share " + code,
                text -> service.waypoints().share(viewer, owner, code, text).thenAccept(result -> {
                    service.feedback().send(viewer, result);
                    service.openEdit(viewer, owner, admin, code, returnPage);
                }),
                this::backHere));
    }

    private void backHere() {
        service.sound(viewer, SoundService.INPUT_CANCEL);
        service.openEdit(viewer, owner, admin, code, returnPage);
    }
}
