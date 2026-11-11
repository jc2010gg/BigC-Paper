package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class SchizophreniaSettingsLoader {

    public static final String SECTION = "schizophrenia";

    private SchizophreniaSettingsLoader() {
    }

    public static SchizophreniaSettings load(ConfigurationSection root) {
        return load(root, warning -> { });
    }

    public static SchizophreniaSettings load(ConfigurationSection root, Consumer<String> warnings) {
        SchizophreniaSettings defaults = SchizophreniaSettings.defaults();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(SECTION);
        if (section == null) {
            return defaults;
        }
        SchizophreniaSettings.Natural naturalDefaults = defaults.natural();
        SchizophreniaSettings.Phantoms phantomDefaults = defaults.phantoms();
        SchizophreniaSettings.Lobotomizer lobotomizerDefaults = defaults.lobotomizer();
        SchizophreniaSettings.VoidPlace voidDefaults = defaults.voidPlace();
        SchizophreniaSettings.Cleaver cleaverDefaults = defaults.cleaver();

        EnumSet<Hallucination> disabled = EnumSet.noneOf(Hallucination.class);
        for (String raw : section.getStringList("disabled-hallucinations")) {
            Optional<Hallucination> parsed = Hallucination.parse(raw);
            if (parsed.isPresent()) {
                disabled.add(parsed.get());
            } else {
                warnings.accept("Alucinación desconocida en disabled-hallucinations: " + raw);
            }
        }
        List<String> worlds = section.getStringList("natural.worlds");

        return new SchizophreniaSettings(
            section.getInt("max-episode-minutes", defaults.maxEpisodeMinutes()),
            section.getDouble("minutes-per-intensity", defaults.minutesPerIntensity()),
            section.getInt("interval-seconds.slowest", defaults.slowestIntervalSeconds()),
            section.getInt("interval-seconds.fastest", defaults.fastestIntervalSeconds()),
            section.getInt("immunity-minutes-after-cure", defaults.immunityMinutesAfterCure()),
            new SchizophreniaSettings.Natural(
                section.getBoolean("natural.enabled", naturalDefaults.enabled()),
                section.getInt("natural.check-interval-seconds", naturalDefaults.checkIntervalSeconds()),
                section.getDouble("natural.chance", naturalDefaults.chance()),
                section.getInt("natural.alone-radius", naturalDefaults.aloneRadius()),
                section.getInt("natural.max-light-level", naturalDefaults.maxLightLevel()),
                section.getInt("natural.deep-below-y", naturalDefaults.deepBelowY()),
                section.getInt("natural.insomnia-days", naturalDefaults.insomniaDays()),
                section.getInt("natural.stress-threshold", naturalDefaults.stressThreshold()),
                section.getInt("natural.player-cooldown-hours", naturalDefaults.playerCooldownHours()),
                section.getInt("natural.server-cooldown-minutes", naturalDefaults.serverCooldownMinutes()),
                section.getInt("natural.max-concurrent", naturalDefaults.maxConcurrent()),
                new HashSet<>(worlds)),
            new SchizophreniaSettings.Phantoms(
                section.getInt("phantoms.max-per-player", phantomDefaults.maxPerPlayer()),
                section.getDouble("phantoms.min-distance", phantomDefaults.minDistance()),
                section.getDouble("phantoms.max-distance", phantomDefaults.maxDistance()),
                section.getDouble("phantoms.gaze-threshold-degrees", phantomDefaults.gazeThresholdDegrees()),
                section.getDouble("phantoms.vanish-distance", phantomDefaults.vanishDistance()),
                section.getInt("phantoms.lifetime-seconds", phantomDefaults.lifetimeSeconds())),
            new SchizophreniaSettings.Lobotomizer(
                section.getBoolean("lobotomizer.recipe", lobotomizerDefaults.recipeEnabled()),
                section.getInt("lobotomizer.durability-cost", lobotomizerDefaults.durabilityCost()),
                section.getInt("lobotomizer.stun-seconds", lobotomizerDefaults.stunSeconds())),
            new SchizophreniaSettings.VoidPlace(
                section.getBoolean("void.enabled", voidDefaults.enabled()),
                section.getString("void.world", voidDefaults.worldName())),
            new SchizophreniaSettings.Cleaver(
                section.getBoolean("cleaver.enabled", cleaverDefaults.enabled()),
                section.getDouble("cleaver.sight-range", cleaverDefaults.sightRange()),
                section.getBoolean("cleaver.attack-players", cleaverDefaults.attackPlayers())),
            disabled);
    }
}
