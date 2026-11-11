package dev.linqfy.bigCasares.modules.mobscaling;

import dev.linqfy.bigCasares.modules.mobscaling.SpecialEnemyMath.ZombieVariant;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialEnemyMathTest {

    @Test
    void zombieMutationsAreSplitBetweenFourVariants() {
        assertEquals(ZombieVariant.SPEED_BABY, SpecialEnemyMath.pickZombieVariant(true, true, 0));
        assertEquals(ZombieVariant.TOXIC, SpecialEnemyMath.pickZombieVariant(true, true, 1));
        assertEquals(ZombieVariant.RABBIT_LANCER, SpecialEnemyMath.pickZombieVariant(true, true, 2));
        assertEquals(ZombieVariant.PEARL_THROWER, SpecialEnemyMath.pickZombieVariant(true, true, 3));
    }

    @Test
    void disabledSpecialZombiesFallBackToTheOriginalMutations() {
        assertEquals(ZombieVariant.SPEED_BABY, SpecialEnemyMath.pickZombieVariant(false, false, 2));
        assertEquals(ZombieVariant.TOXIC, SpecialEnemyMath.pickZombieVariant(false, false, 3));
    }

    @Test
    void nuclearCreeperNeedsAThunderstormAndTheTinyRoll() {
        double chance = SpecialEnemySettings.DEFAULT_NUCLEAR_CHANCE;
        assertEquals(0.0000001, chance, 0.0); // 0.00001 %
        assertTrue(SpecialEnemyMath.rollNuclear(true, chance, 0.0));
        assertFalse(SpecialEnemyMath.rollNuclear(true, chance, 0.000001));
        assertFalse(SpecialEnemyMath.rollNuclear(false, chance, 0.0));
        assertFalse(SpecialEnemyMath.rollNuclear(true, 0.0, 0.0));
    }

    @Test
    void onlyAGrowingGapInsideTheRangeCountsAsFleeing() {
        assertTrue(SpecialEnemyMath.isFleeing(8.0, 9.0, 6.0, 24.0));
        assertFalse(SpecialEnemyMath.isFleeing(9.0, 8.0, 6.0, 24.0), "approaching");
        assertFalse(SpecialEnemyMath.isFleeing(8.0, 8.1, 6.0, 24.0), "jitter");
        assertFalse(SpecialEnemyMath.isFleeing(3.0, 4.0, 6.0, 24.0), "too close");
        assertFalse(SpecialEnemyMath.isFleeing(30.0, 31.0, 6.0, 24.0), "too far");
        assertFalse(SpecialEnemyMath.isFleeing(Double.NaN, 10.0, 6.0, 24.0), "no history yet");
    }

    @Test
    void interceptLeadsAlongThePlayersMovement() {
        Vector point = SpecialEnemyMath.interceptPoint(new Vector(0, 64, 0), new Vector(10, 64, 0),
                new Vector(0, 0.5, 2), 4.0);
        assertEquals(10.0, point.getX(), 1.0E-9);
        assertEquals(64.0, point.getY(), 1.0E-9);
        assertEquals(4.0, point.getZ(), 1.0E-9);
    }

    @Test
    void standingPlayerIsCutOffDirectlyBehind() {
        Vector point = SpecialEnemyMath.interceptPoint(new Vector(0, 64, 0), new Vector(10, 64, 0), new Vector(), 4.0);
        assertEquals(14.0, point.getX(), 1.0E-9);
        assertEquals(0.0, point.getZ(), 1.0E-9);
    }

    @Test
    void launchVelocityLandsNearTheTarget() {
        Vector from = new Vector(0, 65.6, 0);
        for (Vector to : new Vector[]{new Vector(12, 64, 3), new Vector(20, 64, -6), new Vector(6, 68, 2)}) {
            Vector velocity = SpecialEnemyMath.pearlLaunchVelocity(from, to);
            assertTrue(simulatedMiss(from, velocity, to) < 1.5, "missed " + to);
        }
    }

    /** Closest approach of a vanilla-like pearl (move, drag, gravity) to the target. */
    private static double simulatedMiss(Vector from, Vector velocity, Vector to) {
        Vector pos = from.clone();
        Vector vel = velocity.clone();
        double best = Double.MAX_VALUE;
        for (int tick = 0; tick < 200; tick++) {
            pos.add(vel);
            vel.multiply(SpecialEnemyMath.PEARL_DRAG);
            vel.setY(vel.getY() - SpecialEnemyMath.PEARL_GRAVITY);
            best = Math.min(best, pos.distance(to));
        }
        return best;
    }
}
