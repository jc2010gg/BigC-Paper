package dev.linqfy.bigCasares.modules.casino.world;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Chest menu whose buttons are callbacks. Nothing can be taken out of it. */
public final class CasinoMenu implements InventoryHolder {

    @FunctionalInterface
    public interface Action {
        void click(Player player, ClickType click);
    }

    private final Inventory inventory;
    private final Map<Integer, Action> actions = new HashMap<>();

    public CasinoMenu(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, CasinoText.legacy(title));
    }

    public CasinoMenu set(int slot, ItemStack item, Action action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
        return this;
    }

    public CasinoMenu fill(ItemStack filler) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public int size() {
        return inventory.getSize();
    }

    public void dispatch(Player player, int slot, ClickType click) {
        Action action = actions.get(slot);
        if (action != null) {
            action.click(player, click);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
