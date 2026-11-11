package dev.linqfy.bigCasares.modules.casino.pawn;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PawnTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void appraisalAccountsForWearAndEnchantments() {
        Appraiser appraiser = new Appraiser(Appraiser.defaultPrices());
        assertEquals(3000, appraiser.appraise(new AppraisalInput("ELYTRA", 1, 432, 0, 0)).getAsLong());
        assertEquals(900, appraiser.appraise(new AppraisalInput("ELYTRA", 1, 432, 432, 0)).getAsLong(),
            "a broken elytra keeps 30%");
        assertEquals(653, appraiser.appraise(new AppraisalInput("DIAMOND_PICKAXE", 1, 1561, 0, 3)).getAsLong());
        assertEquals(1200, appraiser.appraise(new AppraisalInput("diamond", 10, 0, 0, 0)).getAsLong());
        assertTrue(appraiser.appraise(new AppraisalInput("DIRT", 64, 0, 0, 0)).isEmpty());
    }

    @Test
    void ticketsCanBeRedeemedUntilTheyExpireIntoTheWindow() {
        PawnLedger ledger = new PawnLedger(0.6, 1.25, 0.85, 48 * HOUR);
        UUID owner = UUID.randomUUID();
        PawnTicket ticket = ledger.pawn(owner, "data", "elytra", 3000, 0);
        assertEquals(1800, ticket.loan());
        assertEquals(2250, ticket.redeemPrice());
        assertEquals(2550, ticket.salePrice());
        assertEquals(1, ledger.pawnedBy(owner).size());
        assertThrows(IllegalStateException.class, () -> ledger.redeem(ticket.id(), UUID.randomUUID(), HOUR));
        assertEquals(ticket, ledger.redeem(ticket.id(), owner, HOUR));
        assertTrue(ledger.all().isEmpty());

        PawnTicket late = ledger.pawn(owner, "data", "elytra", 3000, 0);
        assertTrue(ledger.expire(47 * HOUR).isEmpty());
        assertEquals(1, ledger.expire(48 * HOUR).size());
        assertThrows(IllegalStateException.class, () -> ledger.redeem(late.id(), owner, 49 * HOUR));
        assertEquals(1, ledger.showcase().size());
        ledger.buy(late.id());
        assertThrows(IllegalStateException.class, () -> ledger.buy(late.id()));
    }

    @Test
    void seizedGoodsGoStraightToTheWindow() {
        PawnLedger ledger = new PawnLedger(0.6, 1.25, 0.85, 48 * HOUR);
        PawnTicket seized = ledger.consign("data", "netherite pickaxe", 2200, 5);
        assertEquals(PawnTicket.State.SHOWCASE, seized.state());
        assertEquals(1870, seized.salePrice());
        assertEquals(1, ledger.showcase().size());
    }
}
