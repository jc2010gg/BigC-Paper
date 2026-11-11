package dev.linqfy.bigCasares.modules.mobscaling;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enemigos especiales del modulo mob-scaling:
 * <ul>
 *     <li>Zombie bebe lancero: jinete sobre un conejo ultra rapido con lanza de netherita Filo V.</li>
 *     <li>Zombie con perlas: si el jugador huye, arroja perlas de ender para cerrarle el paso.</li>
 *     <li>Creeper nuclear: 0.00001 % en tormenta electrica, explosion titanica y radiacion venenosa.</li>
 * </ul>
 */
public final class SpecialEnemies implements Listener {

    static final String RABBIT_LANCER = "rabbit_lancer";
    static final String LANCER_MOUNT = "lancer_mount";
    static final String PEARL_ZOMBIE = "pearl_zombie";
    static final String NUCLEAR_CREEPER = "nuclear_creeper";

    /** Steering runs every task period; pearl decisions run every {@link #PEARL_EVERY_RUNS} runs (0.5 s). */
    static final long TASK_PERIOD_TICKS = 5L;
    private static final int PEARL_EVERY_RUNS = 2;
    private static final double PEARL_LEAD_BLOCKS = 4.0;
    private static final double LANCER_HOP_SPEED = 0.55;
    private static final double LANCER_HOP_HEIGHT = 0.35;
    private static final double LANCER_SEARCH_RADIUS = 16.0;
    private static final double RADIATION_WARNING_RADIUS = 64.0;

    private final JavaPlugin plugin;
    private final SpecialEnemySettings settings;
    private final NamespacedKey enemyKey;
    private final NamespacedKey pearlOwnerKey;

    private final Map<LivingEntity, String> tracked = new ConcurrentHashMap<>();
    private final Map<LivingEntity, PearlState> pearlStates = new ConcurrentHashMap<>();
    private long runs;

    public SpecialEnemies(JavaPlugin plugin, SpecialEnemySettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.enemyKey = new NamespacedKey(plugin, "special_enemy");
        this.pearlOwnerKey = new NamespacedKey(plugin, "special_enemy_pearl");
    }

    public SpecialEnemySettings settings() {
        return settings;
    }

    // ---------------------------------------------------------------- creation

    /** Turns a freshly spawned zombie into a baby lancer and mounts it on a fast rabbit on the next tick. */
    public void makeRabbitLancer(Zombie zombie) {
        mark(zombie, RABBIT_LANCER);
        zombie.setBaby();

        ItemStack spear = new ItemStack(Material.NETHERITE_SPEAR);
        spear.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(spear);
            equipment.setItemInMainHandDropChance(settings.spearDropChance());
        }

