package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.CostSpec;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import io.github.obedsource.advancedwaypoints.util.Chat;
import io.github.obedsource.advancedwaypoints.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Sharing with confirmation: the receiver must be online and gets a clickable [Accept] / [Decline]
 * message. Only when they accept is the sender charged and the copy created. Requests expire after
 * share.request-timeout-seconds, or when either player leaves.
 * <p>
 * Normal and death waypoints have their own switch (share.enabled / death-waypoint.share.enabled)
 * and price.
 */
public final class ShareService implements Listener {

    private record Request(String id, UUID sender, String senderName, String code, UUID receiver, String receiverName) {
    }

    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final SessionManager sessions;
    private final WaypointService waypoints;
    private final CostService costs;
    private final LimitService limits;
    private final PlatformScheduler scheduler;
    private final Feedback feedback;
    private final Map<String, Request> requests = new ConcurrentHashMap<>();

    public ShareService(Supplier<Settings> settings, Supplier<Messages> messages, SessionManager sessions,
                        WaypointService waypoints, CostService costs, LimitService limits,
                        PlatformScheduler scheduler, Feedback feedback) {
        this.settings = settings;
        this.messages = messages;
        this.sessions = sessions;
        this.waypoints = waypoints;
        this.costs = costs;
        this.limits = limits;
        this.scheduler = scheduler;
        this.feedback = feedback;
    }

    /** Whether this kind of waypoint can be shared (config switches). */
    public boolean isEnabled(Waypoint waypoint) {
        Settings s = settings.get();
        return waypoint.isDeath() ? s.deathShareEnabled : s.shareEnabled;
    }

    public CostSpec costOf(Waypoint waypoint) {
        Settings s = settings.get();
        return waypoint.isDeath() ? s.deathShareCost : s.shareCost;
    }

    /** Sends a share request to an online player. */
    public ActionResult request(Player sender, OwnerData owner, String input, String receiverName) {
        Optional<Waypoint> found = waypoints.resolve(owner, input);
        if (!found.isPresent()) {
            return waypoints.notFound(input);
        }
        Waypoint waypoint = found.get();
        Map<String, String> ph = waypoints.placeholders(waypoint, "player", receiverName);
        if (!isEnabled(waypoint)) {
            return ActionResult.fail(waypoint.isDeath() ? "messages.share-death-disabled" : "messages.share-disabled", ph);
        }
        Player receiver = Bukkit.getPlayerExact(receiverName);
        if (receiver == null) {
            return ActionResult.fail("messages.player-offline", ph);
        }
        ph.put("player", receiver.getName());
        if (receiver.getUniqueId().equals(owner.owner())) {
            return ActionResult.fail("messages.share-self");
        }
        CostSpec cost = costOf(waypoint);
        ph.putAll(costs.placeholders(cost));
        if (costs.isCharged(sender, cost) && !costs.canAfford(sender, cost)) {
            return ActionResult.fail("messages.cost.not-enough", ph);
        }
        String failure = receiverProblem(receiver, waypoint);
        if (failure != null) {
            return ActionResult.fail(failure, ph);
        }
        Request request = new Request(newId(), owner.owner(), sender.getName(), waypoint.getCode(),
                receiver.getUniqueId(), receiver.getName());
        requests.put(request.id(), request);
        int seconds = settings.get().shareRequestSeconds;
        sendRequest(receiver, request, waypoint, seconds);
        scheduler.runGlobalLater(() -> expire(request), seconds * 20L);
        ph.put("seconds", String.valueOf(seconds));
        return ActionResult.ok("messages.share-request-sent", ph, SoundService.SHARE_SEND, waypoint);
    }

    /** Limit problem of the receiver, as a language key, or null. */
    private String receiverProblem(Player receiver, Waypoint waypoint) {
        Settings s = settings.get();
        OwnerData data = sessions.getOnline(receiver.getUniqueId());
        if (data == null) {
            return "messages.share-target-loading";
        }
        if (s.shareCheckTargetLimit && (!waypoint.isDeath() || s.deathCountsTowardLimit)
                && data.countForLimit(s.deathCountsTowardLimit) >= limits.limitOf(receiver)) {
            return "messages.share-target-limit";
        }
        return null;
    }

