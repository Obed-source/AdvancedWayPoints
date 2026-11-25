package io.github.obedsource.advancedwaypoints.input;

import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text input with a real anvil menu (the rename field). Opening a working anvil without NMS needs
 * Paper's {@code HumanEntity#openAnvil} or Spigot 1.21's {@code MenuType.ANVIL}; both are looked up
 * by reflection, and when neither exists this provider reports itself unavailable.
 */
public final class AnvilInputProvider implements InputProvider, Listener {

    private static final class Session {
        final InputRequest request;
        final Inventory top;
        boolean done;

        Session(InputRequest request, Inventory top) {
            this.request = request;
            this.top = top;
        }
    }

    private final InputContext ctx;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Object anvilMenuType;
    private final Method menuTypeCreate;
    private final Method paperOpenAnvil;

    public AnvilInputProvider(InputContext ctx) {
        this.ctx = ctx;
        Object menuType = null;
        Method create = null;
        try {
            Class<?> menuTypeClass = Class.forName("org.bukkit.inventory.MenuType");
            menuType = menuTypeClass.getField("ANVIL").get(null);
            Class<?> typed = Class.forName("org.bukkit.inventory.MenuType$Typed");
            create = typed.getMethod("create", HumanEntity.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            menuType = null;
            create = null;
        }
        anvilMenuType = menuType;
        menuTypeCreate = create;
        Method paper;
        try {
            paper = HumanEntity.class.getMethod("openAnvil", Location.class, boolean.class);
        } catch (NoSuchMethodException e) {
            paper = null;
        }
        paperOpenAnvil = paper;
    }

    @Override
    public InputType type() {
        return InputType.ANVIL;
    }

    @Override
    public boolean isAvailable(InputRequest request) {
        return menuTypeCreate != null || paperOpenAnvil != null;
    }

    @Override
    public boolean open(Player player, InputRequest request) {
        InventoryView view = null;
        String title = ctx.messages().get().get("input.anvil-title." + request.purpose());
        try {
            if (menuTypeCreate != null) {
                view = (InventoryView) menuTypeCreate.invoke(anvilMenuType, player, title);
                player.openInventory(view);
                view = player.getOpenInventory();
            } else if (paperOpenAnvil != null) {
                view = (InventoryView) paperOpenAnvil.invoke(player, null, true);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ctx.plugin().getLogger().fine("Anvil input failed: " + e);
            return false;
        }
        if (view == null || !(view.getTopInventory() instanceof AnvilInventory)) {
            return false;
        }
        Inventory top = view.getTopInventory();
        String initial = request.initial() == null || request.initial().isEmpty() ? " " : request.initial();
        top.setItem(0, paper(initial));
        sessions.put(player.getUniqueId(), new Session(request, top));
        return true;
    }

    private static ItemStack paper(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String renameText(InventoryView view, Inventory top) {
        try {
            Method method = Class.forName("org.bukkit.inventory.view.AnvilView").getMethod("getRenameText");
            if (method.getDeclaringClass().isInstance(view)) {
                return (String) method.invoke(view);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // older API
        }
        return top instanceof AnvilInventory ? legacyRenameText((AnvilInventory) top) : null;
    }

    @SuppressWarnings("deprecation")
    private static String legacyRenameText(AnvilInventory inventory) {
        return inventory.getRenameText();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        Session session = sessions.get(event.getView().getPlayer().getUniqueId());
        if (session == null || session.top != event.getInventory()) {
            return;
        }
        String text = renameText(event.getView(), event.getInventory());
        event.setResult(paper(text == null || text.isEmpty() ? " " : text));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || session.top != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != 2 || session.done) {
            return;
        }
        String text = renameText(event.getView(), session.top);
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        session.done = true;
        Player player = (Player) event.getWhoClicked();
        String value = Text.strip(text).trim();
        ctx.scheduler().runAtEntity(player, () -> {
            sessions.remove(player.getUniqueId());
            session.top.clear();
            player.closeInventory();
            session.request.onSubmit().accept(value);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session != null && session.top == event.getView().getTopInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.top != event.getInventory()) {
            return;
        }
        // Clear before the anvil returns its items to the player.
        session.top.clear();
        sessions.remove(event.getPlayer().getUniqueId());
        if (!session.done && event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            ctx.scheduler().runAtEntity(player, session.request.onCancel());
        }
    }

    @Override
    public void cancel(UUID player) {
        Session session = sessions.remove(player);
        if (session != null) {
            session.done = true;
            session.top.clear();
        }
    }

    @Override
    public void shutdown() {
        for (Session session : sessions.values()) {
            session.done = true;
            session.top.clear();
        }
        sessions.clear();
    }
}
