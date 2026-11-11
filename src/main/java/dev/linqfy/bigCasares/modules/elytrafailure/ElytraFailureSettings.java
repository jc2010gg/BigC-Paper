package dev.linqfy.bigCasares.modules.elytrafailure;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Settings of the elytra mechanical failure.
 *
 * @param checkIntervalTicks   how often gliding players roll for a failure
 * @param chancePerCheck       probability of a failure on each roll
 * @param minFlightTicks       continuous gliding before failures are possible (no failure on take-off)
 * @param driftTicks           how long the player is pulled to the broken side before falling
 * @param driftStrength        sideways speed added every tick of the drift
 * @param durabilityLossFraction share of the elytra's max durability lost when the wing breaks
 */
public record ElytraFailureSettings(
    long checkIntervalTicks,
    double chancePerCheck,
    long minFlightTicks,
    int driftTicks,
    double driftStrength,
    double durabilityLossFraction
) {

    public static ElytraFailureSettings defaults() {
        // One roll per second at 0.15%: on average one failure every ~11 minutes of gliding.
        return new ElytraFailureSettings(20L, 0.0015, 100L, 30, 0.12, 0.25);
    }

    public static ElytraFailureSettings load(FileConfiguration config) {
        ElytraFailureSettings defaults = defaults();
        try {
            return new ElytraFailureSettings(
                config.getLong("elytra-failure.check-interval-ticks", defaults.checkIntervalTicks()),
                config.getDouble("elytra-failure.chance-per-check", defaults.chancePerCheck()),
                config.getLong("elytra-failure.min-flight-ticks", defaults.minFlightTicks()),
                config.getInt("elytra-failure.drift-ticks", defaults.driftTicks()),
                config.getDouble("elytra-failure.drift-strength", defaults.driftStrength()),
                config.getDouble("elytra-failure.durability-loss-fraction", defaults.durabilityLossFraction())
            );
        } catch (IllegalArgumentException invalid) {
            return defaults;
        }
    }

    public ElytraFailureSettings {
        if (checkIntervalTicks <= 0L) {
            throw new IllegalArgumentException("check-interval-ticks debe ser mayor a cero.");
        }
        if (chancePerCheck < 0.0 || chancePerCheck > 1.0) {
            throw new IllegalArgumentException("chance-per-check debe estar entre 0 y 1.");
        }
        if (minFlightTicks < 0L || driftTicks < 0 || driftStrength < 0.0) {
            throw new IllegalArgumentException("La deriva de la falla de elytra no es válida.");
        }
        if (durabilityLossFraction < 0.0 || durabilityLossFraction > 1.0) {
            throw new IllegalArgumentException("durability-loss-fraction debe estar entre 0 y 1.");
        }
    }
}
