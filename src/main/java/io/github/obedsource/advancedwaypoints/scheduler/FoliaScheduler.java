package io.github.obedsource.advancedwaypoints.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Folia implementation, written with reflection so the plugin compiles against the plain Spigot API.
 */
final class FoliaScheduler implements PlatformScheduler {

    private static final String PKG = "io.papermc.paper.threadedregions.scheduler.";

    private final Plugin plugin;
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;
    private final Method globalExecute;
    private final Method globalRunDelayed;
    private final Method globalCancelTasks;
    private final Method globalRunAtFixedRate;
    private final Method regionExecute;
    private final Method asyncRunNow;
    private final Method asyncCancelTasks;
    private final Method entityGetScheduler;
    private final Method entityExecute;
    private final Method entityRunDelayed;
    private final Method entityRunAtFixedRate;
    private final Method taskCancel;
    private final Method teleportAsync;

    static boolean isSupported() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> globalClass = Class.forName(PKG + "GlobalRegionScheduler");
            Class<?> regionClass = Class.forName(PKG + "RegionScheduler");
            Class<?> asyncClass = Class.forName(PKG + "AsyncScheduler");
            Class<?> entityClass = Class.forName(PKG + "EntityScheduler");
            Class<?> taskClass = Class.forName(PKG + "ScheduledTask");

            globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);

            globalExecute = globalClass.getMethod("execute", Plugin.class, Runnable.class);
            globalRunDelayed = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalCancelTasks = globalClass.getMethod("cancelTasks", Plugin.class);
            globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            regionExecute = regionClass.getMethod("execute", Plugin.class, Location.class, Runnable.class);
            asyncRunNow = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);
            asyncCancelTasks = asyncClass.getMethod("cancelTasks", Plugin.class);
            entityGetScheduler = Entity.class.getMethod("getScheduler");
            entityExecute = entityClass.getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            entityRunDelayed = entityClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            entityRunAtFixedRate = entityClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
            taskCancel = taskClass.getMethod("cancel");
            teleportAsync = Entity.class.getMethod("teleportAsync", Location.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to hook into the Folia schedulers", e);
        }
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    private Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia scheduler call failed: " + method.getName(), e);
        }
    }

    private static Consumer<Object> wrap(Runnable task) {
        return scheduledTask -> task.run();
    }

    @Override
    public void runAsync(Runnable task) {
        invoke(asyncRunNow, asyncScheduler, plugin, wrap(task));
    }

    @Override
    public void runGlobal(Runnable task) {
        invoke(globalExecute, globalScheduler, plugin, task);
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        invoke(globalRunDelayed, globalScheduler, plugin, wrap(task), Math.max(1L, delayTicks));
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        Object scheduled = invoke(globalRunAtFixedRate, globalScheduler, plugin, wrap(task),
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return () -> invoke(taskCancel, scheduled);
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        Object scheduler = invoke(entityGetScheduler, entity);
        invoke(entityExecute, scheduler, plugin, task, null, 0L);
    }

    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        Object scheduler = invoke(entityGetScheduler, entity);
        invoke(entityRunDelayed, scheduler, plugin, wrap(task), null, Math.max(1L, delayTicks));
    }

    @Override
    public TaskHandle runAtEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        Object scheduler = invoke(entityGetScheduler, entity);
        Object scheduled = invoke(entityRunAtFixedRate, scheduler, plugin, wrap(task), null,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return () -> {
            if (scheduled != null) {
                invoke(taskCancel, scheduled);
            }
        };
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        invoke(regionExecute, regionScheduler, plugin, location, task);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        return (CompletableFuture<Boolean>) invoke(teleportAsync, entity, location);
    }

    @Override
    public void cancelAll() {
        invoke(globalCancelTasks, globalScheduler, plugin);
        invoke(asyncCancelTasks, asyncScheduler, plugin);
    }
}
