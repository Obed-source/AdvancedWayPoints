package io.github.obedsource.advancedwaypoints;

import io.github.obedsource.advancedwaypoints.api.AdvancedWaypointsAPI;
import io.github.obedsource.advancedwaypoints.command.ChatPages;
import io.github.obedsource.advancedwaypoints.command.CommandRegistrar;
import io.github.obedsource.advancedwaypoints.command.CommandServices;
import io.github.obedsource.advancedwaypoints.command.WaypointsCommand;
import io.github.obedsource.advancedwaypoints.config.ConfigFile;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.input.InputContext;
import io.github.obedsource.advancedwaypoints.input.InputService;
import io.github.obedsource.advancedwaypoints.integration.UpdateChecker;
import io.github.obedsource.advancedwaypoints.integration.WaypointsApiImpl;
import io.github.obedsource.advancedwaypoints.menu.MenuService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.service.EconomyProvider;
import io.github.obedsource.advancedwaypoints.service.Feedback;
import io.github.obedsource.advancedwaypoints.service.LimitService;
import io.github.obedsource.advancedwaypoints.service.NameValidator;
import io.github.obedsource.advancedwaypoints.service.RegionProvider;
import io.github.obedsource.advancedwaypoints.service.RestrictionService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.service.WaypointService;
import io.github.obedsource.advancedwaypoints.session.PlayerListener;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import io.github.obedsource.advancedwaypoints.storage.DataStore;
import io.github.obedsource.advancedwaypoints.storage.StorageException;
import io.github.obedsource.advancedwaypoints.storage.sqlite.SqliteDatabase;
import io.github.obedsource.advancedwaypoints.storage.sqlite.SqlitePlayerRepository;
import io.github.obedsource.advancedwaypoints.storage.sqlite.SqliteWaypointRepository;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * AdvancedWayPoints - personal waypoints shown with floating text.
 * Author: XDiez (Obed-source). Licensed under the Apache License 2.0.
 * <p>
 * This class only wires the components together; each component has a single responsibility.
 */
public final class AdvancedWaypointsPlugin extends JavaPlugin {

    private static final String[] BUNDLED_LANGUAGES = {"es", "en"};

    private PlatformScheduler scheduler;
    private ConfigFile configFile;
    private ConfigFile menusFile;
    private ConfigFile soundsFile;

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile SoundService sounds;
    private volatile EconomyProvider economy = EconomyProvider.NONE;

    private SqliteDatabase database;
    private SessionManager sessions;
    private DisplayManager displays;
    private InputService input;
    private MenuService menus;
    private WaypointService waypoints;
    private CommandRegistrar commandRegistrar;
    private CommandServices commandServices;
    private UpdateChecker updater;
    private String registeredCommandSignature;

    @Override
    public void onEnable() {
        scheduler = PlatformScheduler.create(this);
        configFile = new ConfigFile(this, "config.yml");
        menusFile = new ConfigFile(this, "menus.yml");
        soundsFile = new ConfigFile(this, "sounds.yml");
        loadFiles();

        database = new SqliteDatabase(new File(getDataFolder(), settings.databaseFile), getLogger());
        try {
            database.open();
        } catch (StorageException e) {
            getLogger().log(Level.SEVERE, "Disabling: " + e.getMessage(), e.getCause());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        DataStore store = new DataStore(new SqliteWaypointRepository(database), new SqlitePlayerRepository(database),
                database.executor(), getLogger());
        sessions = new SessionManager(store, getLogger());

        PluginManager pm = Bukkit.getPluginManager();
        RegionProvider regions = hookWorldGuard(pm);
        hookVault(pm);

        LimitService limits = new LimitService(() -> settings.defaultLimit);
        waypoints = new WaypointService(() -> settings, sessions, store, limits,
                new RestrictionService(() -> settings, regions), () -> economy, new NameValidator(() -> settings),
                scheduler, this::colorName);
        displays = new DisplayManager(this, scheduler, sessions, () -> settings);
        waypoints.setRefresher(displays);
        Feedback feedback = new Feedback(() -> messages, () -> sounds);
        waypoints.setShareNotifier((receiver, copy, sender) -> scheduler.runAtEntity(receiver, () -> {
            messages.send(receiver, "messages.share-received",
                    Text.merge(WaypointFormat.placeholders(copy, settings), Text.ph("player", sender)));
            sounds.play(receiver, SoundService.SHARE_RECEIVE);
        }));

        input = new InputService(new InputContext(this, scheduler, () -> settings, () -> messages, () -> sounds));
        input.registerListeners(pm);
        menus = new MenuService(this, scheduler, () -> settings, () -> messages, () -> sounds, waypoints, input, feedback,
                () -> settings.commandName);
        menus.load(menusFile.get());
        pm.registerEvents(menus, this);

        updater = new UpdateChecker(this, getFile());
        PlayerListener players = new PlayerListener(sessions, displays, input, scheduler, getLogger(), this::notifyUpdate);
        pm.registerEvents(players, this);

        commandServices = new CommandServices(scheduler, () -> settings, () -> messages, () -> sounds, sessions, waypoints,
                menus, feedback, new ChatPages(() -> settings, () -> messages, limits), this::restart);
        commandRegistrar = new CommandRegistrar(getLogger());
        registerCommand();

        hookPlaceholderApi(pm, limits);
        Bukkit.getServicesManager().register(AdvancedWaypointsAPI.class,
                new WaypointsApiImpl(sessions, waypoints, scheduler), this, ServicePriority.Normal);

        // Players already online (plugin loaded by a plugin manager or /reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.load(player);
        }
        scheduler.runAsync(() -> updater.check(settings));
        // Economy plugins may register with Vault after us: hook again once the server finished starting.
        scheduler.runGlobalLater(() -> hookVault(pm), 1L);

        getLogger().info("Enabled on " + (scheduler.isFolia() ? "Folia" : Bukkit.getName()) + " " + Bukkit.getBukkitVersion()
                + " | text input: " + settings.inputType + (input.anvilSupported() ? "" : " (anvil not supported here, using the next method)")
                + " | by XDiez");
    }

