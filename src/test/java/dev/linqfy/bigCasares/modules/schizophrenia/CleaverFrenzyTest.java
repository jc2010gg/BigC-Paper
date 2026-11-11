package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleaverFrenzyTest {

    @Test
    void cameraIsDraggedTowardsTheTargetAndEventuallyLocksOn() {
        Vector want = new Vector(10, 0, 0);
        Vector look = new Vector(0, 0, 1);
        double previous = look.angle(want);
        for (int i = 0; i < 20; i++) {
            look = CleaverFrenzy.turnTowards(look, want, CleaverFrenzy.TURN_RATE);
            assertEquals(1.0, look.length(), 1.0E-9);
            double angle = look.angle(want);
            assertTrue(angle < previous || angle < 1.0E-6);
            previous = angle;
        }
        assertTrue(previous < Math.toRadians(1));
    }

    @Test
    void survivesDegenerateDirections() {
        Vector want = new Vector(0, 0, -1);
        Vector look = new Vector(0, 0, 1);
        for (int i = 0; i < 20; i++) {
            look = CleaverFrenzy.turnTowards(look, want, CleaverFrenzy.TURN_RATE);
            assertEquals(1.0, look.length(), 1.0E-9);
        }
        assertTrue(look.angle(want) < Math.toRadians(1), "a target straight behind is still reached");
        Vector still = CleaverFrenzy.turnTowards(new Vector(1, 0, 0), new Vector(), 0.5);
        assertEquals(new Vector(1, 0, 0), still);
        Vector fromNothing = CleaverFrenzy.turnTowards(new Vector(), want, 0.5);
        assertEquals(want, fromNothing);
    }
}
