package io.github.obedsource.advancedwaypoints.input;

import io.github.obedsource.advancedwaypoints.config.SoundService;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asks players for text with the method chosen in config.yml (input.type). When that method is not
 * available on the server (or for the request), the next one is used: ANVIL, SIGN, COMMAND, CHAT.
 */
public final class InputService {

    private final InputContext ctx;
    private final Map<InputType, InputProvider> providers = new EnumMap<>(InputType.class);

    public InputService(InputContext ctx) {
        this.ctx = ctx;
        register(new AnvilInputProvider(ctx));
        register(new SignInputProvider(ctx));
        register(new CommandInputProvider(ctx));
        register(new ChatInputProvider(ctx));
    }

    private void register(InputProvider provider) {
        providers.put(provider.type(), provider);
    }

    public void registerListeners(PluginManager manager) {
        for (InputProvider provider : providers.values()) {
            if (provider instanceof Listener) {
                manager.registerEvents((Listener) provider, ctx.plugin());
            }
        }
    }

    /** Whether the anvil can be used on this server (shown in the startup log). */
    public boolean anvilSupported() {
        return providers.get(InputType.ANVIL).isAvailable(new InputRequest("rename", "", null, s -> { }, () -> { }));
    }

    public void request(Player player, InputRequest request) {
        cancel(player.getUniqueId());
        InputType[] order = InputType.values();
        int start = ctx.settings().get().inputType.ordinal();
        for (int i = start; i < order.length; i++) {
            InputProvider provider = providers.get(order[i]);
            if (!provider.isAvailable(request)) {
                continue;
            }
            if (order[i] != InputType.ANVIL) {
                player.closeInventory();
            }
            if (provider.open(player, request)) {
                ctx.sounds().get().play(player, SoundService.INPUT_OPEN);
                return;
            }
        }
        // Chat is always available, this is only reached if it failed unexpectedly.
        providers.get(InputType.CHAT).open(player, request);
    }

    public void cancel(UUID player) {
        for (InputProvider provider : providers.values()) {
            provider.cancel(player);
        }
    }

    public void shutdown() {
        for (InputProvider provider : providers.values()) {
            provider.shutdown();
        }
    }
}
