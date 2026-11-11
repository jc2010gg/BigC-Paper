package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchizophreniaSettingsTest {

    @Test
    void productionConfigMatchesDefaults() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        List<String> warnings = new ArrayList<>();

        SchizophreniaSettings settings = SchizophreniaSettingsLoader.load(yaml, warnings::add);

        assertTrue(warnings.isEmpty(), warnings::toString);
        assertEquals(SchizophreniaSettings.defaults(), settings);
        assertTrue(yaml.getBoolean("modules.schizophrenia.enabled"));
    }

    @Test
    void missingSectionFallsBackToDefaults() {
        assertEquals(SchizophreniaSettings.defaults(), SchizophreniaSettingsLoader.load(new YamlConfiguration()));
    }

    @Test
    void parsesDisabledHallucinationsAndWarnsOnUnknownOnes() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            schizophrenia:
              disabled-hallucinations: [fake-death, CAMERA_SNAP, nope]
              natural:
                worlds: [World_Nether]
            """);
        List<String> warnings = new ArrayList<>();

        SchizophreniaSettings settings = SchizophreniaSettingsLoader.load(yaml, warnings::add);

        assertEquals(Set.of(Hallucination.FAKE_DEATH, Hallucination.CAMERA_SNAP), settings.disabledHallucinations());
        assertEquals(1, warnings.size());
        assertTrue(settings.natural().allowsWorld("world_nether"));
        assertEquals("bigcasares_void", settings.voidPlace().worldName());
        assertFalse(settings.natural().allowsWorld("world"));
    }

    @Test
    void clampsNonsenseValues() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            schizophrenia:
              max-episode-minutes: -4
              minutes-per-intensity: 0
              interval-seconds: {slowest: 5, fastest: 50}
              natural: {chance: 7, max-light-level: 99}
              phantoms: {max-distance: 1, gaze-threshold-degrees: 500}
              void: {world: "../escape"}
              cleaver: {sight-range: 900, attack-players: false}
            """);

        SchizophreniaSettings settings = SchizophreniaSettingsLoader.load(yaml);

        assertEquals(0, settings.maxEpisodeMinutes());
        assertEquals(3.0, settings.minutesPerIntensity());
        assertEquals(5, settings.fastestIntervalSeconds());
        assertEquals(1.0, settings.natural().chance());
        assertEquals(15, settings.natural().maxLightLevel());
        assertTrue(settings.phantoms().maxDistance() >= settings.phantoms().minDistance());
        assertEquals(60.0, settings.phantoms().gazeThresholdDegrees());
        assertEquals("bigcasares_void", settings.voidPlace().worldName());
        assertEquals(64.0, settings.cleaver().sightRange());
        assertFalse(settings.cleaver().attackPlayers());
        assertTrue(settings.cleaver().enabled());
    }
}
