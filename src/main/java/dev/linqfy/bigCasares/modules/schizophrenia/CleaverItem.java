package dev.linqfy.bigCasares.modules.schizophrenia;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * La Cuchilla: what the last stage of an episode puts in the victim's hand. It only exists while
 * the frenzy lasts; it cannot be dropped, stored or kept, and it is taken away when the voices stop.
 */
final class CleaverItem {

    static final String ITEM_ID = "cuchilla";

    private final NamespacedKey idKey;

    CleaverItem(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "schizophrenia_cleaver");
    }

    ItemStack create() {
        ItemStack stack = new ItemStack(Material.IRON_AXE);
        stack.editMeta(meta -> {
            meta.itemName(Component.text("Cuchilla", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            meta.lore(List.of(
                line("No es tuya. Nunca la soltaste.", NamedTextColor.GRAY),
                line("Todo lo que ves tiene que dejar de moverse.", NamedTextColor.DARK_GRAY)));
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, ITEM_ID);
        });
        return stack;
    }

    boolean isCleaver(ItemStack stack) {
        if (stack == null || stack.getType() != Material.IRON_AXE || !stack.hasItemMeta()) {
            return false;
        }
        return ITEM_ID.equals(stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    /** Puts a cleaver in the main hand, moving whatever was held into the inventory (or at the feet). */
    void forceIntoHand(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (isCleaver(held)) {
            return;
        }
        ItemStack[] contents = inventory.getStorageContents();
        int found = -1;
        for (int slot = 0; slot < contents.length; slot++) {
            if (isCleaver(contents[slot])) {
                found = slot;
                break;
            }
        }
        if (found >= 0) {
            ItemStack cleaver = contents[found];
            inventory.setItem(found, held);
            inventory.setItemInMainHand(cleaver);
            return;
        }
        inventory.setItemInMainHand(create());
        if (!held.getType().isAir()) {
            inventory.addItem(held).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    /** Removes every cleaver the player carries, wherever it ended up. */
    void takeAway(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isCleaver(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
        if (isCleaver(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
