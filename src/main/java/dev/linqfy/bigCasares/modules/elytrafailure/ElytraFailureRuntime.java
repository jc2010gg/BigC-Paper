package dev.linqfy.bigCasares.modules.elytrafailure;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Rolls a rare wing failure for players gliding with an elytra. A failed wing pulls the player towards
 * its side for a moment and then the glide stops; the elytra stays broken (it refuses to open) until
 * it is repaired in an anvil with a phantom membrane.
 */
final class ElytraFailureRuntime {

    static final String BROKEN_LORE = ChatColor.RED + "Ala rota: reparala en un yunque con membrana de phantom";

    private final ElytraFailureSettings settings;
    private final ElytraFailurePolicy policy;
    private final NamespacedKey brokenWingKey;
    private final DoubleSupplier random;
    private final Map<UUID, Long> glidingTicks = new HashMap<>();
    private final Map<UUID, Drift> drifts = new HashMap<>();

    ElytraFailureRuntime(ElytraFailureSettings settings, NamespacedKey brokenWingKey, DoubleSupplier random) {
        this.settings = settings;
        this.policy = new ElytraFailurePolicy(settings);
        this.brokenWingKey = brokenWingKey;
        this.random = random;
    }

    // --- broken-wing marker ----------------------------------------------------------------------

    ElytraWing brokenWing(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ELYTRA || !stack.hasItemMeta()) {
            return null;
        }
        return ElytraWing.parse(stack.getItemMeta().getPersistentDataContainer()
            .get(brokenWingKey, PersistentDataType.STRING));
    }

    void markBroken(ItemStack stack, ElytraWing wing) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(brokenWingKey, PersistentDataType.STRING, wing.name());
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        if (!lore.contains(BROKEN_LORE)) {
            lore.add(BROKEN_LORE);
        }
        meta.setLore(lore);
        if (meta instanceof Damageable damageable) {
            int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : stack.getType().getMaxDurability();
            damageable.setDamage(policy.damageAfterFailure(damageable.getDamage(), maxDamage));
        }
        stack.setItemMeta(meta);
    }

    /** Returns a copy without the broken-wing marker, or {@code null} when the stack was not broken. */
    ItemStack repaired(ItemStack stack) {
        if (brokenWing(stack) == null) {
            return null;
        }
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().remove(brokenWingKey);
        List<String> lore = meta.getLore();
        if (lore != null) {
            List<String> cleaned = new ArrayList<>(lore);
            cleaned.removeIf(BROKEN_LORE::equals);
            meta.setLore(cleaned.isEmpty() ? null : cleaned);
        }
        copy.setItemMeta(meta);
        return copy;
    }

    // --- ticking ---------------------------------------------------------------------------------

    /** Runs every {@code check-interval-ticks}: counts gliding time and rolls for failures. */
    void check(Iterable<? extends Player> players) {
        for (Player player : players) {
            UUID id = player.getUniqueId();
            ItemStack chest = player.getInventory().getChestplate();
            boolean eligible = player.isGliding()
                && chest != null && chest.getType() == Material.ELYTRA
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && !drifts.containsKey(id);
            if (!eligible) {
                if (!player.isGliding()) {
                    glidingTicks.remove(id);
                }
                continue;
            }
            if (brokenWing(chest) != null) {
                // Already broken (e.g. glide started by another plugin): drop out of the sky.
                player.setGliding(false);
                continue;
            }
            long ticks = glidingTicks.merge(id, settings.checkIntervalTicks(), Long::sum);
            if (policy.shouldFail(ticks, random.getAsDouble())) {
                fail(player, chest, ElytraWing.fromRoll(random.getAsDouble()));
            }
        }
    }

    /** Runs every tick: pushes drifting players sideways, then ends their glide. */
    void tickDrifts() {
        Iterator<Map.Entry<UUID, Drift>> iterator = drifts.entrySet().iterator();
        while (iterator.hasNext()) {
            Drift drift = iterator.next().getValue();
            Player player = drift.player();
            if (!player.isOnline() || player.isDead() || !player.isGliding()) {
                iterator.remove();
                continue;
            }
            if (drift.remainingTicks <= 0) {
                player.setGliding(false);
                iterator.remove();
                continue;
            }
            drift.remainingTicks--;
            Vector velocity = player.getVelocity();
            double[] side = ElytraFailurePolicy.sideways(velocity.getX(), velocity.getZ(), drift.wing());
            if (side[0] == 0.0 && side[1] == 0.0) {
                Vector look = player.getLocation().getDirection();
                side = ElytraFailurePolicy.sideways(look.getX(), look.getZ(), drift.wing());
            }
            player.setVelocity(velocity.add(new Vector(
                side[0] * settings.driftStrength(), -0.04, side[1] * settings.driftStrength())));
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    boolean isDrifting(UUID playerId) {
        return drifts.containsKey(playerId);
    }

    void forget(UUID playerId) {
        glidingTicks.remove(playerId);
        drifts.remove(playerId);
    }

    void shutdown() {
        glidingTicks.clear();
        drifts.clear();
    }

    private void fail(Player player, ItemStack chest, ElytraWing wing) {
        markBroken(chest, wing);
        player.getInventory().setChestplate(chest);
        glidingTicks.remove(player.getUniqueId());
        drifts.put(player.getUniqueId(), new Drift(player, wing, settings.driftTicks()));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.6f);
        player.getWorld().spawnParticle(Particle.ITEM, player.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05,
            new ItemStack(Material.PHANTOM_MEMBRANE));
        player.sendTitle(ChatColor.RED + "¡Falla mecánica!",
            ChatColor.GOLD + "Se te rompió el ala " + wing.displayName(), 0, 40, 10);
        player.sendMessage(ChatColor.RED + "Tu elytra perdió el ala " + wing.displayName()
            + ". Reparala en un yunque con membrana de phantom.");
    }

    private static final class Drift {
        private final Player player;
        private final ElytraWing wing;
        private int remainingTicks;

        private Drift(Player player, ElytraWing wing, int remainingTicks) {
            this.player = player;
            this.wing = wing;
            this.remainingTicks = remainingTicks;
        }

        Player player() {
            return player;
        }

        ElytraWing wing() {
            return wing;
        }
    }
}
