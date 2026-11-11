package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

public class MobScalingListener implements Listener {

    private final JavaPlugin plugin;
    private final MobScalingSettings settings;
    private final NamespacedKey scaledKey;
    private final NamespacedKey customEntityKey;
    private final NamespacedKey jeremyTargetKey;
    private final NamespacedKey bloodMoonExtraKey;
    private final NamespacedKey bloodMoonOwnerKey;
    private final NamespacedKey acidRainMobKey;
    private final NamespacedKey acidRainTypeKey;
    private final NamespacedKey airdropDefenderKey;
    private final NamespacedKey airdropIdKey;
    private final NamespacedKey pveBossKey;
    private final NamespacedKey sahurBossKey;
    private final NamespacedKey sahurBatKey;
    private final NamespacedKey shopNpcKey;
    private final NamespacedKey nexusKey;
    private final NamespacedKey tombstoneKey;
    private final MobScalingMutations mutations;

    MobScalingListener(JavaPlugin plugin, MobScalingSettings settings, MobScalingMutations mutations) {
        this.plugin = plugin;
        this.settings = settings;
        this.scaledKey = key(plugin, "mob_scaled");
        this.customEntityKey = key(plugin, "custom_entity");
        this.jeremyTargetKey = key(plugin, "jeremy_target");
        this.bloodMoonExtraKey = key(plugin, "blood_moon_extra");
        this.bloodMoonOwnerKey = key(plugin, "blood_moon_owner");
        this.acidRainMobKey = key(plugin, "acidrain_mob");
        this.acidRainTypeKey = key(plugin, "acidrain_mob_type");
        this.airdropDefenderKey = key(plugin, "airdrop_defender");
        this.airdropIdKey = key(plugin, "airdrop_id");
        this.pveBossKey = key(plugin, "pve_boss_id");
        this.sahurBossKey = key(plugin, "sahur_boss_id");
        this.sahurBatKey = key(plugin, "sahur_bat_id");
        this.shopNpcKey = key(plugin, "shop_npc_id");
        this.nexusKey = key(plugin, "nexus_id");
        this.tombstoneKey = key(plugin, "tombstone_id");
        this.mutations = mutations;
    }

    private static NamespacedKey key(JavaPlugin plugin, String name) {
        return plugin != null ? new NamespacedKey(plugin, name) : new NamespacedKey("bigcasares", name);
    }

    public MobScalingMutations getMutations() {
        return mutations;
    }

    boolean isEventOrSystemMob(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc == null) {
            return false;
        }

        // Jeremy and custom entities
        if ("jeremy".equals(pdc.get(customEntityKey, PersistentDataType.STRING))
                || pdc.has(customEntityKey, PersistentDataType.STRING)
                || pdc.has(jeremyTargetKey, PersistentDataType.STRING)) {
            return true;
        }

        // Blood Moon spawned extra hostiles
        if (pdc.has(bloodMoonExtraKey, PersistentDataType.BYTE)
                || pdc.has(bloodMoonOwnerKey, PersistentDataType.STRING)) {
            return true;
        }

        // Acid Rain spawned environmental mobs
        if (pdc.has(acidRainMobKey, PersistentDataType.BYTE)
                || pdc.has(acidRainTypeKey, PersistentDataType.STRING)) {
            return true;
        }

        // AirDrop defender wave mobs
        if (pdc.has(airdropDefenderKey, PersistentDataType.BYTE)
                || pdc.has(airdropIdKey, PersistentDataType.STRING)) {
            return true;
        }

        // PvE Bosses and special summons
        if (pdc.has(pveBossKey, PersistentDataType.STRING)
                || pdc.has(sahurBossKey, PersistentDataType.STRING)
                || pdc.has(sahurBatKey, PersistentDataType.STRING)) {
            return true;
        }

        // Shop NPCs
        if (pdc.has(shopNpcKey, PersistentDataType.STRING)) {
            return true;
        }

        // Nexus anchors and visual entities
        if (pdc.has(nexusKey, PersistentDataType.STRING)) {
            return true;
        }

        // Tombstones
        if (pdc.has(tombstoneKey, PersistentDataType.STRING)) {
            return true;
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        if (isEventOrSystemMob(entity)) {
            return;
        }

        // Only scale Monsters and Trojan targets (Pig, Cow, etc.)
        boolean isMonster = entity instanceof Monster || entity instanceof Slime || entity instanceof Ghast || entity instanceof Phantom;
        boolean isTrojan = entity instanceof Pig || entity instanceof Cow || entity instanceof Squid || entity instanceof GlowSquid || entity instanceof Bat
                || entity instanceof Sheep || entity instanceof Chicken || entity instanceof Bee || entity instanceof Villager || entity instanceof Llama;
        
        if (!isMonster && !isTrojan) {
            return;
        }

        // Prevent double scaling
        if (entity.getPersistentDataContainer().has(scaledKey, PersistentDataType.BYTE)) {
            return;
        }
        entity.getPersistentDataContainer().set(scaledKey, PersistentDataType.BYTE, (byte) 1);

        long days = ChronoUnit.DAYS.between(settings.startDate(), LocalDate.now());
        if (days < 0) days = 0;

        // Apply health and damage multipliers
        double multiplier = settings.multiplier(days);

        scaleAttribute(entity, Attribute.MAX_HEALTH, multiplier);
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.setHealth(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        
        scaleAttribute(entity, Attribute.ATTACK_DAMAGE, multiplier);

        // Check for mutation
        if (mutations != null) {
            double chance = Math.min(settings.maxMutationChance(), settings.baseMutationChance() + (settings.mutationChancePerDay() * days));
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                mutations.applyRandomMutation(entity);
            }
        }
    }

    private void scaleAttribute(LivingEntity entity, Attribute attribute, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            double base = instance.getBaseValue();
            instance.setBaseValue(base * multiplier);
        }
    }
}
