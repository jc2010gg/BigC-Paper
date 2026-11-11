package dev.linqfy.bigCasares.modules.schizophrenia;

import java.util.Locale;
import java.util.Set;

/** Tuning for episodes, natural onset, phantom clones and the Lobotomizador. */
public record SchizophreniaSettings(
    int maxEpisodeMinutes,
    double minutesPerIntensity,
    int slowestIntervalSeconds,
    int fastestIntervalSeconds,
    int immunityMinutesAfterCure,
    Natural natural,
    Phantoms phantoms,
    Lobotomizer lobotomizer,
    VoidPlace voidPlace,
    Cleaver cleaver,
    Set<Hallucination> disabledHallucinations
) {
    public SchizophreniaSettings {
        maxEpisodeMinutes = Math.max(0, maxEpisodeMinutes);
        minutesPerIntensity = Double.isFinite(minutesPerIntensity) && minutesPerIntensity > 0 ? minutesPerIntensity : 3.0;
        slowestIntervalSeconds = Math.max(1, slowestIntervalSeconds);
        fastestIntervalSeconds = Math.max(1, Math.min(slowestIntervalSeconds, fastestIntervalSeconds));
        immunityMinutesAfterCure = Math.max(0, immunityMinutesAfterCure);
        natural = natural == null ? Natural.defaults() : natural;
        phantoms = phantoms == null ? Phantoms.defaults() : phantoms;
        lobotomizer = lobotomizer == null ? Lobotomizer.defaults() : lobotomizer;
        voidPlace = voidPlace == null ? VoidPlace.defaults() : voidPlace;
        cleaver = cleaver == null ? Cleaver.defaults() : cleaver;
        disabledHallucinations = disabledHallucinations == null ? Set.of() : Set.copyOf(disabledHallucinations);
    }

    public static SchizophreniaSettings defaults() {
        return new SchizophreniaSettings(45, 6.0, 25, 6, 20,
            Natural.defaults(), Phantoms.defaults(), Lobotomizer.defaults(), VoidPlace.defaults(),
            Cleaver.defaults(), Set.of());
    }

    /** The last stage: the victim gets the Cuchilla and is made to kill whatever they can see. */
    public record Cleaver(boolean enabled, double sightRange, boolean attackPlayers) {
        public Cleaver {
            sightRange = Double.isFinite(sightRange) ? Math.max(4.0, Math.min(64.0, sightRange)) : 32.0;
        }

        public static Cleaver defaults() {
            return new Cleaver(true, 32.0, true);
        }
    }

    /** The black place victims get taken to: an empty world owned by this module. */
    public record VoidPlace(boolean enabled, String worldName) {
        public VoidPlace {
            worldName = worldName == null || !worldName.matches("[a-z0-9_\\-]{1,48}") ? "bigcasares_void" : worldName;
        }

        public static VoidPlace defaults() {
            return new VoidPlace(true, "bigcasares_void");
        }
    }

    /** Natural onset: a rare event for sleepless players who spend far too long alone in the dark. */
    public record Natural(
        boolean enabled,
        int checkIntervalSeconds,
        double chance,
        int aloneRadius,
        int maxLightLevel,
        int deepBelowY,
        int insomniaDays,
        int stressThreshold,
        int playerCooldownHours,
        int serverCooldownMinutes,
        int maxConcurrent,
        Set<String> worlds
    ) {
        public Natural {
            checkIntervalSeconds = Math.max(5, checkIntervalSeconds);
            chance = Double.isFinite(chance) ? Math.max(0.0, Math.min(1.0, chance)) : 0.0;
            aloneRadius = Math.max(0, aloneRadius);
            maxLightLevel = Math.max(0, Math.min(15, maxLightLevel));
            insomniaDays = Math.max(0, insomniaDays);
            stressThreshold = Math.max(1, stressThreshold);
            playerCooldownHours = Math.max(0, playerCooldownHours);
            serverCooldownMinutes = Math.max(0, serverCooldownMinutes);
            maxConcurrent = Math.max(0, maxConcurrent);
            worlds = worlds == null ? Set.of() : Set.copyOf(worlds.stream()
                .map(world -> world.toLowerCase(Locale.ROOT)).toList());
        }

        public static Natural defaults() {
            return new Natural(true, 60, 0.1, 48, 4, 0, 3, 40, 48, 180, 1, Set.of());
        }

        boolean allowsWorld(String worldName) {
            return worlds.isEmpty() || (worldName != null && worlds.contains(worldName.toLowerCase(Locale.ROOT)));
        }
    }

    public record Phantoms(
        int maxPerPlayer,
        double minDistance,
        double maxDistance,
        double gazeThresholdDegrees,
        double vanishDistance,
        int lifetimeSeconds
    ) {
        public Phantoms {
            maxPerPlayer = Math.max(1, maxPerPlayer);
            minDistance = Math.max(3.0, minDistance);
            maxDistance = Math.max(minDistance, maxDistance);
            gazeThresholdDegrees = Math.max(1.0, Math.min(60.0, gazeThresholdDegrees));
            vanishDistance = Math.max(1.0, vanishDistance);
            lifetimeSeconds = Math.max(3, lifetimeSeconds);
        }

        public static Phantoms defaults() {
            return new Phantoms(12, 12.0, 26.0, 11.0, 3.5, 30);
        }
    }

    public record Lobotomizer(boolean recipeEnabled, int durabilityCost, int stunSeconds) {
        public Lobotomizer {
            durabilityCost = Math.max(0, durabilityCost);
            stunSeconds = Math.max(0, stunSeconds);
        }

        public static Lobotomizer defaults() {
            return new Lobotomizer(true, 40, 30);
        }
    }
}
