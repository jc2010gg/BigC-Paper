package dev.linqfy.bigCasares.modules.casino.slots;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotEngineTest {

    @Test
    void defaultPaytableKeepsAHouseEdge() {
        SlotPaytable table = SlotPaytable.defaults();
        double fixed = table.fixedReturn();
        assertTrue(fixed > 0.8 && fixed < 0.95, "fixed return " + fixed);
        assertTrue(fixed + 0.03 < 1.0, "even with the jackpot contribution the house wins");
    }

    @Test
    void reelsAlwaysShowTheDrawnOutcome() {
        SlotEngine engine = new SlotEngine(SlotPaytable.defaults(), 0.4, new SplittableRandom(42));
        Map<SlotOutcome, Integer> counts = new EnumMap<>(SlotOutcome.class);
        int nearMisses = 0;
        for (int pull = 0; pull < 200_000; pull++) {
            SlotSpin spin = engine.spin();
            counts.merge(spin.outcome(), 1, Integer::sum);
            List<SlotSymbol> line = spin.payline();
            boolean triple = line.get(0) == line.get(1) && line.get(1) == line.get(2);
            if (spin.outcome() == SlotOutcome.LOSE) {
                assertFalse(triple, "a loss never shows three of a kind on the payline");
            } else {
                assertTrue(triple);
                assertEquals(spin.outcome().symbol(), line.get(0));
            }
            if (spin.nearMiss()) {
                nearMisses++;
                assertEquals(SlotOutcome.LOSE, spin.outcome());
                SlotSymbol teaser = line.get(0);
                assertEquals(teaser, line.get(1));
                List<SlotSymbol> third = spin.window().get(2);
                assertTrue(third.get(0) == teaser || third.get(2) == teaser, "the missing symbol sits just off the line");
            }
        }
        double emeralds = counts.get(SlotOutcome.EMERALDS) / 200_000.0;
        assertEquals(0.13, emeralds, 0.005);
        assertTrue(nearMisses > 40_000, "near misses " + nearMisses);
    }

    @Test
    void rejectsImpossiblePaytables() {
        Map<SlotOutcome, Double> chances = new EnumMap<>(SlotOutcome.class);
        chances.put(SlotOutcome.EMERALDS, 0.7);
        chances.put(SlotOutcome.GOLD, 0.4);
        assertThrows(IllegalArgumentException.class, () -> new SlotPaytable(chances, Map.of()));
    }

    @Test
    void jackpotGrowsWithBetsAndResetsOnClaim() {
        JackpotPool pool = new JackpotPool(5000, 0.03, 0);
        assertEquals(5000, pool.value());
        pool.feed(1000);
        assertEquals(5030, pool.value());
        assertEquals(5030, pool.claim());
        assertEquals(5000, pool.value());
        assertEquals(7000, new JackpotPool(5000, 0.03, 7000).value(), "a saved pool survives restarts");
    }
}
