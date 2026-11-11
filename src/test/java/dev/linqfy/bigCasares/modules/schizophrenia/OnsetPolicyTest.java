package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnsetPolicyTest {

    private static final SchizophreniaSettings.Natural NATURAL = SchizophreniaSettings.Natural.defaults();
    private static final long THREE_DAYS = 3 * OnsetPolicy.TICKS_PER_DAY;
    private static final long HOUR = 3_600_000L;

    private static OnsetPolicy.Conditions lonely(int light, double y, boolean night, long sinceRest, double health) {
        return new OnsetPolicy.Conditions(true, light, y, night, sinceRest, health, false);
    }

    @Test
    void stressOnlyBuildsAloneInTheDarkAndDrainsOtherwise() {
        assertEquals(1, OnsetPolicy.stressDelta(lonely(0, 64, false, 0, 20), NATURAL));
        assertEquals(1, OnsetPolicy.stressDelta(lonely(15, -20, false, 0, 20), NATURAL));
        assertEquals(-2, OnsetPolicy.stressDelta(lonely(15, 64, true, THREE_DAYS, 2), NATURAL));
        assertEquals(-5, OnsetPolicy.stressDelta(new OnsetPolicy.Conditions(false, 0, -20, true, THREE_DAYS, 2, false), NATURAL));
        assertEquals(-5, OnsetPolicy.stressDelta(new OnsetPolicy.Conditions(true, 0, -20, true, THREE_DAYS, 2, true), NATURAL));
    }

    @Test
    void worstCaseStressesSixTimesFaster() {
        assertEquals(6, OnsetPolicy.stressDelta(lonely(0, -30, true, THREE_DAYS, 4), NATURAL));
    }

    @Test
    void stressIsClamped() {
        assertEquals(0, OnsetPolicy.applyStress(1, -5, NATURAL));
        assertEquals(NATURAL.stressThreshold() * 2, OnsetPolicy.applyStress(NATURAL.stressThreshold() * 2, 6, NATURAL));
    }

    @Test
    void takesTimeEvenInTheWorstConditions() {
        int stress = 0;
        int minutes = 0;
        while (stress < NATURAL.stressThreshold()) {
            stress = OnsetPolicy.applyStress(stress, OnsetPolicy.stressDelta(lonely(0, -30, true, THREE_DAYS, 4), NATURAL), NATURAL);
            minutes++;
        }
        assertTrue(minutes >= 6, "never sooner than a few minutes of misery");
        int casual = 0;
        stress = 0;
        while (stress < NATURAL.stressThreshold()) {
            stress = OnsetPolicy.applyStress(stress, OnsetPolicy.stressDelta(lonely(0, 30, false, THREE_DAYS, 20), NATURAL), NATURAL);
            casual++;
        }
        assertTrue(casual >= 13, "a sleepless miner in the dark takes a quarter of an hour");
    }

    @Test
    void needsInsomniaAndRespectsEveryCooldown() {
        int full = NATURAL.stressThreshold();
        long now = 1_000 * HOUR;
        long longAgo = Long.MIN_VALUE / 2;
        assertTrue(OnsetPolicy.mayStart(full, THREE_DAYS, now, null, longAgo, 0, NATURAL));
        assertFalse(OnsetPolicy.mayStart(full - 1, THREE_DAYS, now, null, longAgo, 0, NATURAL), "not stressed enough");
        assertFalse(OnsetPolicy.mayStart(full, THREE_DAYS - 1, now, null, longAgo, 0, NATURAL), "slept recently");
        assertFalse(OnsetPolicy.mayStart(full, THREE_DAYS, now, null, longAgo, 1, NATURAL), "someone else is already sick");
        assertFalse(OnsetPolicy.mayStart(full, THREE_DAYS, now, null, now - HOUR, 0, NATURAL), "server cooldown");
        assertTrue(OnsetPolicy.mayStart(full, THREE_DAYS, now, null, now - 3 * HOUR, 0, NATURAL));
        assertFalse(OnsetPolicy.mayStart(full, THREE_DAYS, now, now - 47 * HOUR, longAgo, 0, NATURAL), "player cooldown");
        assertTrue(OnsetPolicy.mayStart(full, THREE_DAYS, now, now - 48 * HOUR, longAgo, 0, NATURAL));
    }
}
