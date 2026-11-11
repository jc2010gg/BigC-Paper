package dev.linqfy.bigCasares.modules.casino.pit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pool betting on a fight: the house keeps a cut and the winners split the rest in proportion to their stake.
 * When nobody backed the winner, or the fight is called off, every bet is returned in full.
 */
public final class ParimutuelBook {

    public enum Corner {
        RED,
        BLUE
    }

    private final double houseCut;
    private final Map<UUID, Map<Corner, Long>> bets = new LinkedHashMap<>();

    public ParimutuelBook(double houseCut) {
        if (houseCut < 0 || houseCut >= 1) {
            throw new IllegalArgumentException("House cut must be within [0, 1)");
        }
        this.houseCut = houseCut;
    }

    public void bet(UUID bettor, Corner corner, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("A bet must be positive");
        }
        bets.computeIfAbsent(bettor, ignored -> new LinkedHashMap<>()).merge(corner, amount, Long::sum);
    }

    public long total(Corner corner) {
        return bets.values().stream().mapToLong(map -> map.getOrDefault(corner, 0L)).sum();
    }

    public long total() {
        return total(Corner.RED) + total(Corner.BLUE);
    }

    public long stake(UUID bettor, Corner corner) {
        return bets.getOrDefault(bettor, Map.of()).getOrDefault(corner, 0L);
    }

    /** Current decimal odds for {@code corner} (chips returned per chip), or 0 while nobody backed it. */
    public double odds(Corner corner) {
        long backing = total(corner);
        return backing == 0 ? 0.0 : total() * (1.0 - houseCut) / backing;
    }

    public Map<UUID, Long> settle(Corner winner) {
        long backing = total(winner);
        if (backing == 0) {
            return refundAll();
        }
        long distributable = (long) Math.floor(total() * (1.0 - houseCut));
        Map<UUID, Long> payouts = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<Corner, Long>> entry : bets.entrySet()) {
            long stake = entry.getValue().getOrDefault(winner, 0L);
            if (stake > 0) {
                // Never pay less than the stake back: with a lopsided book the cut comes out of the losers only.
                long share = Math.max(stake, distributable * stake / backing);
                payouts.put(entry.getKey(), share);
            }
        }
        bets.clear();
        return payouts;
    }

    public Map<UUID, Long> refundAll() {
        Map<UUID, Long> refunds = new LinkedHashMap<>();
        bets.forEach((bettor, map) -> refunds.put(bettor, map.values().stream().mapToLong(Long::longValue).sum()));
        bets.clear();
        return refunds;
    }

    public boolean isEmpty() {
        return bets.isEmpty();
    }
}