        // the spawning zombie is not in the world yet, so riding has to wait a tick
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!zombie.isValid() || zombie.isDead()) {
                return;
            }
            Rabbit rabbit = zombie.getWorld().spawn(zombie.getLocation(), Rabbit.class, mount -> {
                mark(mount, LANCER_MOUNT);
                mount.setRemoveWhenFarAway(true);
                setBase(mount, Attribute.MOVEMENT_SPEED, settings.rabbitSpeed());
                setBase(mount, Attribute.MAX_HEALTH, 10.0);
                mount.setHealth(10.0);
                mount.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 2, false, false));
            });
            rabbit.addPassenger(zombie);
            tracked.put(rabbit, LANCER_MOUNT);
        });
    }

    /** Gives a zombie ender pearls it throws ahead of players who try to run away. */
    public void makePearlZombie(Zombie zombie) {
        mark(zombie, PEARL_ZOMBIE);
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setItemInOffHand(new ItemStack(Material.ENDER_PEARL));
            equipment.setItemInOffHandDropChance(0.5F);
        }
        tracked.put(zombie, PEARL_ZOMBIE);
    }

    private void makeNuclear(Creeper creeper) {
        mark(creeper, NUCLEAR_CREEPER);
        creeper.setPowered(true);
        creeper.customName(Component.text("☢ Creeper Nuclear ☢", NamedTextColor.GREEN));
        creeper.setCustomNameVisible(true);
        creeper.setRemoveWhenFarAway(false);
        creeper.setGlowing(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!settings.nuclearCreeperEnabled()
                || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                || !(event.getEntity() instanceof Creeper creeper)) {
            return;
        }
        if (SpecialEnemyMath.rollNuclear(creeper.getWorld().isThundering(), settings.nuclearChance(),
                ThreadLocalRandom.current().nextDouble())) {
            makeNuclear(creeper);
        }
    }

    /** Chunks reloading after a restart bring marked enemies back into the tick loop. */
    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        String kind = kindOf(living);
        if (LANCER_MOUNT.equals(kind) || PEARL_ZOMBIE.equals(kind)) {
            tracked.put(living, kind);
        }
    }

    // ---------------------------------------------------------------- nuclear creeper

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (NUCLEAR_CREEPER.equals(kindOf(event.getEntity()))) {
            event.setRadius(settings.nuclearExplosionPower());
            event.setFire(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!NUCLEAR_CREEPER.equals(kindOf(event.getEntity()))) {
            return;
        }
        Location center = event.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 4.0F, 0.5F);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 6, 3.0, 2.0, 3.0, 0.0);
        if (settings.radiationRadius() > 0 && settings.radiationSeconds() > 0) {
            int durationTicks = settings.radiationSeconds() * 20;
            world.spawn(center, AreaEffectCloud.class, cloud -> {
                cloud.setRadius(settings.radiationRadius());
                cloud.setDuration(durationTicks);
                cloud.setWaitTime(0);
                cloud.setReapplicationDelay(20);
                cloud.setRadiusOnUse(0.0F);
                cloud.setRadiusPerTick(0.0F);
                cloud.setColor(Color.LIME);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 2), true);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0), true);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 1), true);
            });
        }
        double warn = RADIATION_WARNING_RADIUS * RADIATION_WARNING_RADIUS;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= warn) {
                player.sendMessage("§a§l☢ §c¡Un Creeper Nuclear ha detonado! §7La zona quedó radiactiva.");
            }
        }
    }

    // ---------------------------------------------------------------- pearl zombie

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof EnderPearl pearl)
                || !pearl.getPersistentDataContainer().has(pearlOwnerKey, PersistentDataType.STRING)) {
            return;
        }
        // the zombie is moved by hand so vanilla pearl handling never touches it
        event.setCancelled(true);
        Location landing = landingLocation(event, pearl);
        pearl.remove();

        if (!(pearl.getShooter() instanceof Zombie zombie) || !zombie.isValid() || zombie.isDead()
                || landing.getWorld() == null || !landing.getWorld().equals(zombie.getWorld())) {
            return;
        }
        Location from = zombie.getLocation();
        LivingEntity target = zombie.getTarget();
        if (target != null) {
            Vector look = target.getLocation().toVector().subtract(landing.toVector());
            landing.setDirection(look.lengthSquared() > 1.0E-6 ? look : from.getDirection());
        } else {
            landing.setYaw(from.getYaw());
            landing.setPitch(from.getPitch());
        }
        zombie.teleport(landing);
        World world = zombie.getWorld();
        world.spawnParticle(Particle.PORTAL, from, 30, 0.4, 1.0, 0.4, 0.2);
        world.spawnParticle(Particle.PORTAL, landing, 30, 0.4, 1.0, 0.4, 0.2);
        world.playSound(landing, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
    }

    private static Location landingLocation(ProjectileHitEvent event, EnderPearl pearl) {
        Block hitBlock = event.getHitBlock();
        if (hitBlock != null && event.getHitBlockFace() != null) {
            return hitBlock.getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        }
        return pearl.getLocation();
    }

    private void tickPearlZombie(Zombie zombie) {
        Player player = zombie.getTarget() instanceof Player p ? p : null;
        if (player == null || !isVulnerable(player) || !player.getWorld().equals(zombie.getWorld())) {
            pearlStates.remove(zombie);
            return;
        }
        PearlState state = pearlStates.computeIfAbsent(zombie, ignored -> new PearlState());
        Vector playerPos = player.getLocation().toVector();
        double distance = playerPos.distance(zombie.getLocation().toVector());
        Vector movement = state.lastPlayerPos == null ? new Vector() : playerPos.clone().subtract(state.lastPlayerPos);
        boolean fleeing = SpecialEnemyMath.isFleeing(state.lastDistance, distance,
                settings.pearlMinDistance(), settings.pearlMaxDistance());
        state.lastPlayerPos = playerPos;
        state.lastDistance = distance;

        if (!fleeing || runs < state.nextThrowRun || !zombie.hasLineOfSight(player)) {
            return;
        }
        Vector origin = zombie.getEyeLocation().toVector();
        Vector landing = SpecialEnemyMath.interceptPoint(zombie.getLocation().toVector(), playerPos, movement, PEARL_LEAD_BLOCKS);
        EnderPearl pearl = zombie.launchProjectile(EnderPearl.class, SpecialEnemyMath.pearlLaunchVelocity(origin, landing));
        pearl.getPersistentDataContainer().set(pearlOwnerKey, PersistentDataType.STRING, zombie.getUniqueId().toString());
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1.0F, 0.6F);
        state.nextThrowRun = runs + (settings.pearlCooldownSeconds() * 20L) / TASK_PERIOD_TICKS;
    }

    // ---------------------------------------------------------------- rabbit lancer

    private boolean tickLancerMount(Rabbit rabbit) {
        Zombie rider = null;
        for (Entity passenger : rabbit.getPassengers()) {
            if (passenger instanceof Zombie zombie && RABBIT_LANCER.equals(kindOf(zombie))) {
                rider = zombie;
                break;
            }
        }
        if (rider == null) {
            return false; // jinete muerto o desmontado: queda un conejo comun
        }
        rabbit.setFallDistance(0.0F);
        Player target = rider.getTarget() instanceof Player p && isVulnerable(p) ? p : null;
        if (target == null) {
            target = nearestVulnerablePlayer(rabbit, LANCER_SEARCH_RADIUS);
            if (target == null) {
                return true;
            }
            rider.setTarget(target);
        }
        if (!rabbit.isOnGround() || !target.getWorld().equals(rabbit.getWorld())) {
            return true;
        }
        Vector toTarget = target.getLocation().toVector().subtract(rabbit.getLocation().toVector()).setY(0);
        if (toTarget.lengthSquared() < 1.5) {
            return true;
        }
        rabbit.setRotation((float) Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ())), 0.0F);
        rabbit.setVelocity(toTarget.normalize().multiply(LANCER_HOP_SPEED).setY(LANCER_HOP_HEIGHT));
        return true;
    }

    // ---------------------------------------------------------------- loop

    /** Repeating task body; scheduled by the module every {@link #TASK_PERIOD_TICKS} ticks. */
    public void tick() {
        runs++;
        boolean pearlRun = runs % PEARL_EVERY_RUNS == 0;
        Iterator<Map.Entry<LivingEntity, String>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, String> entry = it.next();
            LivingEntity entity = entry.getKey();
            if (!entity.isValid() || entity.isDead()) {
                it.remove();
                pearlStates.remove(entity);
                continue;
            }
            if (entity instanceof Rabbit rabbit && LANCER_MOUNT.equals(entry.getValue())) {
                if (!tickLancerMount(rabbit)) {
                    it.remove();
                }
            } else if (pearlRun && entity instanceof Zombie zombie && PEARL_ZOMBIE.equals(entry.getValue())) {
                tickPearlZombie(zombie);
            }
        }
    }

    public void clear() {
        tracked.clear();
        pearlStates.clear();
    }

    // ---------------------------------------------------------------- helpers

    private void mark(LivingEntity entity, String kind) {
        entity.getPersistentDataContainer().set(enemyKey, PersistentDataType.STRING, kind);
    }

    private String kindOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.get(enemyKey, PersistentDataType.STRING);
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static boolean isVulnerable(Player player) {
        GameMode mode = player.getGameMode();
        return player.isValid() && !player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    private static Player nearestVulnerablePlayer(Entity entity, double radius) {
        Player nearest = null;
        double best = radius * radius;
        for (Entity nearby : entity.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player && isVulnerable(player)) {
                double distance = player.getLocation().distanceSquared(entity.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    private static final class PearlState {
        private Vector lastPlayerPos;
        private double lastDistance = Double.NaN;
        private long nextThrowRun;
    }
}
