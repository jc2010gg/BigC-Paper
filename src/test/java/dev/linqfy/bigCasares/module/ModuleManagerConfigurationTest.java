package dev.linqfy.bigCasares.module;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerConfigurationTest {

    @Test
    void readsFrameworkV2NestedEnabledFlag() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("modules.resource-pack-system.enabled", false);

        assertFalse(ModuleManager.isEnabledInConfig(config, "resource-pack-system"));
    }

    @Test
    void migratesLegacyShopToggleToEntityShopId() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("modules.shop-system", true);

        assertTrue(ModuleManager.isEnabledInConfig(config, "entity-shop-system"));
    }

    @Test
    void explicitV2ShopFlagOverridesLegacyToggle() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("modules.shop-system", true);
        config.set("modules.entity-shop-system.enabled", false);

        assertFalse(ModuleManager.isEnabledInConfig(config, "entity-shop-system"));
    }
}
