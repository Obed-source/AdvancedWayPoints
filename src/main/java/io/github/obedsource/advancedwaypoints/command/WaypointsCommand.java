package io.github.obedsource.advancedwaypoints.command;

import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The main command (/waypoints, alias /wp by default; both configurable). Registered at runtime in the
 * server's command map so its name and aliases can come from config.yml.
 */
public final class WaypointsCommand extends Command {

    private final CommandServices services;
    private final List<SubCommand> subCommands = new ArrayList<>();

    public WaypointsCommand(String name, List<String> aliases, CommandServices services) {
        super(name, "AdvancedWayPoints main command", "/" + name + " help", aliases);
        this.services = services;
        subCommands.addAll(UserCommands.all(services));
        subCommands.add(new AdminCommand(services));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player && "menu".equals(services.config().noArgsAction)
                    && sender.hasPermission(Permissions.MENU)) {
                OwnerData data = services.sessions().getOnline(((Player) sender).getUniqueId());
                if (data != null) {
                    services.menus().openMain((Player) sender, data, false, 0);
                    return true;
                }
            }
            args = new String[]{"help"};
        }
        SubCommand sub = find(args[0]);
        if (sub == null) {
            services.feedback().error(sender, "messages.unknown-command", Text.ph("command", services.command()));
            return true;
        }
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            services.feedback().noPermission(sender);
            return true;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            services.lang().send(sender, "messages.player-only");
            return true;
        }
        sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    private SubCommand find(String label) {
        for (SubCommand sub : subCommands) {
            if (sub.matches(label)) {
                return sub;
            }
        }
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (SubCommand sub : subCommands) {
                if (sub.visibleTo(sender)) {
                    names.add(sub.name());
                }
            }
            return SubCommand.filter(names, args[0]);
        }
        SubCommand sub = find(args[0]);
        if (sub == null || (sub.permission() != null && !sender.hasPermission(sub.permission()))) {
            return Collections.emptyList();
        }
        return sub.complete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
