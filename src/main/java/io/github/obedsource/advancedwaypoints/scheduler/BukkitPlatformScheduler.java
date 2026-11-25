package io.github.obedsource.advancedwaypoints.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

/** Spigot / Paper / Purpur implementation (single main thread). */
final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return bukkitTask::cancel;
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        runGlobal(task);
    }

    @Override
    public void runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        runGlobalLater(task, delayTicks);
    }

    @Override
    public TaskHandle runAtEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return bukkitTask::cancel;
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        runGlobal(task);
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        return CompletableFuture.completedFuture(entity.teleport(location));
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
