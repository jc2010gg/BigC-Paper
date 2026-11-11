package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorrorPacingTest {

    @Test
    void cyclesCalmDreadScareSilence() {
        SplittableRandom random = new SplittableRandom(3);
        HorrorPacing pacing = new HorrorPacing(0, 1, random);
        List<HorrorPacing.Phase> seen = new ArrayList<>();
        seen.add(pacing.phase());
        for (long tick = 0; tick < 20 * 60 * 10 && seen.size() < 9; tick += 2) {
            if (pacing.advance(tick, 1, random)) {
                seen.add(pacing.phase());
            }
        }
        assertEquals(List.of(
            HorrorPacing.Phase.CALM, HorrorPacing.Phase.DREAD, HorrorPacing.Phase.SCARE, HorrorPacing.Phase.AFTERMATH,
            HorrorPacing.Phase.CALM, HorrorPacing.Phase.DREAD, HorrorPacing.Phase.SCARE, HorrorPacing.Phase.AFTERMATH,
            HorrorPacing.Phase.CALM), seen);
    }

    @Test
    void tensionBuildsThroughDreadAndPeaksAtTheScare() {
        SplittableRandom random = new SplittableRandom(5);
        HorrorPacing pacing = new HorrorPacing(0, 1, random);
        pacing.forceDread(0, 1, random);
        double early = pacing.tension(1);
        double late = pacing.tension(HorrorPacing.durationTicks(HorrorPacing.Phase.DREAD, 1, 0.0) - 1);
        assertTrue(late > early);
        long tick = 0;
        while (pacing.phase() != HorrorPacing.Phase.SCARE) {
            tick += 2;
            pacing.advance(tick, 1, random);
        }
        assertEquals(1.0, pacing.tension(tick));
        assertFalse(pacing.advance(tick, 1, random));
    }

    @Test
    void worseEpisodesLeaveLessRoomToBreathe() {
        assertTrue(HorrorPacing.durationTicks(HorrorPacing.Phase.CALM, 5, 0.5)
            < HorrorPacing.durationTicks(HorrorPacing.Phase.CALM, 1, 0.5));
        assertTrue(HorrorPacing.durationTicks(HorrorPacing.Phase.DREAD, 5, 0.5)
            < HorrorPacing.durationTicks(HorrorPacing.Phase.DREAD, 1, 0.5));
        for (HorrorPacing.Phase phase : HorrorPacing.Phase.values()) {
            assertTrue(HorrorPacing.durationTicks(phase, 5, 0.0) >= 20);
        }
        // Even late on, the quiet lasts far longer than the scare.
        assertTrue(HorrorPacing.durationTicks(HorrorPacing.Phase.CALM, 5, 0.0)
            > 2 * HorrorPacing.durationTicks(HorrorPacing.Phase.SCARE, 5, 0.0));
        assertTrue(HorrorPacing.durationTicks(HorrorPacing.Phase.CALM, 1, 0.0) >= 50 * 20);
    }
}
