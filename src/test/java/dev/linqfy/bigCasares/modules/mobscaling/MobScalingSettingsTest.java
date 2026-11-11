package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobScalingSettingsTest {

    private static MobScalingSettings settings(double maxMultiplier) {
        return new MobScalingSettings(true, LocalDate.of(2026, 7, 17), 0.05, 1.1, 1.0, 0.0, 0.0, 0.0, maxMultiplier);
    }

    @Test
    void earlyDaysFollowTheCurve() {
        assertEquals(1.05, settings(3.0).multiplier(0), 1.0E-9);
        assertEquals(0.05 * Math.pow(1.1, 10) + 1.0, settings(3.0).multiplier(10), 1.0E-9);
        assertEquals(1.05, settings(3.0).multiplier(-5), 1.0E-9);
    }

    @Test
    void monthsInTheMultiplierStaysCapped() {
        // Day 115 used to give ~2879x health and damage: a baby zombie hit for thousands.
        assertEquals(3.0, settings(3.0).multiplier(115), 1.0E-9);
        assertEquals(3.0, settings(3.0).multiplier(100_000), 1.0E-9);
        assertTrue(settings(0).multiplier(115) > 2000);
    }

    @Test
    void capIsReadFromConfigAndDefaultsToThree() throws Exception {
        assertEquals(MobScalingSettings.DEFAULT_MAX_MULTIPLIER,
            MobScalingSettings.fromConfig(new YamlConfiguration()).maxMultiplier());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("mob-scaling:\n  max-multiplier: 1.5\n");
        assertEquals(1.5, MobScalingSettings.fromConfig(yaml).maxMultiplier());
    }
}
