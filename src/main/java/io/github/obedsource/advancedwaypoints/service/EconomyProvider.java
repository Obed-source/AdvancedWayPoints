package io.github.obedsource.advancedwaypoints.service;

import org.bukkit.OfflinePlayer;

/** Money bridge (Vault). Keeps the business code free of Vault classes. */
public interface EconomyProvider {

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    void deposit(OfflinePlayer player, double amount);

    String format(double amount);

    /** Used when Vault or an economy plugin is missing: everything is free. */
    EconomyProvider NONE = new EconomyProvider() {
        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public void deposit(OfflinePlayer player, double amount) {
        }

        @Override
        public String format(double amount) {
            return String.format(java.util.Locale.ROOT, "%.2f", amount);
        }
    };
}
