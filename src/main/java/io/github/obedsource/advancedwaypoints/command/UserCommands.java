package io.github.obedsource.advancedwaypoints.command;

import io.github.obedsource.advancedwaypoints.api.ColorTarget;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sub commands available to players. Each one is a small class with a single job. */
final class UserCommands {

    private UserCommands() {
    }

    static List<SubCommand> all(CommandServices s) {
        List<SubCommand> list = new ArrayList<>();
        list.add(new Help(s));
        list.add(new Menu(s));
        list.add(new Create(s));
        list.add(new ListCommand(s));
        list.add(new View(s));
        list.add(new Hide(s));
        list.add(new Mode(s));
        list.add(new Rename(s));
        list.add(new Color(s));
        list.add(new Favorite(s));
        list.add(new Share(s));
        list.add(new Delete(s));
        list.add(new Restart(s));
        return list;
    }

    static final class Help extends SubCommand {
        Help(CommandServices s) {
            super(s, "help", List.of("ayuda", "?"), Permissions.HELP, false);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            services.pages().help(sender, page(args, 0), false);
            services.feedback().sound(sender, SoundService.HELP);
        }
    }

    static final class Menu extends SubCommand {
        Menu(CommandServices s) {
            super(s, "menu", List.of("gui"), Permissions.MENU, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            OwnerData data = self(player);
            if (data != null) {
                services.menus().openMain(player, data, false, 0);
            }
        }
    }

    static final class Create extends SubCommand {
        Create(CommandServices s) {
            super(s, "create", List.of("crear", "set", "add"), Permissions.CREATE, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender, "create");
                return;
            }
            Player player = (Player) sender;
            OwnerData data = self(player);
            if (data != null) {
                services.feedback().send(player, services.waypoints().create(player, data, join(args, 0), player.getLocation(), true));
            }
        }
    }

    static final class ListCommand extends SubCommand {
        ListCommand(CommandServices s) {
            super(s, "list", List.of("lista", "ls"), Permissions.LIST, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            OwnerData data = self((Player) sender);
            if (data != null) {
                services.pages().list(sender, data, page(args, 0), false);
                services.feedback().sound(sender, SoundService.LIST);
            }
        }
    }

    static final class View extends SubCommand {
        View(CommandServices s) {
            super(s, "view", List.of("ver", "show", "go", "select"), Permissions.VIEW, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender, "view");
                return;
            }
            OwnerData data = self((Player) sender);
            if (data != null) {
                services.feedback().send(sender, services.waypoints().select(data, join(args, 0)));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? codes(sender, args[0]) : Collections.emptyList();
        }
    }

    static final class Hide extends SubCommand {
        Hide(CommandServices s) {
            super(s, "hide", List.of("ocultar", "clear"), Permissions.HIDE, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            OwnerData data = self((Player) sender);
            if (data == null) {
                return;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("all") || args[0].equalsIgnoreCase("todos")) {
                services.feedback().send(sender, services.waypoints().hideAll(data));
            } else {
                services.feedback().send(sender, services.waypoints().hide(data, join(args, 0)));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length != 1) {
                return Collections.emptyList();
            }
            List<String> options = new ArrayList<>(codes(sender, args[0]));
            options.addAll(filter(List.of("all"), args[0]));
            return options;
        }
    }

    static final class Mode extends SubCommand {
        Mode(CommandServices s) {
            super(s, "mode", List.of("modo", "toggle"), Permissions.MODE, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            OwnerData data = self((Player) sender);
            if (data != null) {
                services.feedback().send(sender, services.waypoints().toggleMode(data));
            }
        }
    }

    static final class Rename extends SubCommand {
        Rename(CommandServices s) {
            super(s, "rename", List.of("renombrar", "name"), Permissions.RENAME, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                usage(sender, "rename");
                return;
            }
            OwnerData data = self((Player) sender);
            if (data != null) {
                services.feedback().send(sender, services.waypoints().rename(sender, data, args[0], join(args, 1), false));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? codes(sender, args[0]) : Collections.emptyList();
        }
    }

    static final class Color extends SubCommand {
        Color(CommandServices s) {
            super(s, "color", List.of("colour"), null, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 3) {
                usage(sender, "color");
                return;
            }
            ColorTarget target = ColorTarget.parse(args[1]).orElse(null);
            if (target == null) {
                usage(sender, "color");
                return;
            }
            if (!sender.hasPermission(Permissions.color(target.key()))) {
                services.feedback().noPermission(sender);
                return;
            }
            OwnerData data = self((Player) sender);
            if (data != null) {
                services.feedback().send(sender, services.waypoints().setColor(sender, data, args[0], target, args[2], false));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return codes(sender, args[0]);
            }
            if (args.length == 2) {
                return filter(List.of("text", "icon", "background"), args[1]);
            }
            if (args.length == 3) {
                return filter(services.config().palette.keys(), args[2]);
            }
            return Collections.emptyList();
        }
    }

    static final class Favorite extends SubCommand {
        Favorite(CommandServices s) {
            super(s, "favorite", List.of("favorito", "fav"), Permissions.FAVORITE, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender, "favorite");
                return;
            }
            OwnerData data = self((Player) sender);
            if (data != null) {
                services.feedback().send(sender, services.waypoints().toggleFavorite(data, join(args, 0)));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? codes(sender, args[0]) : Collections.emptyList();
        }
    }

    static final class Share extends SubCommand {
        Share(CommandServices s) {
            super(s, "share", List.of("compartir"), Permissions.SHARE, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                usage(sender, "share");
                return;
            }
            Player player = (Player) sender;
            OwnerData data = self(player);
            if (data != null) {
                services.waypoints().share(player, data, args[0], args[1])
                        .thenAccept(result -> services.feedback().send(player, result));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return codes(sender, args[0]);
            }
            return args.length == 2 ? onlinePlayers(args[1]) : Collections.emptyList();
        }
    }

    static final class Delete extends SubCommand {
        Delete(CommandServices s) {
            super(s, "delete", List.of("eliminar", "remove", "del"), Permissions.DELETE, true);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender, "delete");
                return;
            }
            Player player = (Player) sender;
            OwnerData data = self(player);
            if (data != null) {
                services.feedback().send(player, services.waypoints().delete(data, join(args, 0), player));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? codes(sender, args[0]) : Collections.emptyList();
        }
    }

    static final class Restart extends SubCommand {
        Restart(CommandServices s) {
            super(s, "restart", List.of("reload", "reiniciar"), Permissions.ADMIN_RESTART, false);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            services.restart().run();
            services.lang().send(sender, "messages.restarted");
            services.feedback().sound(sender, SoundService.RESTART);
        }
    }
}
