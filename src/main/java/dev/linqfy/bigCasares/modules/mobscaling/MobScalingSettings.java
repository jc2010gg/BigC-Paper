package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record MobScalingSettings(
        boolean enabled,
        LocalDate startDate,
        double formulaA,
        double formulaB,
        double formulaC,
        double baseMutationChance,
        double mutationChancePerDay,
        double maxMutationChance,
        double maxMultiplier
) {
    /** Default ceiling: the exponential curve passes 3x after about a month and thousands after four. */
    public static final double DEFAULT_MAX_MULTIPLIER = 3.0;

    private static final String PREFIX = "mob-scaling.";

    public static MobScalingSettings fromConfig(FileConfiguration config) {
        boolean enabled = config.getBoolean(PREFIX + "enabled", true);
        String dateStr = config.getString(PREFIX + "start-date", "2026-07-17");
        LocalDate startDate;
        try {
            startDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            startDate = LocalDate.now();
        }
        
        double a = config.getDouble(PREFIX + "formula.a", 0.05);
        double b = config.getDouble(PREFIX + "formula.b", 1.1);
        double c = config.getDouble(PREFIX + "formula.c", 1.0);
        
        double baseChance = config.getDouble(PREFIX + "mutations.base-chance", 0.05);
        double perDay = config.getDouble(PREFIX + "mutations.chance-per-day", 0.02);
        double maxChance = config.getDouble(PREFIX + "mutations.max-chance", 0.35);

        double maxMultiplier = config.getDouble(PREFIX + "max-multiplier", DEFAULT_MAX_MULTIPLIER);

        return new MobScalingSettings(enabled, startDate, a, b, c, baseChance, perDay, maxChance, maxMultiplier);
    }

    /**
     * Health and damage multiplier for mobs spawned {@code days} after the start date. Capped at
     * {@code maxMultiplier} (0 or less = uncapped) so late servers do not get mobs that one-shot.
     */
    public double multiplier(long days) {
        double raw = formulaA * Math.pow(formulaB, Math.max(0, days)) + formulaC;
        if (!Double.isFinite(raw)) {
            raw = Double.MAX_VALUE;
        }
        return maxMultiplier > 0 ? Math.min(raw, maxMultiplier) : raw;
    }
}
