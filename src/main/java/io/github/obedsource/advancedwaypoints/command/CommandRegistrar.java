package io.github.obedsource.advancedwaypoints.command;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers the main command directly in the server's command map, so the name and aliases are read
 * from config.yml instead of plugin.yml, and can change with /wp restart.
 */
public final class CommandRegistrar {

    private static final String FALLBACK_PREFIX = "advancedwaypoints";

    private final Logger logger;
    private Command registered;

    public CommandRegistrar(Logger logger) {
        this.logger = logger;
    }

    public void register(Command command) {
        CommandMap map = commandMap();
        if (map == null) {
            logger.severe("Could not access the command map; the command is not available");
            return;
        }
        unregister();
        map.register(FALLBACK_PREFIX, command);
        registered = command;
        sync();
    }

    public void unregister() {
        if (registered == null) {
            return;
        }
        CommandMap map = commandMap();
        if (map != null) {
            registered.unregister(map);
            Map<String, Command> known = knownCommands(map);
            if (known != null) {
                // Paper 26+ returns a view whose iterator does not support remove(), so collect the keys first
                List<String> keys = new ArrayList<>();
                try {
                    for (Map.Entry<String, Command> entry : known.entrySet()) {
                        if (entry.getValue() == registered) {
                            keys.add(entry.getKey());
                        }
                    }
                    for (String key : keys) {
                        known.remove(key);
                    }
                } catch (RuntimeException e) {
                    logger.log(Level.FINE, "Could not remove the command from the known commands", e);
                }
            }
        }
        registered = null;
    }

    private static CommandMap commandMap() {
        Server server = Bukkit.getServer();
        try {
            return (CommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands(CommandMap map) {
        try {
            return (Map<String, Command>) map.getClass().getMethod("getKnownCommands").invoke(map);
        } catch (ReflectiveOperationException ignored) {
            // Spigot: protected field
        }
        Class<?> type = map.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("knownCommands");
                field.setAccessible(true);
                return (Map<String, Command>) field.get(map);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException e) {
                logger.log(Level.FINE, "Could not read known commands", e);
                return null;
            }
        }
        return null;
    }

    /** Sends the new command tree to online players (tab completion). */
    private void sync() {
        Server server = Bukkit.getServer();
        try {
            server.getClass().getMethod("syncCommands").invoke(server);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not available on every platform; players get it on next join
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.updateCommands();
            } catch (RuntimeException ignored) {
                // Folia: other thread, the client refreshes on next join
            }
        }
    }
}
