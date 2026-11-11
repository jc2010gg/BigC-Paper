package dev.linqfy.bigCasares.modules.casino.bar;

import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoKeys;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Timed side effects of the bar's drinks that potions cannot express: smoke, slurred chat, vomiting, an
 * invisible inventory and hallucinations only the drinker can see.
 */
public final class DrinkEffects {

    private static final EntityType[] PHANTOMS = {EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SKELETON,
        EntityType.ENDERMAN, EntityType.SPIDER, EntityType.WITCH};

    private final DisplayKit kit;
    private final CasinoKeys keys;
    private final RandomGenerator random;
    private final Map<UUID, Long> smokeUntil = new HashMap<>();
    private final Map<UUID, Long> slurUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> vomitUntil = new HashMap<>();
    private final Map<UUID, Long> tripUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, List<Entity>> phantoms = new HashMap<>();
    private volatile long tick;

    public DrinkEffects(DisplayKit kit, CasinoKeys keys, RandomGenerator random) {
        this.kit = kit;
        this.keys = keys;
        this.random = random;
    }

    public void drink(Player player, DrinkType type) {
        UUID id = player.getUniqueId();
        switch (type) {
            case WHISKY_NETHERRACK -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 60, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 60, 0));
                smokeUntil.put(id, tick + 20 * 60);
                player.sendMessage("§6*glup* §7Te arde hasta el alma. Ves todo borroso.");
            }
            case CERVEZA_CALIENTE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 20, 0));
                slurUntil.put(id, tick + 20 * 20);
                player.sendMessage("§e*glup* §7Tibia y sin gas. Se te traba la lengua.");
            }
            case COCTEL_RUINA -> {
                if (random.nextBoolean()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 60 * 5, 2));
                    player.sendMessage("§d*glup* §a¡Te sentís imparable! §7Prisa minera III.");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 12, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 12, 0));
                    vomitUntil.put(id, tick + 20 * 6);
                    player.sendMessage("§d*glup* §2Uy. No. No, no, no...");
                }
            }
            case VASO_POLVO_BLANCO -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 5, 2));
                tripUntil.put(id, tick + 20 * 60 * 5);
                hideInventory(player);
                player.sendMessage("§f*snif* §7Todo va muy rápido. ¿Dónde están tus cosas? ¿Quién está ahí?");
            }
        }
    }

    public boolean slurring(UUID player) {
        return slurUntil.getOrDefault(player, 0L) > tick;
    }

    public boolean tripping(UUID player) {
        return tripUntil.getOrDefault(player, 0L) > tick;
    }

    /** Called from the async chat thread. */
    public String slur(String message) {
        return Babble.slur(message, java.util.concurrent.ThreadLocalRandom.current());
    }

    public void tick() {
        tick++;
        expire(smokeUntil);
        expire(slurUntil);
        expire(vomitUntil);
        for (Map.Entry<UUID, Long> entry : List.copyOf(tripUntil.entrySet())) {
            if (entry.getValue() <= tick) {
                tripUntil.remove(entry.getKey());
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    restoreInventory(player);
                    player.sendMessage("§7Se te pasó. Tus cosas estaban ahí todo el tiempo.");
                }
                clearPhantoms(entry.getKey());
            }
        }
        if (tick % 5 == 0) {
            for (UUID id : smokeUntil.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getEyeLocation(), 2,
                        0.3, 0.2, 0.3, 0.005);
                }
            }
        }
        if (tick % 2 == 0) {
            for (UUID id : vomitUntil.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    vomit(player);
                }
            }
        }
        if (tick % 20 == 0) {
            for (UUID id : tripUntil.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                hideInventory(player);
                if (random.nextInt(5) == 0) {
                    hallucinate(player);
                }
            }
        }
        if (tick % 10 == 0) {
            fadePhantoms();
        }
    }

    /** Makes every item render as nothing and hides its tooltip. Undone by {@link #restoreInventory}. */
    public void hideInventory(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }
            PersistentDataContainer data = meta.getPersistentDataContainer();
            if (data.has(keys.hiddenModel, PersistentDataType.STRING)) {
                continue;
            }
            // Only an explicit model override is remembered; items on their default model get it reset later.
            Key model = item.isDataOverridden(DataComponentTypes.ITEM_MODEL) ? item.getData(DataComponentTypes.ITEM_MODEL) : null;
            data.set(keys.hiddenModel, PersistentDataType.STRING, model == null ? "" : model.asString());
            // A custom tooltip setting is left alone rather than lost; only plain tooltips get hidden.
            boolean ownTooltip = item.isDataOverridden(DataComponentTypes.TOOLTIP_DISPLAY);
            data.set(keys.hiddenTooltip, PersistentDataType.BYTE, (byte) (ownTooltip ? 1 : 0));
            item.setItemMeta(meta);
            item.setData(DataComponentTypes.ITEM_MODEL, CasinoItems.HIDDEN_MODEL);
            if (!ownTooltip) {
                item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
            }
            inventory.setItem(slot, item);
        }
    }

    public void restoreInventory(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (restore(item)) {
                inventory.setItem(slot, item);
            }
        }
        player.updateInventory();
    }

    /** Puts back the model of one item that was hidden. Safe to call on any item. */
    public boolean restore(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String model = data.get(keys.hiddenModel, PersistentDataType.STRING);
        if (model == null) {
            return false;
        }
        Byte ownTooltip = data.get(keys.hiddenTooltip, PersistentDataType.BYTE);
        data.remove(keys.hiddenModel);
        data.remove(keys.hiddenTooltip);
        item.setItemMeta(meta);
        if (model.isEmpty()) {
            item.resetData(DataComponentTypes.ITEM_MODEL);
        } else {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        }
        if (ownTooltip == null || ownTooltip == 0) {
            item.resetData(DataComponentTypes.TOOLTIP_DISPLAY);
        }
        return true;
    }

    public void forget(Player player) {
        UUID id = player.getUniqueId();
        restoreInventory(player);
        smokeUntil.remove(id);
        slurUntil.remove(id);
        vomitUntil.remove(id);
        tripUntil.remove(id);
        clearPhantoms(id);
    }

    public void shutdown() {
        for (UUID id : List.copyOf(tripUntil.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restoreInventory(player);
            }
        }
        for (UUID id : List.copyOf(phantoms.keySet())) {
            clearPhantoms(id);
        }
        tripUntil.clear();
    }

    private void vomit(Player player) {
        Location mouth = player.getEyeLocation().subtract(0, 0.25, 0);
        Vector direction = mouth.getDirection().multiply(0.4);
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(0x6B8E23), 1.2f);
        for (int index = 0; index < 6; index++) {
            Location at = mouth.clone().add(direction.clone().multiply(1 + index * 0.4)).add(0, -index * 0.12, 0);
            player.getWorld().spawnParticle(Particle.DUST, at, 3, 0.05, 0.05, 0.05, 0, green);
        }
        player.getWorld().spawnParticle(Particle.ITEM, mouth.clone().add(direction), 4, 0.05, 0.05, 0.05, 0.08,
            new ItemStack(Material.SLIME_BALL));
        if (tick % 10 == 0) {
            player.getWorld().playSound(mouth, Sound.ENTITY_GENERIC_DRINK, 0.6f, 0.5f);
        }
    }

    /** A figure only the drinker sees, at the edge of their view. It vanishes when they get close. */
    private void hallucinate(Player player) {
        List<Entity> mine = phantoms.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        if (mine.size() >= 3) {
            return;
        }
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        double angle = Math.toRadians((random.nextBoolean() ? 1 : -1) * (45 + random.nextInt(40)));
        Location at = player.getLocation().add(look.normalize().rotateAroundY(angle).multiply(7 + random.nextInt(6)));
        at.setY(player.getLocation().getY());
        at.setDirection(player.getLocation().toVector().subtract(at.toVector()));
        Entity phantom;
        if (random.nextBoolean() && Bukkit.getOnlinePlayers().size() > 1) {
            List<? extends Player> others = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.equals(player)).toList();
            Player victim = others.get(random.nextInt(others.size()));
            phantom = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
                prepare(mannequin);
                mannequin.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(
                    victim.getPlayerProfile()));
                mannequin.customName(CasinoText.legacy("§f" + victim.getName()));
                mannequin.setCustomNameVisible(true);
                mannequin.setDescription(net.kyori.adventure.text.Component.empty());
            });
        } else {
            EntityType type = PHANTOMS[random.nextInt(PHANTOMS.length)];
            java.util.function.Consumer<Entity> setup = entity -> {
                prepare((LivingEntity) entity);
                if (entity instanceof Mob mob) {
                    mob.setAware(false);
                }
            };
            phantom = at.getWorld().spawn(at, type.getEntityClass(), setup);
        }
        kit.showOnlyTo(phantom, player);
        mine.add(phantom);
        player.playSound(at, random.nextBoolean() ? Sound.ENTITY_ZOMBIE_AMBIENT : Sound.AMBIENT_CAVE, 0.6f, 0.8f);
    }

    private void prepare(LivingEntity entity) {
        kit.tag(entity);
        entity.setPersistent(false);
        entity.setVisibleByDefault(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setAI(false);
        entity.setCollidable(false);
        entity.setGravity(false);
    }

    private void fadePhantoms() {
        for (Map.Entry<UUID, List<Entity>> entry : phantoms.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            entry.getValue().removeIf(phantom -> {
                boolean gone = player == null || !phantom.isValid() || phantom.getWorld() != player.getWorld()
                    || phantom.getLocation().distanceSquared(player.getLocation()) < 16 || phantom.getTicksLived() > 120;
                if (gone && phantom.isValid()) {
                    phantom.getWorld().spawnParticle(Particle.SMOKE, phantom.getLocation().add(0, 1, 0), 8, 0.2, 0.5, 0.2, 0.01);
                    phantom.remove();
                }
                return gone;
            });
        }
    }

    private void clearPhantoms(UUID player) {
        List<Entity> mine = phantoms.remove(player);
        if (mine != null) {
            mine.forEach(Entity::remove);
        }
    }

    private void expire(Map<UUID, Long> timers) {
        timers.values().removeIf(until -> until <= tick);
    }
}
