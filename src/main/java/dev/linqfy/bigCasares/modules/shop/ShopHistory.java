package dev.linqfy.bigCasares.modules.shop;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Each player's last purchases and sales that went through, newest first, from any menu. Kept in memory while the
 * server runs (a restart forgets it); the economy plugin stays the record of the money itself.
 */
public final class ShopHistory {
    static final int KEPT = 10;

    record Entry(Instant at, String message) {
    }

    private final Map<UUID, Deque<Entry>> entries = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public ShopHistory() {
        this(Instant::now);
    }

    ShopHistory(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** A successful operation's message ("Compraste ... por ..."). */
    void record(UUID playerId, String message) {
        Deque<Entry> kept = entries.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        synchronized (kept) {
            kept.addFirst(new Entry(clock.get(), message));
            while (kept.size() > KEPT) kept.removeLast();
        }
    }

    /** The player's last operation that went through, if any (any thread). */
    public java.util.Optional<String> latest(UUID playerId) {
        Deque<Entry> kept = entries.get(playerId);
        if (kept == null) {
            return java.util.Optional.empty();
        }
        synchronized (kept) {
            return kept.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(kept.peekFirst().message());
        }
    }

    /** Chat lines for the player: newest first, each with how long ago. */
    public List<String> lines(UUID playerId) {
        Deque<Entry> kept = entries.get(playerId);
        List<String> lines = new ArrayList<>();
        lines.add("§6Tus últimas operaciones en la tienda");
        if (kept == null || kept.isEmpty()) {
            lines.add("§7Todavía no compraste ni vendiste nada desde que arrancó el servidor.");
            return lines;
        }
        Instant now = clock.get();
        synchronized (kept) {
            for (Entry entry : kept) {
                lines.add("§8" + ago(Duration.between(entry.at(), now)) + " §f" + entry.message());
            }
        }
        return lines;
    }

    static String ago(Duration elapsed) {
        long seconds = Math.max(0, elapsed.getSeconds());
        if (seconds < 60) return "recién";
        if (seconds < 3600) return "hace " + seconds / 60 + " min";
        if (seconds < 86_400) return "hace " + seconds / 3600 + " h";
        return "hace " + seconds / 86_400 + " d";
    }
}
