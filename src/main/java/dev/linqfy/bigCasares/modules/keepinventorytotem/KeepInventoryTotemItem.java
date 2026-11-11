package dev.linqfy.bigCasares.modules.keepinventorytotem;

import dev.linqfy.bigCasares.items.CatalogBackedCustomItem;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KeepInventoryTotemItem extends CatalogBackedCustomItem {

    public static final String ID = "keep_inventory_totem";
    public static final int MODEL_DATA = 1024;

    public KeepInventoryTotemItem(CustomItemRegistry registry, NamespacedKey legacyItemKey) {
        super(registry, ID, MODEL_DATA, legacyItemKey);
    }

    @Override
    public void onConsume(Player player, ItemStack consumedItem) {
        // The totem works from the inventory on death; it is never eaten.
    }
}
