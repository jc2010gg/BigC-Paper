package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeEngine;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeApi;
import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class TideframeEmbeddedRuntimeTest {
    @Test
    void engineIsOwnedByBigCasaresNotASecondJavaPlugin() {
        assertFalse(JavaPlugin.class.isAssignableFrom(TideframeEngine.class));
    }

    @Test
    void engineConfigurationAndArtAreBundledWithoutReplacingBigCasaresConfiguration() {
        assertNotNull(getClass().getResource("/tideframe/engine/config.yml"));
        assertNotNull(getClass().getResource("/tideframe/engine/boss-face.png"));
        assertNotNull(getClass().getResource("/tideframe/engine/item-icons.tsv"));
        assertNotNull(getClass().getResource("/config.yml"));
    }

    @Test
    void onePluginDescriptorAndSourceBuiltApiNeedNoExternalTideframeJar() throws Exception {
        try (var input = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(input);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            assertEquals("dev.linqfy.bigCasares.BigCasares", yaml.getString("main"));
            assertFalse(yaml.getStringList("softdepend").contains("Tideframe"));
            assertNotNull(yaml.getConfigurationSection("commands.tideframe"));
        }
        assertEquals(BigCasares.class.getProtectionDomain().getCodeSource().getLocation(),
            TideframeApi.class.getProtectionDomain().getCodeSource().getLocation());
    }
}
