package dev.linqfy.bigCasares.modules.elytrafailure;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

final class ElytraFailureListener implements Listener {

    private final ElytraFailureRuntime runtime;

    ElytraFailureListener(ElytraFailureRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        ElytraWing wing = runtime.brokenWing(player.getInventory().getChestplate());
        if (wing != null) {
            event.setCancelled(true);
            player.sendActionBar(ChatColor.RED + "Tu elytra tiene el ala " + wing.displayName()
                + " rota. Reparala con membrana de phantom.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getFirstItem();
        ItemStack addition = inventory.getSecondItem();
        if (runtime.brokenWing(base) == null
            || addition == null || addition.getType() != Material.PHANTOM_MEMBRANE) {
            return;
        }
        ItemStack vanillaResult = event.getResult();
        ItemStack fixed = runtime.repaired(vanillaResult != null && vanillaResult.getType() == Material.ELYTRA
            ? vanillaResult
            : base);
        if (fixed == null) {
            fixed = vanillaResult;
        }
        event.setResult(fixed);
        if (vanillaResult == null || vanillaResult.getType() != Material.ELYTRA) {
            // A full-durability elytra has no vanilla repair: still allow fixing the wing for one level.
            event.getView().setRepairCost(Math.max(1, event.getView().getRepairCost()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        runtime.forget(event.getPlayer().getUniqueId());
    }
}
