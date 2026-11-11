package dev.linqfy.bigCasares.modules.casino;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Chips on the felt. Every bet is withdrawn into the escrow before it plays and leaves it only when the game
 * settles it. The ledger is saved on every change, so a crash or a reload refunds whatever was still in play
 * instead of losing it.
 */
public final class CasinoEscrow {

    public record Hold(UUID player, long amount) {
    }

    public interface Store {
        Map<String, Hold> load();

        void save(Map<String, Hold> holds);
    }

    private final CasinoBank bank;
    private final Store store;
    private final Map<String, Hold> holds = new LinkedHashMap<>();

    public CasinoEscrow(CasinoBank bank, Store store) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.store = Objects.requireNonNull(store, "store");
        holds.putAll(store.load());
    }

    /** Withdraws {@code amount} from the player into {@code key}. False when the player cannot cover it. */
    public synchronized boolean take(String key, UUID player, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Hold existing = holds.get(key);
        if (existing != null && !existing.player().equals(player)) {
            throw new IllegalStateException("Escrow " + key + " belongs to another player");
        }
        if (!bank.withdraw(player, amount)) {
            return false;
        }
        holds.put(key, new Hold(player, (existing == null ? 0 : existing.amount()) + amount));
        save();
        return true;
    }

    public synchronized long held(String key) {
        Hold hold = holds.get(key);
        return hold == null ? 0 : hold.amount();
    }

    /** Rewrites the amount held without touching the bank, for example a poker stack after a hand. */
    public synchronized void adjust(String key, UUID player, long amount) {
        if (amount <= 0) {
            holds.remove(key);
        } else {
            holds.put(key, new Hold(player, amount));
        }
        save();
    }

    /** Closes {@code key} and pays {@code payout} to its owner. The difference stays with (or comes from) the house. */
    public synchronized void settle(String key, long payout) {
        Hold hold = holds.remove(key);
        save();
        if (hold != null && payout > 0) {
            bank.deposit(hold.player(), payout);
        }
    }

    /** Returns the held chips to their owner. */
    public synchronized long refund(String key) {
        Hold hold = holds.remove(key);
        if (hold == null) {
            return 0;
        }
        save();
        bank.deposit(hold.player(), hold.amount());
        return hold.amount();
    }

    public synchronized int refundPrefix(String prefix) {
        int refunded = 0;
        for (String key : Map.copyOf(holds).keySet()) {
            if (key.startsWith(prefix)) {
                refund(key);
                refunded++;
            }
        }
        return refunded;
    }

    /** Refunds everything still in play. */
    public synchronized int refundAll() {
        return refundPrefix("");
    }

    public synchronized Map<String, Hold> snapshot() {
        return Map.copyOf(holds);
    }

    private void save() {
        store.save(Map.copyOf(holds));
    }
}
