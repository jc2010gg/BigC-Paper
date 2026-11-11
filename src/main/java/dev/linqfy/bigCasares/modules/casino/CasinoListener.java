package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.bar.DrinkType;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

final class CasinoListener implements Listener {

    private final CasinoRuntime runtime;

    CasinoListener(CasinoRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!runtime.kit().isCasinoEntity(clicked)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            runtime.router().dispatch(clicked, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (runtime.kit().isCasinoEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (runtime.kit().isCasinoEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (event instanceof EntityDamageByEntityEvent byEntity && runtime.kit().isCasinoEntity(byEntity.getDamager())) {
            // Jackpot fireworks and props never hurt anybody.
            event.setCancelled(true);
            return;
        }
        if (runtime.kit().isCasinoEntity(victim)) {
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player
                && runtime.collector().isCollector(victim)) {
                runtime.collector().punched(player, victim);
            }
            return;
        }
        if (!(victim instanceof Player player)) {
            return;
        }
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player damager) {
                attacker = damager;
            } else if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
        }
        for (Attraction attraction : runtime.attractions()) {
            if (attraction.onPlayerDamage(player, attacker, event.getFinalDamage())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHopperPickup(InventoryPickupItemEvent event) {
        if (runtime.kit().isCasinoEntity(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !runtime.kit().isCasinoEntity(event.getDismounted())) {
            return;
        }
        for (Attraction attraction : runtime.attractions()) {
            if (attraction.onDismount(player, event.getDismounted())) {
                return;
            }
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Attraction attraction : runtime.attractions()) {
            attraction.onQuit(player);
        }
        runtime.drinkEffects().forget(player);
        runtime.loans().save();
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        runtime.recover(event.getPlayer());
    }

    @EventHandler
    void onDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            runtime.drinkEffects().restore(drop);
        }
        runtime.drinkEffects().forget(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    void onConsume(PlayerItemConsumeEvent event) {
        Optional<DrinkType> drink = runtime.drinks().typeOf(event.getItem());
        drink.ifPresent(type -> runtime.drinkEffects().drink(event.getPlayer(), type));
    }

    @EventHandler(ignoreCancelled = true)
    void onChat(AsyncChatEvent event) {
        if (!runtime.drinkEffects().slurring(event.getPlayer().getUniqueId())) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.message(Component.text(runtime.drinkEffects().slur(plain)));
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        if (runtime.drinkEffects().tripping(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("No encontrás tus cosas para tirarlas..."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0
                && event.getRawSlot() < menu.size()) {
                menu.dispatch(player, event.getRawSlot(), event.getClick());
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player && runtime.drinkEffects().tripping(player.getUniqueId())
            && event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            // Hidden items must not leave the player's inventory while their models are swapped.
            event.setCancelled(true);
            player.sendActionBar(Component.text("Estás muy duro para ordenar nada."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || runtime.loans().loan(player.getUniqueId()).isEmpty()) {
            return;
        }
        var holder = event.getInventory().getHolder();
        if (holder instanceof Container container) {
            runtime.loans().rememberChest(player.getUniqueId(), container.getLocation());
        } else if (holder instanceof DoubleChest chest) {
            if (chest.getLeftSide() instanceof Container left) {
                runtime.loans().rememberChest(player.getUniqueId(), left.getLocation());
            }
            if (chest.getRightSide() instanceof Container right) {
                runtime.loans().rememberChest(player.getUniqueId(), right.getLocation());
            }
        }
    }
}
