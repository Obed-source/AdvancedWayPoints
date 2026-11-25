package io.github.obedsource.advancedwaypoints.command;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** A /wp sub command. Arguments exclude the sub command name itself. */
public abstract class SubCommand {

    protected final CommandServices services;
    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final boolean playerOnly;

    protected SubCommand(CommandServices services, String name, List<String> aliases, String permission, boolean playerOnly) {
        this.services = services;
        this.name = name;
        this.aliases = aliases;
        this.permission = permission;
        this.playerOnly = playerOnly;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String permission() {
        return permission;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    public boolean matches(String label) {
        String value = label.toLowerCase(Locale.ROOT);
        return name.equals(value) || aliases.contains(value);
    }

    /** Whether tab completion offers this sub command to the sender. */
    public boolean visibleTo(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    public abstract void execute(CommandSender sender, String[] args);

    public List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------ helpers

    protected void usage(CommandSender sender, String helpKey) {
        services.feedback().error(sender, "messages.usage",
                Text.ph("usage", "/" + services.command() + " " + services.lang().raw("help.entries." + helpKey + ".usage")));
    }

    /** The online data of the player running the command, or null (with a message) while it loads. */
    protected OwnerData self(Player player) {
        OwnerData data = services.sessions().getOnline(player.getUniqueId());
        if (data == null) {
            services.feedback().error(player, "messages.loading", null);
        }
        return data;
    }

    /** Loads a player's data by name (async) and continues on the sender's thread. */
    protected void withTarget(CommandSender sender, String targetName, Consumer<OwnerData> action) {
        services.sessions().getOrLoadByName(targetName).whenComplete((found, error) -> runFor(sender, () -> {
            if (error != null || found == null || !found.isPresent()) {
                services.feedback().error(sender, "messages.player-not-found", Text.ph("player", targetName));
                return;
            }
            OwnerData data = found.get();
            if (data.name() == null) {
                data.name(targetName);
            }
            action.accept(data);
        }));
    }

    protected void runFor(CommandSender sender, Runnable task) {
        if (sender instanceof Player) {
            services.scheduler().runAtEntity((Player) sender, task);
        } else {
            services.scheduler().runGlobal(task);
        }
    }

    protected static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    protected static int page(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[index]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    protected static List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    /** Waypoint codes of the player, for tab completion. */
    protected List<String> codes(CommandSender sender, String prefix) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        OwnerData data = services.sessions().getOnline(((Player) sender).getUniqueId());
        if (data == null) {
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>();
        for (Waypoint waypoint : data.all()) {
            codes.add(waypoint.getCode());
        }
        return filter(codes, prefix);
    }

    protected static List<String> onlinePlayers(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(names, prefix);
    }

    protected Optional<Waypoint> find(OwnerData data, String input) {
        return services.waypoints().resolve(data, input);
    }
}
