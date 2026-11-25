package io.github.obedsource.advancedwaypoints.command;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.service.LimitService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Chat;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Paginated chat output: the waypoint list and the help pages. Everything is clickable. */
public final class ChatPages {

    /** One help line: language key, permission and whether it is an admin command. */
    private record HelpEntry(String key, String permission, boolean admin) {
    }

    private static final List<HelpEntry> HELP = List.of(
            new HelpEntry("help", Permissions.HELP, false),
            new HelpEntry("menu", Permissions.MENU, false),
            new HelpEntry("create", Permissions.CREATE, false),
            new HelpEntry("list", Permissions.LIST, false),
            new HelpEntry("view", Permissions.VIEW, false),
            new HelpEntry("hide", Permissions.HIDE, false),
            new HelpEntry("hide-all", Permissions.HIDE, false),
            new HelpEntry("mode", Permissions.MODE, false),
            new HelpEntry("rename", Permissions.RENAME, false),
            new HelpEntry("color", null, false),
            new HelpEntry("favorite", Permissions.FAVORITE, false),
            new HelpEntry("share", Permissions.SHARE, false),
            new HelpEntry("delete", Permissions.DELETE, false),
            new HelpEntry("teleport", Permissions.TELEPORT, false),
            new HelpEntry("admin-help", Permissions.ADMIN_HELP, true),
            new HelpEntry("admin-list", Permissions.ADMIN_LIST, true),
            new HelpEntry("admin-menu", Permissions.ADMIN_MENU, true),
            new HelpEntry("admin-info", Permissions.ADMIN_INFO, true),
            new HelpEntry("admin-tp", Permissions.ADMIN_TP, true),
            new HelpEntry("admin-create", Permissions.ADMIN_CREATE, true),
            new HelpEntry("admin-rename", Permissions.ADMIN_RENAME, true),
            new HelpEntry("admin-color", Permissions.ADMIN_COLOR, true),
            new HelpEntry("admin-delete", Permissions.ADMIN_DELETE, true),
            new HelpEntry("admin-clear", Permissions.ADMIN_CLEAR, true),
            new HelpEntry("restart", Permissions.ADMIN_RESTART, true));

    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final LimitService limits;

    public ChatPages(Supplier<Settings> settings, Supplier<Messages> messages, LimitService limits) {
        this.settings = settings;
        this.messages = messages;
        this.limits = limits;
    }

    private String cmd() {
        return settings.get().commandName;
    }

    // ------------------------------------------------------------------ help

    /**
     * @param adminHelp true for /wp admin help (user and admin commands), false for /wp help (user commands)
     */
    public void help(CommandSender sender, int page, boolean adminHelp) {
        Messages m = messages.get();
        List<HelpEntry> visible = new ArrayList<>();
        for (HelpEntry entry : HELP) {
            if (entry.admin() && !adminHelp) {
                continue;
            }
            if (entry.permission() == null || sender.hasPermission(entry.permission())) {
                visible.add(entry);
            }
        }
        int perPage = settings.get().helpPageSize;
        int pages = Math.max(1, (visible.size() + perPage - 1) / perPage);
        int current = Math.max(1, Math.min(page, pages));
        Map<String, String> ph = Text.ph("page", current, "pages", pages);
        m.sendPlain(sender, adminHelp ? "help.admin-header" : "help.header", ph);
        for (int i = (current - 1) * perPage; i < Math.min(visible.size(), current * perPage); i++) {
            HelpEntry entry = visible.get(i);
            String usage = m.raw("help.entries." + entry.key() + ".usage");
            String description = m.raw("help.entries." + entry.key() + ".description");
            Map<String, String> lph = Text.ph("usage", "/" + cmd() + " " + usage, "description", description);
            String line = m.format(m.raw(entry.admin() ? "help.admin-line" : "help.line"), lph);
            String suggestion = "/" + cmd() + " " + usage.replaceAll("[<\\[].*$", "").trim() + " ";
            Chat.send(sender, Chat.line(line, m.getList("help.hover", lph), ClickEvent.Action.SUGGEST_COMMAND, suggestion, null));
        }
        footer(sender, current, pages, "/" + cmd() + (adminHelp ? " admin help " : " help "));
    }

