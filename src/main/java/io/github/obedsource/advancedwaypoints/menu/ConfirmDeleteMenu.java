package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.service.ActionResult;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import org.bukkit.entity.Player;

import java.util.Map;

/** Asks for confirmation before deleting a waypoint. */
final class ConfirmDeleteMenu extends Menu {

    private final String code;
    private final int returnPage;

    ConfirmDeleteMenu(MenuService service, Player viewer, OwnerData owner, boolean admin, String code, int returnPage) {
        super(service, viewer, owner, admin, service.layout(MenuService.CONFIRM));
        this.code = code;
        this.returnPage = returnPage;
    }

    @Override
    protected String title() {
        return service.messages().get("menu.confirm-delete.title");
    }

    @Override
    protected void render() {
        Waypoint waypoint = owner.get(code).orElse(null);
        if (waypoint == null) {
            redirectToMain(returnPage);
            return;
        }
        Map<String, String> ph = WaypointFormat.placeholders(waypoint, service.settings());
        filler();
        button("preview", ph, null);
        button("confirm", ph, click -> {
            ActionResult result = service.waypoints().delete(owner, code);
            service.feedback().send(viewer, result);
            service.reopenMain(viewer, owner, admin, returnPage);
        });
        button("cancel", ph, click -> {
            service.sound(viewer, SoundService.MENU_BACK);
            service.openEdit(viewer, owner, admin, code, returnPage);
        });
    }
}
