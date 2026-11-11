package dev.linqfy.bigCasares.modules.casino.poker;

import dev.linqfy.bigCasares.modules.casino.cards.Card;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    private static HandValue best(String... codes) {
        List<Card> cards = Arrays.stream(codes).map(Card::parse).toList();
        return HandEvaluator.best(cards);
    }

    @Test
    void recognisesEveryCategory() {
        assertEquals(HandCategory.STRAIGHT_FLUSH, best("9h", "10h", "jh", "qh", "kh", "2c", "3d").category());
        assertEquals("Escalera real", best("10s", "js", "qs", "ks", "as", "2c", "2d").label());
        assertEquals(HandCategory.FOUR_OF_A_KIND, best("9h", "9s", "9d", "9c", "kh", "2c", "3d").category());
        assertEquals(HandCategory.FULL_HOUSE, best("9h", "9s", "9d", "kc", "kh", "2c", "3d").category());
        assertEquals(HandCategory.FLUSH, best("2h", "9h", "jh", "4h", "kh", "2c", "3d").category());
        assertEquals(HandCategory.STRAIGHT, best("9h", "10s", "jd", "qc", "kh", "2c", "3d").category());
        assertEquals(HandCategory.THREE_OF_A_KIND, best("9h", "9s", "9d", "qc", "kh", "2c", "3d").category());
        assertEquals(HandCategory.TWO_PAIR, best("9h", "9s", "kd", "kc", "4h", "2c", "3d").category());
        assertEquals(HandCategory.PAIR, best("9h", "9s", "jd", "qc", "kh", "2c", "3d").category());
        assertEquals(HandCategory.HIGH_CARD, best("9h", "7s", "jd", "qc", "kh", "2c", "3d").category());
    }

    @Test
    void wheelIsTheLowestStraight() {
        HandValue wheel = best("as", "2h", "3d", "4c", "5s", "9h", "kd");
        HandValue sixHigh = best("6s", "2h", "3d", "4c", "5s", "9h", "kd");
        assertEquals(HandCategory.STRAIGHT, wheel.category());
        assertTrue(sixHigh.compareTo(wheel) > 0);
    }

    @Test
    void kickersBreakTies() {
        HandValue aceKicker = best("9h", "9s", "ad", "7c", "4h", "2c", "3d");
        HandValue kingKicker = best("9d", "9c", "kd", "7s", "4s", "2h", "3h");
        assertTrue(aceKicker.compareTo(kingKicker) > 0);
        HandValue same = best("9d", "9c", "ac", "7s", "4s", "2h", "3h");
        assertEquals(0, aceKicker.compareTo(same));
    }

    @Test
    void fullHouseUsesTheBestTripsAndPair() {
        HandValue value = best("9h", "9s", "9d", "kc", "kh", "kd", "2c");
        assertEquals(HandCategory.FULL_HOUSE, value.category());
        assertEquals(List.of(13, 9), value.ranks());
    }
}
