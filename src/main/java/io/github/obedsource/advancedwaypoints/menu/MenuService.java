package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.api.ColorTarget;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.input.InputService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.service.Feedback;
import io.github.obedsource.advancedwaypoints.service.ShareService;
import io.github.obedsource.advancedwaypoints.service.TeleportService;
import io.github.obedsource.advancedwaypoints.service.WaypointService;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Items;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Opens the menus and routes their clicks. Layouts and static buttons are built once per load
 * ("load first") and cloned when a menu opens, so opening a menu only costs the dynamic items.
 */
public final class MenuService implements Listener {

    public static final String MAIN = "main";
    public static final String EDIT = "edit";
    public static final String COLOR = "color";
    public static final String ICON = "icon";
    public static final String CONFIRM = "confirm-delete";

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final Supplier<SoundService> sounds;
    private final WaypointService waypoints;
    private final ShareService shares;
    private final TeleportService teleports;
    private final InputService input;
    private final Feedback feedback;
    private final Supplier<String> commandName;
    private final Map<String, MenuLayout> layouts = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> buttonCache = new ConcurrentHashMap<>();

    public MenuService(Plugin plugin, PlatformScheduler scheduler, Supplier<Settings> settings, Supplier<Messages> messages,
                       Supplier<SoundService> sounds, WaypointService waypoints, ShareService shares,
                       TeleportService teleports, InputService input, Feedback feedback,
                       Supplier<String> commandName) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.settings = settings;
        this.messages = messages;
        this.sounds = sounds;
        this.waypoints = waypoints;
        this.shares = shares;
        this.teleports = teleports;
        this.input = input;
        this.feedback = feedback;
        this.commandName = commandName;
    }

    /** (Re)loads every layout from menus.yml and clears the cached buttons. */
    public void load(FileConfiguration menus) {
        layouts.clear();
        buttonCache.clear();
        for (String id : new String[]{MAIN, EDIT, COLOR, ICON, CONFIRM}) {
            layouts.put(id, MenuLayout.load(id, menus.getConfigurationSection(id), plugin.getLogger()));
        }
        // Warm the cache with every static button.
        for (MenuLayout layout : layouts.values()) {
            for (String key : new String[]{"filler", "back", "close"}) {
                MenuLayout.Item item = layout.item(key);
                if (item != null) {
                    buildButton(layout, item, null, null, false);
                }
            }
        }
    }

    MenuLayout layout(String id) {
        return layouts.get(id);
    }

    // ------------------------------------------------------------------ accessors used by menus

    Settings settings() {
        return settings.get();
    }

    Messages messages() {
        return messages.get();
    }

    Feedback feedback() {
        return feedback;
    }

    WaypointService waypoints() {
        return waypoints;
    }

    ShareService shares() {
        return shares;
    }

    TeleportService teleports() {
        return teleports;
    }

    InputService input() {
        return input;
    }

    PlatformScheduler scheduler() {
        return scheduler;
    }

    String command() {
        return commandName.get();
    }

    void sound(Player player, String key) {
        sounds.get().play(player, key);
    }

    /** Builds a button from its layout and the language file. Static buttons come from the cache. */
    ItemStack buildButton(MenuLayout layout, MenuLayout.Item item, Map<String, String> placeholders,
                          List<String> extraLore, boolean glow) {
        boolean cacheable = (placeholders == null || placeholders.isEmpty()) && extraLore == null && !glow;
        String cacheKey = layout.id() + "." + item.key();
        if (cacheable) {
            ItemStack cached = buttonCache.get(cacheKey);
            if (cached != null) {
                return cached.clone();
            }
        }
        Messages m = messages.get();
        String base = "menu." + layout.id() + ".items." + item.key();
        String name = m.get(base + ".name", placeholders);
        List<String> lore = new ArrayList<>(m.has(base + ".lore") ? m.getList(base + ".lore", placeholders) : List.of());
        if (extraLore != null) {
            lore.addAll(extraLore);
        }
        ItemStack stack = Items.build(item.material(), name, lore, item.customModelData(), glow);
        if (cacheable) {
            buttonCache.put(cacheKey, stack.clone());
        }
        return stack;
    }

    // ------------------------------------------------------------------ opening

    public void openMain(Player viewer, OwnerData owner, boolean admin, int page) {
        sound(viewer, SoundService.MENU_OPEN);
        new MainMenu(this, viewer, owner, admin, page).open();
    }

    void reopenMain(Player viewer, OwnerData owner, boolean admin, int page) {
        new MainMenu(this, viewer, owner, admin, page).open();
    }

    public void openEdit(Player viewer, OwnerData owner, boolean admin, String code, int returnPage) {
        if (!owner.hasCode(code)) {
            reopenMain(viewer, owner, admin, returnPage);
            return;
        }
        new EditMenu(this, viewer, owner, admin, code, returnPage).open();
    }

    void openColor(Player viewer, OwnerData owner, boolean admin, String code, ColorTarget target, int returnPage, int page) {
        new ColorMenu(this, viewer, owner, admin, code, target, returnPage, page).open();
    }

    void openIcon(Player viewer, OwnerData owner, boolean admin, String code, int returnPage, int page) {
        new IconMenu(this, viewer, owner, admin, code, returnPage, page).open();
    }

    void openConfirm(Player viewer, OwnerData owner, boolean admin, String code, int returnPage) {
        new ConfirmDeleteMenu(this, viewer, owner, admin, code, returnPage).open();
    }

    /** Closes every open plugin menu (used by /wp restart). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runAtEntity(player, () -> {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof Menu) {
                    player.closeInventory();
                }
            });
        }
    }

    /**
     * Prices for lore lines, from the viewer's point of view: {teleport_price}, {share_price} and
     * {create_price}. Shows the "free" text when the action costs this viewer nothing.
     */
    Map<String, String> pricePlaceholders(Player viewer, io.github.obedsource.advancedwaypoints.api.Waypoint waypoint) {
        io.github.obedsource.advancedwaypoints.service.CostService costs = waypoints.costs();
        String free = messages.get().raw("placeholders.free");
        Map<String, String> map = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<String, io.github.obedsource.advancedwaypoints.config.CostSpec> put = (key, cost) ->
                map.put(key, costs.isCharged(viewer, cost) ? costs.describe(cost) : free);
        if (waypoint != null) {
            put.accept("teleport_price", teleports.costOf(waypoint));
            put.accept("share_price", shares.costOf(waypoint));
        }
        put.accept("create_price", settings.get().createCost);
        map.put("price", map.getOrDefault("teleport_price", free));
        return map;
    }

    String ownerName(OwnerData owner) {
        return owner.name() == null ? owner.owner().toString().substring(0, 8) : owner.name();
    }

    Map<String, String> ownerPlaceholders(OwnerData owner) {
        return Text.ph("owner", ownerName(owner));
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Menu menu = (Menu) holder;
        if (!menu.viewer().getUniqueId().equals(event.getWhoClicked().getUniqueId())) {
            return;
        }
        menu.click(event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
