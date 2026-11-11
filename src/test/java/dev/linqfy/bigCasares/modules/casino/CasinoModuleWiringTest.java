package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoModuleWiringTest {

    @Test
    void exposesAStableIdAndIsANoOpWithoutAPlugin() {
        CasinoModule module = new CasinoModule(null);
        assertEquals("casino", module.getId());
        assertDoesNotThrow(() -> module.onEnable(new RuntimeRegistrationScope()));
        assertFalse(module.isOpen());
        assertDoesNotThrow(module::onDisable);
    }

    @Test
    void moduleIsRegisteredDeclaredAndEnabledByDefault() throws Exception {
        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/casino/CasinoModule", "<init>"));
        assertEquals(CasinoModule.class, BigCasares.class.getMethod("getCasinoModule").getReturnType());

        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        assertTrue(plugin.isConfigurationSection("commands/casino"));
        assertEquals("op", plugin.getString("permissions/bigcasares.casino.admin/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.*/children/bigcasares.casino.admin"));
        assertTrue(plugin.getStringList("softdepend").contains("Vault"), "the casino needs Vault but must not hard-depend on it");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.getBoolean("modules.casino.enabled"));
    }
}
