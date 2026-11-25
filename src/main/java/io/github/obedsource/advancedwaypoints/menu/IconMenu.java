package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Items;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Icon (tag) picker, listing the icons of config.yml. Only available from the menu. */
final class IconMenu extends Menu {

    private final String code;
    private final int returnPage;
    private final int page;

    IconMenu(MenuService service, Player viewer, OwnerData owner, boolean admin, String code, int returnPage, int page) {
        super(service, viewer, owner, admin, service.layout(MenuService.ICON));
        this.code = code;
        this.returnPage = returnPage;
        this.page = page;
    }

    @Override
    protected String title() {
        return service.messages().get("menu.icon.title");
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
        List<String> icons = service.settings().icons;
        List<Integer> slots = layout.contentSlots();
        int pages = pages(icons.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        int from = current * slots.size();
        Material material = layout.material("icon-item.material", Material.PAPER, null);
        Material selectedMaterial = layout.material("icon-item.selected-material", Material.MAP, null);
        for (int i = 0; i < slots.size() && from + i < icons.size(); i++) {
            String icon = icons.get(from + i);
            boolean selected = icon.equals(waypoint.getIcon());
            Map<String, String> ph = WaypointFormat.placeholders(waypoint, service.settings());
            ph.put("new_icon", icon);
            String name = messages.get("menu.icon.icon-item.name", ph);
            List<String> lore = new ArrayList<>(messages.getList("menu.icon.icon-item.lore", ph));
            lore.addAll(messages.getList(selected ? "menu.icon.icon-item.selected" : "menu.icon.icon-item.click", ph));
            set(slots.get(i), Items.build(selected ? selectedMaterial : material, name, lore, null, selected), click -> {
                service.feedback().send(viewer, service.waypoints().setIcon(owner, code, icon, admin));
                refresh();
            });
        }
        pagination(current, pages, newPage -> {
            service.sound(viewer, SoundService.MENU_PAGE);
            service.openIcon(viewer, owner, admin, code, returnPage, newPage);
        });
        button("back", Text.ph(), click -> {
            service.sound(viewer, SoundService.MENU_BACK);
            service.openEdit(viewer, owner, admin, code, returnPage);
        });
    }
}
