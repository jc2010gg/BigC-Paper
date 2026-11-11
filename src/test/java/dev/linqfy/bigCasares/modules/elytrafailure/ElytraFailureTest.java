package dev.linqfy.bigCasares.modules.elytrafailure;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraFailureTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("elytra-failure", new ElytraFailureModule(null).getId());
    }

    @Test
    void failuresAreRareAndNeverOnTakeOff() {
        ElytraFailurePolicy policy = new ElytraFailurePolicy(ElytraFailureSettings.defaults());

        assertFalse(policy.shouldFail(20L, 0.0), "no failure before min-flight-ticks");
        assertTrue(policy.shouldFail(100L, 0.001));
        assertFalse(policy.shouldFail(100L, 0.01));
    }

    @Test
    void brokenWingDamagesButNeverDestroysTheElytra() {
        ElytraFailurePolicy policy = new ElytraFailurePolicy(ElytraFailureSettings.defaults());

        assertEquals(108, policy.damageAfterFailure(0, 432));
        assertEquals(431, policy.damageAfterFailure(400, 432));
        assertEquals(0, policy.damageAfterFailure(0, 1));
    }

    @Test
    void driftPushesTowardsTheBrokenSide() {
        // Facing north (negative Z): right is east (+X), left is west (-X).
        assertVector(1.0, 0.0, ElytraFailurePolicy.sideways(0.0, -2.0, ElytraWing.RIGHT));
        assertVector(-1.0, 0.0, ElytraFailurePolicy.sideways(0.0, -2.0, ElytraWing.LEFT));
        // Facing east (+X): right is south (+Z).
        assertVector(0.0, 1.0, ElytraFailurePolicy.sideways(3.0, 0.0, ElytraWing.RIGHT));
        assertVector(0.0, 0.0, ElytraFailurePolicy.sideways(0.0, 0.0, ElytraWing.RIGHT));
    }

    @Test
    void wingsParseAndRoll() {
        assertEquals(ElytraWing.LEFT, ElytraWing.fromRoll(0.2));
        assertEquals(ElytraWing.RIGHT, ElytraWing.fromRoll(0.7));
        assertEquals(ElytraWing.RIGHT, ElytraWing.parse("right"));
        assertNull(ElytraWing.parse("middle"));
        assertNull(ElytraWing.parse(null));
    }

    @Test
    void settingsLoadAndRejectNonsense() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("elytra-failure.chance-per-check", 0.01);
        yaml.set("elytra-failure.drift-ticks", 10);

        ElytraFailureSettings settings = ElytraFailureSettings.load(yaml);
        assertEquals(0.01, settings.chancePerCheck());
        assertEquals(10, settings.driftTicks());

        yaml.set("elytra-failure.chance-per-check", 3.0);
        assertEquals(ElytraFailureSettings.defaults(), ElytraFailureSettings.load(yaml));
        assertThrows(IllegalArgumentException.class,
            () -> new ElytraFailureSettings(0L, 0.1, 0L, 0, 0.0, 0.0));
    }

    private static void assertVector(double x, double z, double[] actual) {
        assertEquals(x, actual[0], 1.0E-9);
        assertEquals(z, actual[1], 1.0E-9);
    }
}
