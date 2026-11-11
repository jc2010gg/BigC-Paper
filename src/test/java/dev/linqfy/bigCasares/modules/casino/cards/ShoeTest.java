package dev.linqfy.bigCasares.modules.casino.cards;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoeTest {

    @Test
    void containsEveryCardOncePerDeck() {
        Shoe shoe = new Shoe(2, 1.0, new SplittableRandom(7));
        Map<Card, Integer> seen = new HashMap<>();
        for (int index = 0; index < 104; index++) {
            seen.merge(shoe.draw(), 1, Integer::sum);
        }
        assertEquals(52, seen.size());
        assertTrue(seen.values().stream().allMatch(count -> count == 2));
    }

    @Test
    void reportsTheCutCardAndReshufflesWhenEmpty() {
        Shoe shoe = new Shoe(1, 0.5, new SplittableRandom(1));
        for (int index = 0; index < 25; index++) {
            shoe.draw();
        }
        assertFalse(shoe.pastCutCard());
        shoe.draw();
        assertTrue(shoe.pastCutCard());
        for (int index = 0; index < 26; index++) {
            shoe.draw();
        }
        assertEquals(0, shoe.remaining());
        shoe.draw();
        assertEquals(51, shoe.remaining());
    }

    @Test
    void cardCodesRoundTrip() {
        for (Card card : Shoe.orderedDeck()) {
            assertEquals(card, Card.parse(card.code()));
        }
        assertEquals("10h", new Card(Rank.TEN, Suit.HEARTS).code());
        assertEquals("as", new Card(Rank.ACE, Suit.SPADES).code());
        assertThrows(IllegalArgumentException.class, () -> Card.parse("1x"));
    }
}
