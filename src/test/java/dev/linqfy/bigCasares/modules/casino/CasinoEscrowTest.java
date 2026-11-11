package dev.linqfy.bigCasares.modules.casino;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoEscrowTest {

    static final class FakeBank implements CasinoBank {
        final Map<UUID, Long> balances = new HashMap<>();

        @Override
        public long balance(UUID player) {
            return balances.getOrDefault(player, 0L);
        }

        @Override
        public boolean withdraw(UUID player, long amount) {
            if (balance(player) < amount) {
                return false;
            }
            balances.merge(player, -amount, Long::sum);
            return true;
        }

        @Override
        public boolean deposit(UUID player, long amount) {
            balances.merge(player, amount, Long::sum);
            return true;
        }

        @Override
        public String format(long amount) {
            return Long.toString(amount);
        }
    }

    static final class MemoryStore implements CasinoEscrow.Store {
        Map<String, CasinoEscrow.Hold> saved = new LinkedHashMap<>();

        @Override
        public Map<String, CasinoEscrow.Hold> load() {
            return new LinkedHashMap<>(saved);
        }

        @Override
        public void save(Map<String, CasinoEscrow.Hold> holds) {
            saved = new LinkedHashMap<>(holds);
        }
    }

    private final UUID player = UUID.randomUUID();

    @Test
    void betsLeaveTheBalanceAndSettleWithThePayout() {
        FakeBank bank = new FakeBank();
        bank.deposit(player, 1000);
        CasinoEscrow escrow = new CasinoEscrow(bank, new MemoryStore());
        assertTrue(escrow.take("slot:a:" + player, player, 100));
        assertTrue(escrow.take("slot:a:" + player, player, 50));
        assertEquals(850, bank.balance(player));
        assertEquals(150, escrow.held("slot:a:" + player));
        escrow.settle("slot:a:" + player, 450);
        assertEquals(1300, bank.balance(player));
        assertEquals(0, escrow.held("slot:a:" + player));
    }

    @Test
    void insufficientFundsAreRejectedWithoutAHold() {
        FakeBank bank = new FakeBank();
        bank.deposit(player, 10);
        CasinoEscrow escrow = new CasinoEscrow(bank, new MemoryStore());
        assertFalse(escrow.take("k", player, 11));
        assertTrue(escrow.snapshot().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> escrow.take("k", player, 0));
    }

    @Test
    void aRestartRefundsWhatWasStillInPlay() {
        FakeBank bank = new FakeBank();
        bank.deposit(player, 500);
        MemoryStore store = new MemoryStore();
        CasinoEscrow before = new CasinoEscrow(bank, store);
        before.take("roulette:r:" + player, player, 200);
        before.take("poker:p:1:" + player, player, 100);
        assertEquals(200, bank.balance(player));

        CasinoEscrow after = new CasinoEscrow(bank, store);
        assertEquals(2, after.refundAll());
        assertEquals(500, bank.balance(player));
        assertTrue(store.saved.isEmpty());
    }

    @Test
    void adjustTracksPokerStacksAndRefundPrefixOnlyTouchesOneTable() {
        FakeBank bank = new FakeBank();
        bank.deposit(player, 1000);
        CasinoEscrow escrow = new CasinoEscrow(bank, new MemoryStore());
        escrow.take("poker:a:0:" + player, player, 400);
        escrow.take("poker:b:0:" + player, player, 400);
        escrow.adjust("poker:a:0:" + player, player, 650);
        assertEquals(1, escrow.refundPrefix("poker:a:"));
        assertEquals(850, bank.balance(player));
        assertEquals(400, escrow.held("poker:b:0:" + player));
    }
}
