package dev.linqfy.bigCasares.modules.casino;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class YamlEscrowStore implements CasinoEscrow.Store {

    private final File file;
    private final Logger logger;

    YamlEscrowStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public Map<String, CasinoEscrow.Hold> load() {
        Map<String, CasinoEscrow.Hold> holds = new LinkedHashMap<>();
        if (!file.isFile()) {
            return holds;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("holds");
        if (section == null) {
            return holds;
        }
        for (String index : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(index);
            if (entry == null) {
                continue;
            }
            try {
                holds.put(entry.getString("key", index), new CasinoEscrow.Hold(
                    UUID.fromString(entry.getString("player", "")), entry.getLong("amount")));
            } catch (IllegalArgumentException invalid) {
                logger.warning("[Casino] Ignoring invalid escrow entry " + index);
            }
        }
        return holds;
    }

    @Override
    public void save(Map<String, CasinoEscrow.Hold> holds) {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (Map.Entry<String, CasinoEscrow.Hold> entry : holds.entrySet()) {
            String path = "holds." + index++;
            yaml.set(path + ".key", entry.getKey());
            yaml.set(path + ".player", entry.getValue().player().toString());
            yaml.set(path + ".amount", entry.getValue().amount());
        }
        try {
            CasinoFiles.atomicSave(yaml, file);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Casino] Could not save the escrow ledger", failure);
        }
    }
}
