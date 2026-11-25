package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.api.ColorTarget;
import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.ColorPalette;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Items;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dye list to pick the text, icon or background color. Colors without permission show it in the lore. */
final class ColorMenu extends Menu {

    private final String code;
    private final ColorTarget target;
    private final int returnPage;
    private final int page;

    ColorMenu(MenuService service, Player viewer, OwnerData owner, boolean admin, String code, ColorTarget target,
              int returnPage, int page) {
        super(service, viewer, owner, admin, service.layout(MenuService.COLOR));
        this.code = code;
        this.target = target;
        this.returnPage = returnPage;
        this.page = page;
    }

    @Override
    protected String title() {
        return service.messages().get("menu.color.title." + target.key());
    }

    private boolean isCurrent(Waypoint waypoint, ColorPalette.Entry entry) {
        switch (target) {
            case TEXT:
                return entry.legacy().equalsIgnoreCase(String.valueOf(waypoint.getTextColor()));
            case ICON:
                return entry.legacy().equalsIgnoreCase(String.valueOf(waypoint.getIconColor()));
            default:
                return (entry.rgb() & 0xFFFFFF) == (waypoint.getBackgroundArgb() & 0xFFFFFF);
        }
    }

    @Override
    protected void render() {
        Waypoint waypoint = owner.get(code).orElse(null);
        if (waypoint == null) {
            redirectToMain(returnPage);
            return;
        }
        Messages messages = service.messages();
        filler();
        List<ColorPalette.Entry> colors = new ArrayList<>(service.settings().palette.all());
        List<Integer> slots = layout.contentSlots();
        int pages = pages(colors.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        int from = current * slots.size();
        for (int i = 0; i < slots.size() && from + i < colors.size(); i++) {
            ColorPalette.Entry entry = colors.get(from + i);
            boolean permitted = admin || service.waypoints().isFreeColor(target, entry.key())
                    || viewer.hasPermission(Permissions.color(target.key(), entry.key()));
            if (!permitted && service.settings().hideItemsWithoutPermission) {
                continue;
            }
            boolean selected = isCurrent(waypoint, entry);
            Map<String, String> ph = WaypointFormat.placeholders(waypoint, service.settings());
            ph.put("color", messages.raw("colors." + entry.key()));
            ph.put("color_code", entry.legacy());
            ph.put("hex", entry.hex());
            String name = messages.get("menu.color.color-item.name", ph);
            List<String> lore = new ArrayList<>(messages.getList("menu.color.color-item.lore", ph));
            lore.addAll(messages.getList(selected ? "menu.color.color-item.selected"
                    : permitted ? "menu.color.color-item.click" : "menu.color.color-item.locked", ph));
            set(slots.get(i), Items.build(entry.material(), name, lore, null, selected), click -> {
                if (!permitted) {
                    service.feedback().error(viewer, "messages.color-no-permission", ph);
                    return;
                }
                service.feedback().send(viewer, service.waypoints().setColor(viewer, owner, code, target, entry.key(), admin));
                refresh();
            });
        }
        pagination(current, pages, newPage -> {
            service.sound(viewer, SoundService.MENU_PAGE);
            service.openColor(viewer, owner, admin, code, target, returnPage, newPage);
        });
        button("back", Text.ph(), click -> {
            service.sound(viewer, SoundService.MENU_BACK);
            service.openEdit(viewer, owner, admin, code, returnPage);
        });
    }
}
