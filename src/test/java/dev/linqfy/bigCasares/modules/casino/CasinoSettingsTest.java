package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.slots.SlotOutcome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoSettingsTest {

    @Test
    void shippedConfigMatchesTheBuiltInDefaults() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        CasinoSettings shipped = CasinoSettings.load(config);
        CasinoSettings defaults = CasinoSettings.defaults();
        assertEquals(defaults.chips(), shipped.chips());
        assertEquals(defaults.blackjack(), shipped.blackjack());
        assertEquals(defaults.poker(), shipped.poker());
        assertEquals(defaults.roulette(), shipped.roulette());
        assertEquals(defaults.pit(), shipped.pit());
        assertEquals(defaults.drinkPrices(), shipped.drinkPrices());
        assertEquals(defaults.loans().offers(), shipped.loans().offers());
        for (SlotOutcome outcome : SlotOutcome.values()) {
            assertEquals(defaults.slots().paytable().chance(outcome), shipped.slots().paytable().chance(outcome), 1e-12);
            assertEquals(defaults.slots().paytable().multiplier(outcome), shipped.slots().paytable().multiplier(outcome));
        }
        assertTrue(config.getBoolean("modules.casino.enabled"));
    }

    @Test
    void invalidValuesAreRejectedOrClamped() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
            casino:
              poker: { small-blind: 50, big-blind: 20 }
            """);
        assertThrows(IllegalArgumentException.class, () -> CasinoSettings.load(config));

        YamlConfiguration clamped = new YamlConfiguration();
        clamped.loadFromString("""
            casino:
              roulette: { cycle-seconds: 5, spin-seconds: 100 }
              slots: { near-miss-chance: 4 }
            """);
        CasinoSettings settings = CasinoSettings.load(clamped);
        assertEquals(30, settings.roulette().cycleSeconds());
        assertEquals(15, settings.roulette().spinSeconds());
        assertEquals(1.0, settings.slots().nearMissChance());
    }
}
