package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base menu. The layout and static buttons come pre-built from {@link MenuService} (cached on load);
 * a menu only fills its dynamic part (waypoints of the viewer, current page...).
 */
public abstract class Menu implements InventoryHolder {

    protected final MenuService service;
    protected final Player viewer;
    protected final OwnerData owner;
    protected final boolean admin;
    protected final MenuLayout layout;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(MenuService service, Player viewer, OwnerData owner, boolean admin, MenuLayout layout) {
        this.service = service;
        this.viewer = viewer;
        this.owner = owner;
        this.admin = admin;
        this.layout = layout;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected abstract String title();

    protected abstract void render();

    public void open() {
        inventory = Bukkit.createInventory(this, layout.rows() * 9, Text.color(title()));
        draw();
        viewer.openInventory(inventory);
    }

    /** Redraws the menu in place, without closing it. */
    public void refresh() {
        if (inventory == null) {
            open();
            return;
        }
        draw();
    }

    private void draw() {
        inventory.clear();
        actions.clear();
        render();
    }

    void click(int rawSlot, ClickType type) {
        Consumer<ClickType> action = actions.get(rawSlot);
        if (action != null) {
            action.accept(type);
        }
    }

    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inventory.getSize() || item == null) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    /** Places a configured button on all of its slots. */
    protected void button(String key, Map<String, String> placeholders, Consumer<ClickType> action) {
        MenuLayout.Item item = layout.item(key);
        if (item == null) {
            return;
        }
        ItemStack stack = service.buildButton(layout, item, placeholders, null, false);
        for (int slot : item.slots()) {
            set(slot, stack, action);
        }
    }

    /**
     * Places a button that needs a permission. Without it the button is hidden or shown with a
     * "no permission" lore line (menus.hide-items-without-permission).
     */
    protected void guardedButton(String key, String permission, Map<String, String> placeholders, Consumer<ClickType> action) {
        if (allowed(permission)) {
            button(key, placeholders, action);
            return;
        }
        if (service.settings().hideItemsWithoutPermission) {
            return;
        }
        MenuLayout.Item item = layout.item(key);
        if (item == null) {
            return;
        }
        List<String> extra = service.messages().getList("menu.no-permission-lore", null);
        ItemStack stack = service.buildButton(layout, item, placeholders, extra, false);
        for (int slot : item.slots()) {
            set(slot, stack, click -> service.feedback().noPermission(viewer));
        }
    }

    protected boolean allowed(String permission) {
        return admin || permission == null || viewer.hasPermission(permission);
    }

    /** Fills every free slot listed by the "filler" button. */
    protected void filler() {
        button("filler", null, null);
    }

    /** Draws previous/next/page buttons; returns nothing, the callback receives the new page. */
    protected void pagination(int page, int pages, Consumer<Integer> goTo) {
        Map<String, String> ph = Text.ph("page", page + 1, "pages", pages);
        if (page > 0) {
            button("previous-page", ph, click -> goTo.accept(page - 1));
        } else {
            button("previous-page-disabled", ph, null);
        }
        if (page + 1 < pages) {
            button("next-page", ph, click -> goTo.accept(page + 1));
        } else {
            button("next-page-disabled", ph, null);
        }
        button("page-info", ph, null);
    }

    /** The waypoint of this menu no longer exists (deleted elsewhere): go back to the list next tick. */
    protected void redirectToMain(int page) {
        service.scheduler().runAtEntityLater(viewer, () -> service.reopenMain(viewer, owner, admin, page), 1L);
    }

    protected int pages(int total) {
        int perPage = Math.max(1, layout.contentSlots().size());
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    public Player viewer() {
        return viewer;
    }
}
