package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpisodeMathTest {

    private static final long MINUTE = 60_000L;

    @Test
    void intensityEscalatesAndCapsAtFive() {
        assertEquals(1, EpisodeMath.intensity(1, 0, 3.0));
        assertEquals(1, EpisodeMath.intensity(1, 2 * MINUTE, 3.0));
        assertEquals(2, EpisodeMath.intensity(1, 3 * MINUTE, 3.0));
        assertEquals(4, EpisodeMath.intensity(2, 6 * MINUTE, 3.0));
        assertEquals(5, EpisodeMath.intensity(1, 500 * MINUTE, 3.0));
        assertEquals(5, EpisodeMath.intensity(9, 0, 3.0));
        assertEquals(1, EpisodeMath.intensity(-3, -10, 3.0));
    }

    @Test
    void beatsComeFasterAsTensionBuilds() {
        assertEquals(14.0, EpisodeMath.beatSeconds(0.0, 14, 3, 0.5), 1.0E-9);
        assertEquals(1.0, EpisodeMath.beatSeconds(1.0, 7, 1, 0.5), 1.0E-9);
        assertEquals(3.0, EpisodeMath.beatSeconds(1.0, 14, 3, 0.5), 1.0E-9);
        assertTrue(EpisodeMath.beatSeconds(0.5, 14, 3, 0.0) < EpisodeMath.beatSeconds(0.5, 14, 3, 1.0));
        assertTrue(EpisodeMath.beatSeconds(9.0, 1, 1, 0.0) >= 1.0);
    }

    @Test
    void slowBurnNeverPilesHallucinationsOrScaresUp() {
        assertEquals(1, EpisodeMath.stackedBeats(0.0, 1));
        assertEquals(1, EpisodeMath.stackedBeats(1.0, 3));
        assertEquals(1, EpisodeMath.stackedBeats(0.5, 5));
        assertEquals(2, EpisodeMath.stackedBeats(1.0, 4));
        for (int intensity = 1; intensity <= 5; intensity++) {
            assertEquals(1, EpisodeMath.scaresInCombo(intensity));
        }
    }

    @Test
    void sanityDrainsWithIntensityAndTension() {
        assertEquals(1.0f, EpisodeMath.sanity(1, 0.0), 1.0E-6f);
        assertTrue(EpisodeMath.sanity(3, 0.0) < EpisodeMath.sanity(1, 0.0));
        assertTrue(EpisodeMath.sanity(3, 1.0) < EpisodeMath.sanity(3, 0.2));
        assertTrue(EpisodeMath.sanity(5, 1.0) > 0f);
    }

    @Test
    void zeroMaxMinutesMeansOnlyTheLobotomyEndsIt() {
        assertFalse(EpisodeMath.expired(10_000 * MINUTE, 0));
        assertFalse(EpisodeMath.expired(24 * MINUTE, 25));
        assertTrue(EpisodeMath.expired(25 * MINUTE, 25));
    }

}
