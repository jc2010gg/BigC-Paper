package dev.linqfy.bigCasares.modules.casino.pit;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParimutuelBookTest {

    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();
    private final UUID caro = UUID.randomUUID();

    @Test
    void winnersSplitThePoolMinusTheCut() {
        ParimutuelBook book = new ParimutuelBook(0.10);
        book.bet(ana, ParimutuelBook.Corner.RED, 300);
        book.bet(beto, ParimutuelBook.Corner.RED, 100);
        book.bet(caro, ParimutuelBook.Corner.BLUE, 600);
        assertEquals(2.25, book.odds(ParimutuelBook.Corner.RED), 1e-9);
        Map<UUID, Long> payouts = book.settle(ParimutuelBook.Corner.RED);
        assertEquals(675, payouts.get(ana));
        assertEquals(225, payouts.get(beto));
        assertTrue(!payouts.containsKey(caro));
        assertTrue(book.isEmpty());
    }

    @Test
    void nobodyOnTheWinnerMeansEveryoneIsRefunded() {
        ParimutuelBook book = new ParimutuelBook(0.10);
        book.bet(ana, ParimutuelBook.Corner.BLUE, 300);
        book.bet(ana, ParimutuelBook.Corner.RED, 50);
        Map<UUID, Long> payouts = book.settle(ParimutuelBook.Corner.RED);
        assertEquals(315, payouts.get(ana), "ana's red bet takes the pool minus the cut");

        ParimutuelBook empty = new ParimutuelBook(0.10);
        empty.bet(beto, ParimutuelBook.Corner.BLUE, 300);
        assertEquals(Map.of(beto, 300L), empty.settle(ParimutuelBook.Corner.RED));
    }

    @Test
    void lopsidedBookNeverPaysLessThanTheStake() {
        ParimutuelBook book = new ParimutuelBook(0.10);
        book.bet(ana, ParimutuelBook.Corner.RED, 1000);
        book.bet(beto, ParimutuelBook.Corner.BLUE, 1);
        assertEquals(1000, book.settle(ParimutuelBook.Corner.RED).get(ana));
    }
}
