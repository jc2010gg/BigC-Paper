package dev.linqfy.bigCasares.modules.keepinventorytotem;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Settings of the Tótem de Conservación.
 *
 * @param cooldownSeconds time after a use during which another totem does not protect the player
 * @param keepExperience  whether the player also keeps their levels when the totem fires
 * @param maxCarried      how many totems one player may carry at once
 */
public record KeepInventoryTotemSettings(long cooldownSeconds, boolean keepExperience, int maxCarried) {

    public static final long DEFAULT_COOLDOWN_SECONDS = 4L * 60L * 60L;

    public static KeepInventoryTotemSettings defaults() {
        return new KeepInventoryTotemSettings(DEFAULT_COOLDOWN_SECONDS, true, 1);
    }

    public static KeepInventoryTotemSettings load(FileConfiguration config) {
        KeepInventoryTotemSettings defaults = defaults();
        long cooldown = config.getLong("keep-inventory-totem.cooldown-seconds", defaults.cooldownSeconds());
        int maxCarried = config.getInt("keep-inventory-totem.max-carried", defaults.maxCarried());
        return new KeepInventoryTotemSettings(
            cooldown < 0L ? defaults.cooldownSeconds() : cooldown,
            config.getBoolean("keep-inventory-totem.keep-experience", defaults.keepExperience()),
            maxCarried < 1 ? defaults.maxCarried() : maxCarried
        );
    }

    public KeepInventoryTotemSettings {
        if (cooldownSeconds < 0L) {
            throw new IllegalArgumentException("cooldown-seconds no puede ser negativo.");
        }
        if (maxCarried < 1) {
            throw new IllegalArgumentException("max-carried debe ser al menos 1.");
        }
    }

    public long cooldownMillis() {
        return cooldownSeconds * 1000L;
    }
}