    @Override
    public void onDisable() {
        if (commandRegistrar != null) {
            commandRegistrar.unregister();
        }
        if (displays != null) {
            displays.shutdown();
        }
        if (input != null) {
            input.shutdown();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (database != null) {
            database.close();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
    }

    // ------------------------------------------------------------------ configuration

    private void loadFiles() {
        settings = new Settings(configFile.load(), getLogger());
        menusFile.load();
        sounds = new SoundService(soundsFile.load());
        messages = new Messages(loadLanguage(settings.language), settings.messagePrefix,
                Map.of("cmd", settings.commandName, "prefix", Text.color(settings.messagePrefix)));
    }

    private YamlConfiguration loadLanguage(String language) {
        YamlConfiguration active = null;
        for (String bundled : BUNDLED_LANGUAGES) {
            YamlConfiguration loaded = new ConfigFile(this, "lang/" + bundled + ".yml").load();
            if (bundled.equals(language)) {
                active = loaded;
            }
        }
        if (active == null) {
            ConfigFile custom = new ConfigFile(this, "lang/" + language + ".yml");
            if (custom.file().exists()) {
                active = custom.load();
                try (InputStreamReader reader = new InputStreamReader(getResource("lang/en.yml"), StandardCharsets.UTF_8)) {
                    active.setDefaults(YamlConfiguration.loadConfiguration(reader));
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Could not load English fallback texts", e);
                }
            } else {
                getLogger().warning("Language file lang/" + language + ".yml not found, using English");
                active = new ConfigFile(this, "lang/en.yml").load();
            }
        }
        return active;
    }

    private String colorName(String key) {
        String path = "colors." + key;
        return messages.has(path) ? messages.raw(path) : key;
    }

    /** /wp restart: reloads every file. Data stays in memory; the database is not reloaded. */
    public void restart() {
        loadFiles();
        menus.closeAll();
        menus.load(menusFile.get());
        String signature = settings.commandName + settings.commandAliases;
        if (!signature.equals(registeredCommandSignature)) {
            registerCommand();
        }
        displays.restartAll();
        hookVault(Bukkit.getPluginManager());
    }

    private void registerCommand() {
        // The command map may remove aliases it cannot register, so it needs a mutable list.
        commandRegistrar.register(new WaypointsCommand(settings.commandName,
                new java.util.ArrayList<>(settings.commandAliases), commandServices));
        registeredCommandSignature = settings.commandName + settings.commandAliases;
    }

    // ------------------------------------------------------------------ integrations

    private RegionProvider hookWorldGuard(PluginManager pm) {
        Plugin plugin = pm.getPlugin("WorldGuard");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            RegionProvider provider = new io.github.obedsource.advancedwaypoints.integration.WorldGuardRegions();
            getLogger().info("Hooked into WorldGuard (region rules enabled).");
            return provider;
        } catch (LinkageError e) {
            getLogger().warning("WorldGuard found but its API could not be used: " + e);
            return null;
        }
    }

    private void hookVault(PluginManager pm) {
        if (pm.getPlugin("Vault") == null) {
            economy = EconomyProvider.NONE;
            return;
        }
        try {
            EconomyProvider vault = io.github.obedsource.advancedwaypoints.integration.VaultEconomy.hook();
            economy = vault == null ? EconomyProvider.NONE : vault;
            if (vault == null) {
                getLogger().info("Vault found without an economy plugin; prices are disabled.");
            }
        } catch (LinkageError e) {
            economy = EconomyProvider.NONE;
        }
    }

    private void hookPlaceholderApi(PluginManager pm, LimitService limits) {
        if (pm.getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new io.github.obedsource.advancedwaypoints.integration.WaypointPlaceholders(this, sessions, limits,
                    () -> settings, () -> messages).register();
            getLogger().info("Hooked into PlaceholderAPI (%awp_...%).");
        } catch (LinkageError e) {
            getLogger().warning("PlaceholderAPI found but could not be hooked: " + e);
        }
    }

    private void notifyUpdate(Player player) {
        if (updater == null || updater.latestVersion() == null || !settings.updaterNotifyAdmins
                || !player.hasPermission(Permissions.ADMIN_UPDATE)) {
            return;
        }
        scheduler.runAtEntityLater(player, () -> messages.send(player,
                updater.downloaded() ? "messages.update-downloaded" : "messages.update-available",
                Text.ph("version", updater.latestVersion(), "changelog", updater.changelog())), 40L);
    }
}
