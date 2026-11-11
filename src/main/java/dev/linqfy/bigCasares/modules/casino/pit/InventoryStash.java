package dev.linqfy.bigCasares.modules.casino.pit;

import dev.linqfy.bigCasares.modules.casino.CasinoFiles;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a fighter's inventory on disk while they use the house's wooden sword. The file is written before the
 * inventory is cleared and deleted only after it was given back, so a crash returns everything on next join.
 */
public final class InventoryStash {

    private final File folder;
    private final Logger logger;

    public InventoryStash(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    public boolean has(UUID player) {
        return file(player).isFile();
    }

    /** Saves and clears the inventory. Returns false (and leaves the inventory alone) when saving failed. */
    public boolean stash(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            items.add(item == null || item.getType().isAir() ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        yaml.set("items", items);
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        yaml.set("return", player.getLocation());
        try {
            CasinoFiles.atomicSave(yaml, file(player.getUniqueId()));
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Casino] Could not stash the inventory of " + player.getName(), failure);
            return false;
        }
        player.getInventory().clear();
        return true;
    }

    /** Gives the stashed inventory back. Returns the stored return location, or null when nothing was stashed. */
    public Location restore(Player player) {
        File file = file(player.getUniqueId());
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> items = yaml.getStringList("items");
        ItemStack[] contents = new ItemStack[player.getInventory().getSize()];
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            String encoded = items.get(slot);
            if (encoded.isEmpty()) {
                continue;
            }
            try {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                if (slot < contents.length) {
                    contents[slot] = item;
                } else {
                    overflow.add(item);
                }
            } catch (RuntimeException invalid) {
                logger.log(Level.WARNING, "[Casino] Could not restore a stashed item of " + player.getName(), invalid);
            }
        }
        player.getInventory().clear();
        player.getInventory().setContents(contents);
        for (ItemStack item : overflow) {
            player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        player.setLevel(yaml.getInt("level", player.getLevel()));
        player.setExp((float) yaml.getDouble("exp", player.getExp()));
        Location back = yaml.getLocation("return");
        if (!file.delete()) {
            logger.warning("[Casino] Could not delete the inventory stash of " + player.getName());
        }
        return back;
    }

    private File file(UUID player) {
        return new File(folder, player + ".yml");
    }
}
