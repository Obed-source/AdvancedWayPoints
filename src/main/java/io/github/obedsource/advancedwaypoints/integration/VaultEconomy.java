package io.github.obedsource.advancedwaypoints.integration;

import io.github.obedsource.advancedwaypoints.service.EconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault bridge. Only loaded when Vault is installed. */
public final class VaultEconomy implements EconomyProvider {

    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /** Returns the Vault economy, or null when no economy plugin is registered. */
    public static EconomyProvider hook() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : new VaultEconomy(provider.getProvider());
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.has(player, amount) && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
