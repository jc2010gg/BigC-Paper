package dev.linqfy.bigCasares.modules.casino.bar;

import dev.linqfy.bigCasares.modules.casino.world.CasinoKeys;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public final class DrinkItems {

    private final CasinoKeys keys;

    public DrinkItems(CasinoKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(DrinkType type) {
        ItemStack bottle = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        meta.setColor(Color.fromRGB(type.color()));
        meta.displayName(CasinoText.legacy(type.displayName()));
        meta.lore(type.lore().stream().map(CasinoText::legacy).toList());
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(keys.drink, PersistentDataType.STRING, type.id());
        bottle.setItemMeta(meta);
        return bottle;
    }

    public Optional<DrinkType> typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keys.drink, PersistentDataType.STRING);
        return id == null ? Optional.empty() : DrinkType.byId(id);
    }
}
