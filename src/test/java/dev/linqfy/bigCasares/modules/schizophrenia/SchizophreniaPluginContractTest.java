package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchizophreniaPluginContractTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("schizophrenia", new SchizophreniaModule(null).getId());
    }

    @Test
    void pluginDeclaresCommandPermissionAndRegistration() throws Exception {
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String main = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/BigCasares.java"));

        assertTrue(pluginYml.contains("\n  esquizo:\n"));
        assertTrue(pluginYml.contains("permission: bigcasares.schizophrenia.admin"));
        assertTrue(pluginYml.contains("\n  bigcasares.schizophrenia.admin:\n"));
        assertTrue(main.contains("moduleManager.register(new SchizophreniaModule(this))"));
    }

    @Test
    void lobotomizerIsASpear() throws Exception {
        String item = Files.readString(Path.of(
            "src/main/java/dev/linqfy/bigCasares/modules/schizophrenia/LobotomizerItem.java"));
        assertTrue(item.contains("new ItemStack(Material.IRON_SPEAR)"));
        assertEquals(3, LobotomizerItem.SHAPE.size());
        LobotomizerItem.SHAPE.forEach(row -> assertEquals(3, row.length()));
        assertEquals("lobotomizador", LobotomizerItem.ITEM_ID);
    }

    @Test
    void cleaverIsAnUnbreakableAxe() throws Exception {
        String item = Files.readString(Path.of(
            "src/main/java/dev/linqfy/bigCasares/modules/schizophrenia/CleaverItem.java"));
        assertTrue(item.contains("new ItemStack(Material.IRON_AXE)"));
        assertTrue(item.contains("setUnbreakable(true)"));
        assertEquals("cuchilla", CleaverItem.ITEM_ID);
    }
}
