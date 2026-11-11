package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Settings for the hand-made enemies of the mob-scaling module: the baby zombie lancer riding a rabbit,
 * the zombie that throws ender pearls to cut off fleeing players and the nuclear creeper.
 */
public record SpecialEnemySettings(
        boolean rabbitLancerEnabled,
        double rabbitSpeed,
        float spearDropChance,
        boolean pearlZombieEnabled,
        int pearlCooldownSeconds,
        double pearlMinDistance,
        double pearlMaxDistance,
        boolean nuclearCreeperEnabled,
        double nuclearChance,
        float nuclearExplosionPower,
        float radiationRadius,
        int radiationSeconds
) {
    /** 0.00001 % per creeper spawned naturally during a thunderstorm. */
    public static final double DEFAULT_NUCLEAR_CHANCE = 0.0000001;

    private static final String PREFIX = "mob-scaling.special-enemies.";

    public static SpecialEnemySettings defaults() {
        return new SpecialEnemySettings(true, 0.6, 0.0F, true, 6, 6.0, 24.0,
                true, DEFAULT_NUCLEAR_CHANCE, 20.0F, 14.0F, 60);
    }

    public static SpecialEnemySettings fromConfig(FileConfiguration config) {
        SpecialEnemySettings d = defaults();
        return new SpecialEnemySettings(
                config.getBoolean(PREFIX + "rabbit-lancer.enabled", d.rabbitLancerEnabled()),
                Math.max(0.0, config.getDouble(PREFIX + "rabbit-lancer.rabbit-speed", d.rabbitSpeed())),
                clamp01(config.getDouble(PREFIX + "rabbit-lancer.spear-drop-chance", d.spearDropChance())),
                config.getBoolean(PREFIX + "pearl-zombie.enabled", d.pearlZombieEnabled()),
                Math.max(1, config.getInt(PREFIX + "pearl-zombie.cooldown-seconds", d.pearlCooldownSeconds())),
                Math.max(0.0, config.getDouble(PREFIX + "pearl-zombie.min-distance", d.pearlMinDistance())),
                Math.max(1.0, config.getDouble(PREFIX + "pearl-zombie.max-distance", d.pearlMaxDistance())),
                config.getBoolean(PREFIX + "nuclear-creeper.enabled", d.nuclearCreeperEnabled()),
                Math.max(0.0, Math.min(1.0, config.getDouble(PREFIX + "nuclear-creeper.chance", d.nuclearChance()))),
                (float) Math.max(1.0, config.getDouble(PREFIX + "nuclear-creeper.explosion-power", d.nuclearExplosionPower())),
                (float) Math.max(0.0, config.getDouble(PREFIX + "nuclear-creeper.radiation-radius", d.radiationRadius())),
                Math.max(0, config.getInt(PREFIX + "nuclear-creeper.radiation-seconds", d.radiationSeconds()))
        );
    }

    private static float clamp01(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
