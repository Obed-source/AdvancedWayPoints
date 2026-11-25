package io.github.obedsource.advancedwaypoints.command;

import io.github.obedsource.advancedwaypoints.api.ColorTarget;
import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.service.LimitService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * /wp admin ... : full control over any player's waypoints, online or offline.
 */
final class AdminCommand extends SubCommand {

    private static final List<String> ACTIONS = List.of("help", "list", "menu", "edit", "info", "tp", "create",
            "rename", "color", "delete", "clear");

    AdminCommand(CommandServices services) {
        // Checked per action, so a staff member can have only some admin actions.
        super(services, "admin", List.of(), null, false);
    }

    private static String permissionOf(String action) {
        switch (action) {
            case "help":
                return Permissions.ADMIN_HELP;
            case "list":
                return Permissions.ADMIN_LIST;
            case "menu":
            case "edit":
                return Permissions.ADMIN_MENU;
            case "info":
                return Permissions.ADMIN_INFO;
            case "tp":
                return Permissions.ADMIN_TP;
            case "create":
                return Permissions.ADMIN_CREATE;
            case "rename":
                return Permissions.ADMIN_RENAME;
            case "color":
                return Permissions.ADMIN_COLOR;
            case "delete":
                return Permissions.ADMIN_DELETE;
            case "clear":
                return Permissions.ADMIN_CLEAR;
            default:
                return Permissions.ADMIN;
        }
    }

    @Override
    public boolean visibleTo(CommandSender sender) {
        for (String action : ACTIONS) {
            if (sender.hasPermission(permissionOf(action))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            usage(sender, "admin-help");
            return;
        }
        if (!sender.hasPermission(permissionOf(action))) {
            services.feedback().noPermission(sender);
            return;
        }
        if (action.equals("help")) {
            services.pages().help(sender, page(args, 1), true);
            services.feedback().sound(sender, SoundService.HELP);
            return;
        }
        if (args.length < 2) {
            usage(sender, "admin-" + action);
            return;
        }
        String target = args[1];
        switch (action) {
            case "list":
                withTarget(sender, target, data -> services.pages().list(sender, data, page(args, 2), true));
                break;
            case "menu":
                if (!(sender instanceof Player)) {
                    services.lang().send(sender, "messages.player-only");
                    return;
                }
                withTarget(sender, target, data -> services.menus().openMain((Player) sender, data, true, 0));
                break;
            case "edit":
                if (!(sender instanceof Player) || args.length < 3) {
                    usage(sender, "admin-menu");
                    return;
                }
                withTarget(sender, target, data -> {
                    Optional<Waypoint> waypoint = find(data, args[2]);
                    if (waypoint.isPresent()) {
                        services.menus().openEdit((Player) sender, data, true, waypoint.get().getCode(), 0);
                    } else {
                        services.feedback().error(sender, "messages.not-found", Text.ph("input", args[2]));
                    }
                });
                break;
            case "info":
                withTarget(sender, target, data -> {
                    Player online = Bukkit.getPlayer(data.owner());
                    Map<String, String> ph = Text.ph("player", data.name(), "count", data.count(),
                            "visible", data.visible().size(),
                            "limit", online != null ? LimitService.display(services.waypoints().limits().limitOf(online)) : "?",
                            "mode", services.lang().raw(data.accumulate() ? "placeholders.mode-accumulate" : "placeholders.mode-single"),
                            "online", services.lang().raw(online != null ? "placeholders.answer-yes" : "placeholders.answer-no"),
                            "uuid", data.owner());
                    services.lang().sendPlain(sender, "messages.admin-info", ph);
                });
                break;
            case "tp":
                if (!(sender instanceof Player) || args.length < 3) {
                    usage(sender, "admin-tp");
                    return;
                }
                withTarget(sender, target, data -> {
                    Optional<Waypoint> waypoint = find(data, args[2]);
                    if (!waypoint.isPresent()) {
                        services.feedback().error(sender, "messages.not-found", Text.ph("input", args[2]));
                        return;
                    }
                    Location location = waypoint.get().toLocation();
                    if (location == null) {
                        services.feedback().error(sender, "messages.world-not-loaded",
                                WaypointFormat.placeholders(waypoint.get(), services.config()));
                        return;
                    }
                    services.scheduler().teleport((Player) sender, location);
                    services.lang().send(sender, "messages.teleported", WaypointFormat.placeholders(waypoint.get(), services.config()));
                    services.feedback().sound(sender, SoundService.TELEPORT);
                });
                break;
            case "create":
                if (!(sender instanceof Player) || args.length < 3) {
                    usage(sender, "admin-create");
                    return;
                }
                Location here = ((Player) sender).getLocation();
                withTarget(sender, target, data ->
                        services.feedback().send(sender, services.waypoints().create((Player) sender, data, join(args, 2), here, false)));
                break;
            case "rename":
                if (args.length < 4) {
                    usage(sender, "admin-rename");
                    return;
                }
                withTarget(sender, target, data ->
                        services.feedback().send(sender, services.waypoints().rename(sender, data, args[2], join(args, 3), true)));
                break;
            case "color":
                if (args.length < 5) {
                    usage(sender, "admin-color");
                    return;
                }
                Optional<ColorTarget> colorTarget = ColorTarget.parse(args[3]);
                if (!colorTarget.isPresent()) {
                    usage(sender, "admin-color");
                    return;
                }
                withTarget(sender, target, data -> services.feedback().send(sender,
                        services.waypoints().setColor(sender, data, args[2], colorTarget.get(), args[4], true)));
                break;
            case "delete":
                if (args.length < 3) {
                    usage(sender, "admin-delete");
                    return;
                }
                withTarget(sender, target, data ->
                        services.feedback().send(sender, services.waypoints().delete(data, join(args, 2))));
                break;
            case "clear":
                withTarget(sender, target, data -> {
                    int count = data.count();
                    services.waypoints().clear(data);
                    services.lang().send(sender, "messages.admin-cleared", Text.ph("player", data.name(), "count", count));
                    services.feedback().sound(sender, SoundService.DELETE);
                });
                break;
            default:
                usage(sender, "admin-help");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String action : ACTIONS) {
                if (sender.hasPermission(permissionOf(action))) {
                    allowed.add(action);
                }
            }
            return filter(allowed, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("help")) {
            return onlinePlayers(args[1]);
        }
        if (args.length == 3) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (List.of("edit", "tp", "rename", "color", "delete").contains(action)) {
                Player online = Bukkit.getPlayerExact(args[1]);
                if (online != null && services.sessions().getOnline(online.getUniqueId()) != null) {
                    List<String> codes = new ArrayList<>();
                    for (Waypoint waypoint : services.sessions().getOnline(online.getUniqueId()).all()) {
                        codes.add(waypoint.getCode());
                    }
                    return filter(codes, args[2]);
                }
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("color")) {
            return filter(List.of("text", "icon", "background"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("color")) {
            return filter(services.config().palette.keys(), args[4]);
        }
        return Collections.emptyList();
    }
}
