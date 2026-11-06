package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightScreens;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BossFightConfigTest {
    private static String bundled() throws Exception {
        try (var in = TideframeResources.open("config.yml")) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test void bundledConfigDescribesExactlyTheDefaults(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, bundled());
        assertEquals(BossFightSettings.defaults(), BossFightConfig.load(config));
    }

    @Test void ownersCanRestyleEverythingAndScreensFollow(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, """
                boss-fight:
                  keys: [w, space]
                  lives: 3
                  time-start: 1.5
                  time-min: 0.5
                  colors:
                    key: "#202020"
                    key-letter: "#ffffff"
                    timer: "#ffcc00"
                  layout:
                    key-y: -0.9
                    key-size: 1.5
                  show:
                    lives: false
                    countdown: false
                  texts:
                    perfect: NICE!
                    lives: HEARTS
                  sounds:
                    miss: none
                """);
        var settings = BossFightConfig.load(config);
        assertEquals(List.of(KeyChallenge.Key.W, KeyChallenge.Key.SPACE), settings.keys());
        assertEquals(3, settings.lives()); assertEquals(30, settings.startTicks()); assertEquals(10, settings.minTicks());
        assertNull(settings.sound("miss"));
        var screens = BossFightScreens.build(settings);
        var key = screens.get(KeyChallenge.KEY_SCREEN);
        var face = key.elements().stream().filter(e -> e.id().equals("cap_face")).findFirst().orElseThrow();
        assertEquals("#202020", face.style().fillColor());
        assertTrue(face.height() > .7, "key-size 1.5 makes a bigger cap: " + face.height());
        assertEquals("#ffffff", key.elements().stream().filter(e -> e.id().equals("key")).findFirst().orElseThrow().style().textColor());
        assertEquals("#ffcc00", key.elements().stream().filter(e -> e.id().equals("timer")).findFirst().orElseThrow().style().textColor());
        assertTrue(key.elements().stream().noneMatch(e -> e.id().equals("lives")), "lives hidden");
        for (var screen : screens.values()) for (var e : screen.elements()) assertTrue(Math.abs(e.y()) <= 1, e.id() + " stays on the canvas");
        // The game uses the configured keys, lives and texts; no countdown.
        var game = new KeyChallenge(1, settings);
        assertEquals("", game.values().get("key"));
        for (int i = 0; i < 20 && game.phase() == KeyChallenge.Phase.COUNTDOWN; i++) game.tick();
        assertEquals(KeyChallenge.Phase.PROMPT, game.phase());
        assertTrue(settings.keys().contains(game.current()));
        game.press(game.current());
        assertTrue(List.of("NICE!", "GREAT!", "GOOD!").contains(game.values().get("feedback")));
        assertEquals("3/3", game.values().get("lives"));
    }

    @Test void invalidValuesAreRejectedWithTheKeyName() {
        var d = BossFightSettings.defaults();
        var bad = List.<Map<String, String>>of(Map.of("key", "white"), Map.of("bogus", "#ffffff"));
        for (var colors : bad) {
            var error = assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 40, 14, 30, 6, 2, colors, Map.of(), Map.of(), Map.of(), Map.of()));
            assertTrue(error.getMessage().contains("colors"), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(List.of(), 5, 40, 14, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 10, 20, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 40, 14, 30, 6, 2, Map.of(), Map.of("key-size", 3.0), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 40, 14, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of("perfect", "ÉPICO"), Map.of()));
    }

    @Test void menusAreReadOnlyInTheGameByDefaultAndOldConfigsGetThatSetting(@TempDir Path dir) throws Exception {
        var shipped = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.StringReader(bundled()));
        assertTrue(shipped.contains("editing.in-game"), "the setting is documented in the shipped config");
        assertFalse(shipped.getBoolean("editing.in-game", true), "read-only by default");
        Path config = dir.resolve("config.yml");
        Files.writeString(config, "pack:\n  port: 9999\n");
        assertTrue(BossFightConfig.addMissingSections(config, bundled()));
        var upgraded = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
        assertFalse(upgraded.getBoolean("editing.in-game", true), "an old config also becomes read-only");
        assertTrue(Files.readString(config).contains("MENUS.md"), "with the comment that says where the menus live");
    }

    @Test void oldConfigsGainTheNewSectionsOnceWithoutTouchingValues(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, "pack:\n  port: 9999\n");
        assertTrue(BossFightConfig.addMissingSections(config, bundled()));
        String text = Files.readString(config);
        assertTrue(text.startsWith("pack:\n  port: 9999\n"));
        assertTrue(text.contains("boss-fight:") && text.contains("editor:") && text.contains("# Boss fight key prompts"));
        assertFalse(BossFightConfig.addMissingSections(config, bundled()), "second run adds nothing");
        assertEquals(BossFightSettings.defaults(), BossFightConfig.load(config));
        assertEquals(9999, org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile()).getInt("pack.port"));
    }

    @Test void singleKeyFightsStillWork() {
        var d = BossFightSettings.defaults();
        var settings = new BossFightSettings(List.of(KeyChallenge.Key.CLICK), 5, 40, 14, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        var game = new KeyChallenge(4, settings);
        while (game.phase() == KeyChallenge.Phase.COUNTDOWN) game.tick();
        assertEquals(KeyChallenge.Key.CLICK, game.current());
        game.press(KeyChallenge.Key.CLICK);
        while (game.phase() == KeyChallenge.Phase.GAP) game.tick();
        assertEquals(KeyChallenge.Key.CLICK, game.current());
        assertEquals(KeyChallenge.WIDE_SCREEN, game.frameId());
        assertTrue(BossFightScreens.build(d).values().stream().flatMap(s -> s.elements().stream()).anyMatch(LiveText::isBar));
    }

    @Test void untouchedOldDisplayDefaultsMoveToFullScreenSizes(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, "display:\n  sizes: [1, 1.5, 1.75, 2.25, 3.5]\n  # Size for players who have not chosen one yet.\n  default-size: 1.75\nsounds:\n  enabled: true\n");
        assertTrue(BossFightConfig.upgradeDisplayDefaults(config));
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
        assertEquals(List.of(1, 1.75, 2, 2.75), yaml.getList("display.sizes").stream().map(v -> ((Number) v).doubleValue() == Math.rint(((Number) v).doubleValue()) ? (Object) ((Number) v).intValue() : v).toList());
        assertEquals(2, yaml.getInt("display.default-size"));
        assertEquals(.4, yaml.getDouble("display.cursor-size"), 1e-9);
        assertFalse(BossFightConfig.upgradeDisplayDefaults(config), "idempotent");
        // An owner's own choice is never rewritten (only the cursor option is added).
        Files.writeString(config, "display:\n  sizes: [1, 2.25]\n  default-size: 2.25\n");
        assertTrue(BossFightConfig.upgradeDisplayDefaults(config));
        String text = Files.readString(config);
        assertTrue(text.contains("sizes: [1, 2.25]") && text.contains("default-size: 2.25") && text.contains("cursor-size: 0.4"));
    }

    @Test void theOwnersPre013ConfigGainsEveryNewSection(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, "pack:\n  bind: 127.0.0.1\n  port: 8140\n  public-url: http://127.0.0.1:8140/packs/\n  required: false\ncursor:\n  distance: 2.5\nlimits:\n  elements: 128\n");
        assertTrue(BossFightConfig.addMissingSections(config, bundled()));
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
        assertEquals(8140, yaml.getInt("pack.port"));
        assertEquals(2, yaml.getInt("display.default-size"));
        assertEquals(.4, yaml.getDouble("display.cursor-size"), 1e-9);
        assertTrue(yaml.getBoolean("editor.auto-reload"));
        assertEquals(BossFightSettings.defaults(), BossFightConfig.load(config));
    }
}
