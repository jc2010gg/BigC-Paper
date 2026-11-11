package dev.linqfy.bigCasares.modules.keepinventorytotem;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeepInventoryTotemTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void exposesStableModuleId() {
        assertEquals("keep-inventory-totem", new KeepInventoryTotemModule(null).getId());
        assertFalse(new KeepInventoryTotemModule(null).protectsDeath(java.util.UUID.randomUUID()));
    }

    @Test
    void defaultsAreExpensiveToAbuse() {
        KeepInventoryTotemSettings settings = KeepInventoryTotemSettings.load(new YamlConfiguration());

        assertEquals(4L * 3600L, settings.cooldownSeconds());
        assertEquals(1, settings.maxCarried());
        assertTrue(settings.keepExperience());
    }

    @Test
    void invalidSettingsFallBackToDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("keep-inventory-totem.cooldown-seconds", -5);
        yaml.set("keep-inventory-totem.max-carried", 0);
        yaml.set("keep-inventory-totem.keep-experience", false);

        KeepInventoryTotemSettings settings = KeepInventoryTotemSettings.load(yaml);

        assertEquals(KeepInventoryTotemSettings.DEFAULT_COOLDOWN_SECONDS, settings.cooldownSeconds());
        assertEquals(1, settings.maxCarried());
        assertFalse(settings.keepExperience());
        assertThrows(IllegalArgumentException.class, () -> new KeepInventoryTotemSettings(10, true, 0));
    }

    @Test
    void cooldownCountsDownFromLastUse() {
        assertEquals(0L, KeepInventoryTotemCooldown.remainingMillis(null, 10 * HOUR, 4 * HOUR));
        assertEquals(3 * HOUR, KeepInventoryTotemCooldown.remainingMillis(HOUR, 2 * HOUR, 4 * HOUR));
        assertEquals(0L, KeepInventoryTotemCooldown.remainingMillis(HOUR, 5 * HOUR, 4 * HOUR));
        assertEquals(4 * HOUR, KeepInventoryTotemCooldown.remainingMillis(5 * HOUR, HOUR, 4 * HOUR));
        assertEquals(0L, KeepInventoryTotemCooldown.remainingMillis(HOUR, HOUR, 0L));
    }

    @Test
    void formatsRemainingTime() {
        assertEquals("3h 05m", KeepInventoryTotemCooldown.format(3 * HOUR + 5 * 60_000L));
        assertEquals("12m 40s", KeepInventoryTotemCooldown.format(12 * 60_000L + 40_000L));
        assertEquals("1s", KeepInventoryTotemCooldown.format(1L));
    }

    @Test
    void carryLimitKeepsOnlyTheFirstTotem() {
        String[] contents = {null, "dirt", "totem", null, "totem", "totem"};

        assertEquals(3, KeepInventoryTotemCarryLimit.count(contents, "totem"::equals, ignored -> 1));
        assertEquals(2, KeepInventoryTotemCarryLimit.firstSlot(contents, "totem"::equals));
        List<int[]> excess = KeepInventoryTotemCarryLimit.excess(contents, "totem"::equals, ignored -> 1, 1);
        assertEquals(2, excess.size());
        assertArrayEquals(new int[] {4, 1}, excess.get(0));
        assertArrayEquals(new int[] {5, 1}, excess.get(1));
    }

    @Test
    void carryLimitSplitsOversizedStacks() {
        Integer[] amounts = {3};

        List<int[]> excess = KeepInventoryTotemCarryLimit.excess(amounts, ignored -> true, Integer::intValue, 1);

        assertEquals(1, excess.size());
        assertArrayEquals(new int[] {0, 2}, excess.get(0));
        assertTrue(KeepInventoryTotemCarryLimit.excess(new Integer[] {1, null}, a -> true, Integer::intValue, 1)
            .isEmpty());
        assertEquals(-1, KeepInventoryTotemCarryLimit.firstSlot(new String[] {"dirt"}, "totem"::equals));
    }
}