    // ------------------------------------------------------------------ list

    /**
     * Sends the waypoint list of an owner.
     *
     * @param admin true when an admin lists another player's waypoints (click opens the admin editor)
     */
    public void list(CommandSender sender, OwnerData owner, int page, boolean admin) {
        Messages m = messages.get();
        Settings s = settings.get();
        List<Waypoint> waypoints = owner.sorted(s.newestFirst);
        int perPage = s.listPageSize;
        int pages = Math.max(1, (waypoints.size() + perPage - 1) / perPage);
        int current = Math.max(1, Math.min(page, pages));
        String limit = sender instanceof Player && !admin
                ? LimitService.display(limits.limitOf((Player) sender)) : "-";
        Map<String, String> header = Text.ph("page", current, "pages", pages, "count", waypoints.size(), "limit", limit,
                "owner", owner.name() == null ? "?" : owner.name(),
                "mode", m.raw(owner.accumulate() ? "placeholders.mode-accumulate" : "placeholders.mode-single"));
        m.sendPlain(sender, admin ? "list.admin-header" : "list.header", header);
        if (waypoints.isEmpty()) {
            m.sendPlain(sender, "list.empty", header);
        }
        for (int i = (current - 1) * perPage; i < Math.min(waypoints.size(), current * perPage); i++) {
            Waypoint waypoint = waypoints.get(i);
            Map<String, String> ph = WaypointFormat.placeholders(waypoint, s);
            boolean visible = owner.isVisible(waypoint.getCode());
            ph.put("visible", m.raw(visible ? "list.visible-marker" : "list.hidden-marker"));
            ph.put("favorite", waypoint.isFavorite() ? m.raw("list.favorite-marker") : "");
            OptionalDouble distance = sender instanceof Player && owner.owner().equals(((Player) sender).getUniqueId())
                    ? DisplayManager.distance((Player) sender, waypoint) : OptionalDouble.empty();
            ph.put("distance", distance.isPresent() ? WaypointFormat.distance(distance.getAsDouble(), s)
                    : m.raw("list.other-world"));
            String line = m.format(m.raw("list.entry"), ph);
            List<String> hover = new ArrayList<>(m.getList(admin ? "list.admin-hover" : "list.hover", ph));
            if (s.showIdOnHover) {
                hover.addAll(m.getList("list.hover-id", ph));
            }
            String click = admin
                    ? "/" + cmd() + " admin edit " + owner.name() + " " + waypoint.getCode()
                    : "/" + cmd() + " view " + waypoint.getCode();
            Chat.send(sender, Chat.line(line, hover, ClickEvent.Action.RUN_COMMAND, click, waypoint.getCode()));
        }
        footer(sender, current, pages, admin
                ? "/" + cmd() + " admin list " + owner.name() + " "
                : "/" + cmd() + " list ");
    }

    private void footer(CommandSender sender, int current, int pages, String commandPrefix) {
        Messages m = messages.get();
        Map<String, String> ph = Text.ph("page", current, "pages", pages);
        List<BaseComponent> parts = new ArrayList<>();
        if (current > 1) {
            parts.add(Chat.line(m.raw("pages.previous"), m.getList("pages.previous-hover", ph),
                    ClickEvent.Action.RUN_COMMAND, commandPrefix + (current - 1), null));
        } else {
            parts.add(Chat.line(m.raw("pages.previous-disabled"), null, null, null, null));
        }
        parts.add(Chat.line(m.format(m.raw("pages.current"), ph), null, null, null, null));
        if (current < pages) {
            parts.add(Chat.line(m.raw("pages.next"), m.getList("pages.next-hover", ph),
                    ClickEvent.Action.RUN_COMMAND, commandPrefix + (current + 1), null));
        } else {
            parts.add(Chat.line(m.raw("pages.next-disabled"), null, null, null, null));
        }
        Chat.send(sender, new TextComponent(parts.toArray(new BaseComponent[0])));
    }
}
