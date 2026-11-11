package dev.linqfy.bigCasares.modules.casino.roulette;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteTest {

    @Test
    void wheelHasEveryNumberOnceAndEighteenOfEachColour() {
        assertEquals(37, new HashSet<>(RoulettePockets.WHEEL_ORDER).size());
        long red = RoulettePockets.WHEEL_ORDER.stream().filter(n -> RoulettePockets.color(n) == RoulettePockets.Color.RED).count();
        long black = RoulettePockets.WHEEL_ORDER.stream().filter(n -> RoulettePockets.color(n) == RoulettePockets.Color.BLACK).count();
        assertEquals(18, red);
        assertEquals(18, black);
        assertEquals(RoulettePockets.Color.GREEN, RoulettePockets.color(0));
    }

    @Test
    void payoutsFollowTheTable() {
        assertEquals(3600, RouletteBet.straight(17).payout(100, 17));
        assertEquals(0, RouletteBet.straight(17).payout(100, 18));
        assertEquals(3600, RouletteBet.straight(0).payout(100, 0), "green 0 pays 35 to 1");
        assertEquals(200, RouletteBet.of(RouletteBet.Kind.RED).payout(100, 1));
        assertEquals(0, RouletteBet.of(RouletteBet.Kind.BLACK).payout(100, 1));
        assertEquals(300, new RouletteBet(RouletteBet.Kind.DOZEN, 3).payout(100, 36));
        assertEquals(300, new RouletteBet(RouletteBet.Kind.COLUMN, 1).payout(100, 34));
        assertEquals(200, RouletteBet.of(RouletteBet.Kind.EVEN).payout(100, 36));
        assertEquals(200, RouletteBet.of(RouletteBet.Kind.LOW).payout(100, 18));
    }

    @Test
    void zeroLosesEveryOutsideBet() {
        for (RouletteBet.Kind kind : RouletteBet.Kind.values()) {
            if (kind == RouletteBet.Kind.STRAIGHT) {
                continue;
            }
            RouletteBet bet = kind == RouletteBet.Kind.DOZEN || kind == RouletteBet.Kind.COLUMN
                ? new RouletteBet(kind, 1) : RouletteBet.of(kind);
            assertFalse(bet.wins(0), kind + " must lose on zero");
        }
    }

    @Test
    void houseEdgeIsOneThirtySeventhOnEveryBet() {
        for (RouletteBet bet : new RouletteBet[]{RouletteBet.straight(5), RouletteBet.of(RouletteBet.Kind.RED),
            new RouletteBet(RouletteBet.Kind.DOZEN, 2), RouletteBet.of(RouletteBet.Kind.ODD)}) {
            long returned = 0;
            for (int number = 0; number <= 36; number++) {
                returned += bet.payout(1, number);
            }
            assertEquals(36, returned, bet.key());
        }
    }

    @Test
    void betKeysRoundTripAndValidate() {
        RouletteBet dozen = new RouletteBet(RouletteBet.Kind.DOZEN, 2);
        assertEquals(dozen, RouletteBet.parseKey(dozen.key()));
        assertEquals(RouletteBet.of(RouletteBet.Kind.RED), RouletteBet.parseKey("red"));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.straight(37));
        assertThrows(IllegalArgumentException.class, () -> new RouletteBet(RouletteBet.Kind.DOZEN, 4));
    }

    @Test
    void everySpinStopsOnItsResult() {
        for (int number = 0; number <= 36; number++) {
            for (double start : new double[]{0, 1.3, 4.9}) {
                SpinPlan plan = new SpinPlan(number, 220, start, start * 2.1, 2, 7);
                assertEquals(number, plan.pocketUnderBall(plan.durationTicks()), "number " + number);
                assertTrue(plan.finished(220));
                assertEquals(0.0, plan.ballRadiusFactor(220));
                assertEquals(1.0, plan.ballRadiusFactor(0));
            }
        }
    }

    @Test
    void ratchetClicksSlowDownTowardsTheEnd() {
        SpinPlan plan = new SpinPlan(17, 220, 0, 0, 2, 7);
        int early = crossings(plan, 0, 40);
        int late = crossings(plan, 180, 220);
        assertTrue(early > late * 3, "early " + early + " late " + late);
    }

    private static int crossings(SpinPlan plan, int from, int to) {
        Set<Integer> seen = new HashSet<>();
        int count = 0;
        int last = plan.pocketUnderBall(from);
        for (int tick = from + 1; tick <= to; tick++) {
            int pocket = plan.pocketUnderBall(tick);
            if (pocket != last) {
                count++;
                last = pocket;
            }
            seen.add(pocket);
        }
        return count;
    }
}
