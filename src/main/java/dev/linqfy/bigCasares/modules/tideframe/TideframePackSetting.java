package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Tideframe's {@code pack.send} (plugins/Tideframe/config.yml, true by default). Only while Tideframe sends its own pack
 * does its "pack loaded" mean that pack: with {@code pack.send: false} any loaded server pack counts, so BigCasares
 * cannot tell that the player has Tideframe's assets. Fails closed:
 * <ul>
 *   <li>The file is read again on each call while it says true; this is only when a menu opens, so it is cheap.</li>
 *   <li>A file that cannot be read counts as false.</li>
 *   <li>Once false has been read, it stays false until the server restarts, including across BigCasares module reloads.
 *   The file can say true before Tideframe has reloaded it, and its API does not tell which mode it runs.</li>
 * </ul>
 */
final class TideframePackSetting implements BooleanSupplier {
    // Provider configuration, not player state: a new BigCasares runtime must retain a known unsafe pack mode.
    private static final Set<Path> OFF_CONFIGS = ConcurrentHashMap.newKeySet();
    private final Path config;

    TideframePackSetting(Path config) {
        this.config = Objects.requireNonNull(config, "config").toAbsolutePath().normalize();
    }

    @Override
    public synchronized boolean getAsBoolean() {
        if (OFF_CONFIGS.contains(config)) {
            return false;
        }
        if (Files.notExists(config)) {
            return true;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(config.toFile());
        } catch (IOException | InvalidConfigurationException unreadable) {
            return false;
        }
        boolean sends = yaml.getBoolean("pack.send", true);
        if (!sends) {
            OFF_CONFIGS.add(config);
        }
        return !OFF_CONFIGS.contains(config);
    }
}
