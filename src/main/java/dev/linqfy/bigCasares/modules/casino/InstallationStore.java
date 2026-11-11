package dev.linqfy.bigCasares.modules.casino;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class InstallationStore {

    private final File file;
    private final Logger logger;

    InstallationStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    List<Installation> load() {
        List<Installation> installations = new ArrayList<>();
        if (!file.isFile()) {
            return installations;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("installations");
        if (root == null) {
            return installations;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            AttractionType type = AttractionType.byId(entry.getString("type", "")).orElse(null);
            if (type == null) {
                logger.warning("[Casino] Unknown attraction type in installation " + id);
                continue;
            }
            installations.add(new Installation(id, type, entry.getString("world", "world"),
                entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"), (float) entry.getDouble("yaw"),
                entry.getString("variant", "")));
        }
        return installations;
    }

    void save(List<Installation> installations) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Installation installation : installations) {
            String path = "installations." + installation.id();
            yaml.set(path + ".type", installation.type().id());
            yaml.set(path + ".world", installation.world());
            yaml.set(path + ".x", installation.x());
            yaml.set(path + ".y", installation.y());
            yaml.set(path + ".z", installation.z());
            yaml.set(path + ".yaw", (double) installation.yaw());
            if (!installation.variant().isEmpty()) {
                yaml.set(path + ".variant", installation.variant());
            }
        }
        try {
            CasinoFiles.atomicSave(yaml, file);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Casino] Could not save installations", failure);
        }
    }
}
