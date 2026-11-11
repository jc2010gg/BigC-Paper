package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpecialEnemySettingsTest {

    @Test
    void missingSectionUsesTheDefaults() {
        assertEquals(SpecialEnemySettings.defaults(), SpecialEnemySettings.fromConfig(new YamlConfiguration()));
    }

    @Test
    void configOverridesAndClampsValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mob-scaling.special-enemies.rabbit-lancer.enabled", false);
        config.set("mob-scaling.special-enemies.rabbit-lancer.spear-drop-chance", 4.0);
        config.set("mob-scaling.special-enemies.pearl-zombie.cooldown-seconds", 0);
        config.set("mob-scaling.special-enemies.nuclear-creeper.chance", -1.0);

        SpecialEnemySettings settings = SpecialEnemySettings.fromConfig(config);

        assertFalse(settings.rabbitLancerEnabled());
        assertEquals(1.0F, settings.spearDropChance());
        assertEquals(1, settings.pearlCooldownSeconds());
        assertEquals(0.0, settings.nuclearChance());
    }
}