    private void sendRequest(Player receiver, Request request, Waypoint waypoint, int seconds) {
        Messages m = messages.get();
        Map<String, String> ph = waypoints.placeholders(waypoint, "player", request.senderName(), "seconds", seconds);
        m.send(receiver, "share-request.received", ph);
        String command = "/" + settings.get().commandName + " share ";
        List<BaseComponent> parts = new ArrayList<>();
        parts.add(Chat.line(m.prefix() + m.raw("share-request.accept"), m.getList("share-request.accept-hover", ph),
                ClickEvent.Action.RUN_COMMAND, command + "accept " + request.id(), null));
        parts.add(Chat.line(m.raw("share-request.separator"), null, null, null, null));
        parts.add(Chat.line(m.raw("share-request.decline"), m.getList("share-request.decline-hover", ph),
                ClickEvent.Action.RUN_COMMAND, command + "decline " + request.id(), null));
        Chat.send(receiver, new TextComponent(parts.toArray(new BaseComponent[0])));
        feedback.sound(receiver, SoundService.SHARE_RECEIVE);
    }

    /** The receiver accepts: the sender pays and the copy is created. */
    public ActionResult accept(Player receiver, String id) {
        Request request = requests.get(id);
        if (request == null || !request.receiver().equals(receiver.getUniqueId())) {
            return ActionResult.fail("messages.share-request-not-found");
        }
        requests.remove(id);
        Player sender = Bukkit.getPlayer(request.sender());
        OwnerData senderData = sessions.getOnline(request.sender());
        OwnerData receiverData = sessions.getOnline(receiver.getUniqueId());
        Map<String, String> ph = Text.ph("player", request.senderName());
        if (sender == null || senderData == null) {
            return ActionResult.fail("messages.share-sender-offline", ph);
        }
        Waypoint source = senderData.get(request.code()).orElse(null);
        if (source == null || receiverData == null) {
            return ActionResult.fail("messages.share-waypoint-gone", ph);
        }
        ph = waypoints.placeholders(source, "player", request.senderName());
        if (!isEnabled(source)) {
            return ActionResult.fail(source.isDeath() ? "messages.share-death-disabled" : "messages.share-disabled", ph);
        }
        String problem = receiverProblem(receiver, source);
        if (problem != null) {
            ph.put("player", receiver.getName());
            return ActionResult.fail(problem, ph);
        }
        CostSpec cost = costOf(source);
        ph.putAll(costs.placeholders(cost));
        boolean pay = costs.isCharged(sender, cost);
        if (pay && !costs.charge(sender, cost)) {
            Map<String, String> senderPh = waypoints.placeholders(source, "player", receiver.getName());
            senderPh.putAll(costs.placeholders(cost));
            notify(sender, "messages.share-failed-cost", senderPh, false);
            return ActionResult.fail("messages.share-sender-cannot-pay", ph);
        }
        Waypoint copy = waypoints.giveCopy(receiverData, source);
        if (copy == null) {
            return ActionResult.fail("messages.cancelled");
        }
        Map<String, String> senderPh = waypoints.placeholders(source, "player", receiver.getName());
        senderPh.putAll(costs.placeholders(cost));
        notify(sender, pay ? "messages.shared-paid" : "messages.shared", senderPh, true);
        return ActionResult.ok("messages.share-accepted", waypoints.placeholders(copy, "player", request.senderName()),
                SoundService.SHARE_RECEIVE, copy);
    }

    public ActionResult decline(Player receiver, String id) {
        Request request = requests.get(id);
        if (request == null || !request.receiver().equals(receiver.getUniqueId())) {
            return ActionResult.fail("messages.share-request-not-found");
        }
        requests.remove(id);
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            notify(sender, "messages.share-declined-sender", Text.ph("player", receiver.getName()), false);
        }
        return ActionResult.ok("messages.share-declined", Text.ph("player", request.senderName()), SoundService.HIDE, null);
    }

    private void expire(Request request) {
        if (!requests.remove(request.id(), request)) {
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            notify(sender, "messages.share-expired-sender", Text.ph("player", request.receiverName()), false);
        }
        Player receiver = Bukkit.getPlayer(request.receiver());
        if (receiver != null) {
            notify(receiver, "messages.share-expired-receiver", Text.ph("player", request.senderName()), false);
        }
    }

    private void notify(Player player, String key, Map<String, String> ph, boolean success) {
        scheduler.runAtEntity(player, () -> {
            messages.get().send(player, key, ph);
            feedback.sound(player, success ? SoundService.SHARE_SEND : SoundService.ERROR);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        requests.values().removeIf(request -> request.sender().equals(uuid) || request.receiver().equals(uuid));
    }

    private String newId() {
        String chars = "abcdefghijkmnpqrstuvwxyz23456789";
        String id;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                builder.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            }
            id = builder.toString();
        } while (requests.containsKey(id));
        return id;
    }
}
