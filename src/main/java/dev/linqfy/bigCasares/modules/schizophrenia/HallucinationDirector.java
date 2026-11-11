package dev.linqfy.bigCasares.modules.schizophrenia;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Plays hallucinations. Everything here is sent to the victim only: per-player sounds, particles,
 * fake blocks, fake equipment, a private world border and mannequins hidden from everyone else.
 * Nothing touches real blocks, inventories or health.
 */
final class HallucinationDirector {

    static final String PHANTOM_TAG = "bigcasares_schizo_phantom";

    private static final List<Material> ROT_BLOCKS = List.of(
        Material.NETHER_WART_BLOCK, Material.CRYING_OBSIDIAN, Material.SCULK,
        Material.RED_CONCRETE, Material.MAGMA_BLOCK, Material.BLACK_CONCRETE, Material.REDSTONE_BLOCK);
    private static final Set<Material> LIGHT_SOURCES = Set.of(
        Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH,
        Material.LANTERN, Material.SOUL_LANTERN, Material.GLOWSTONE, Material.SEA_LANTERN,
        Material.JACK_O_LANTERN, Material.SHROOMLIGHT, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.END_ROD, Material.COPPER_TORCH, Material.COPPER_WALL_TORCH, Material.COPPER_LANTERN);
    private static final List<Material> MASKS = List.of(
        Material.WITHER_SKELETON_SKULL, Material.SKELETON_SKULL, Material.CARVED_PUMPKIN,
        Material.ZOMBIE_HEAD, Material.CREEPER_HEAD);
    private static final List<Material> WEAPONS = List.of(
        Material.IRON_SWORD, Material.IRON_AXE, Material.DIAMOND_SWORD, Material.CROSSBOW, Material.TRIDENT);
    private static final List<Sound> ROARS = List.of(
        Sound.ENTITY_WARDEN_ROAR, Sound.ENTITY_RAVAGER_ROAR, Sound.ENTITY_ENDER_DRAGON_GROWL,
        Sound.ENTITY_GHAST_SCREAM, Sound.ENTITY_WITHER_AMBIENT);
    private static final List<Sound> MOB_SOUNDS = List.of(
        Sound.ENTITY_ZOMBIE_AMBIENT, Sound.ENTITY_SPIDER_AMBIENT, Sound.ENTITY_ENDERMAN_STARE,
        Sound.ENTITY_WOLF_GROWL, Sound.ENTITY_TNT_PRIMED, Sound.ENTITY_CAT_HISS,
        Sound.ENTITY_SKELETON_HORSE_AMBIENT, Sound.ENTITY_PHANTOM_AMBIENT, Sound.ENTITY_VEX_CHARGE);
    private static final Particle.DustOptions SHADOW = new Particle.DustOptions(Color.fromRGB(8, 8, 10), 1.6f);
    private static final Particle.DustOptions EYES = new Particle.DustOptions(Color.fromRGB(255, 20, 20), 0.7f);

    private final Plugin plugin;
    private final SchizophreniaSettings settings;
    private final RandomGenerator random;
    private final Map<Hallucination, Effect> effects = new EnumMap<>(Hallucination.class);
    private final VoidRealm voidRealm;

    @FunctionalInterface
    private interface Effect {
        boolean play(Player player, Episode episode, long now, int intensity);
    }

    HallucinationDirector(Plugin plugin, SchizophreniaSettings settings, RandomGenerator random, VoidRealm voidRealm) {
        this.plugin = plugin;
        this.voidRealm = voidRealm;
        this.settings = settings;
        this.random = random;
        effects.put(Hallucination.FOOTSTEPS, this::footsteps);
        effects.put(Hallucination.CREEPER_HISS, this::creeperHiss);
        effects.put(Hallucination.DISTANT_ROAR, this::distantRoar);
        effects.put(Hallucination.WHISPERS, this::whispers);
        effects.put(Hallucination.PHANTOM_CLONE, (p, e, now, i) -> spawnPhantom(p, e, now, PhantomClone.Behaviour.STARE,
            behind(p, between(settings.phantoms().minDistance(), settings.phantoms().maxDistance()), random.nextDouble()),
            randomIdentity(p), settings.phantoms().lifetimeSeconds() * 20L) != null);
        effects.put(Hallucination.SILHOUETTE, this::silhouette);
        effects.put(Hallucination.MINING_BEHIND, this::miningBehind);
        effects.put(Hallucination.MOB_SOUNDS, this::mobSounds);
        effects.put(Hallucination.DOOR_KNOCKING, this::doorKnocking);
        effects.put(Hallucination.HEARTBEAT, this::heartbeat);
        effects.put(Hallucination.FAKE_HURT, this::fakeHurt);
        effects.put(Hallucination.FAKE_CHAT, this::fakeChat);
        effects.put(Hallucination.DOPPELGANGER, this::doppelganger);
        effects.put(Hallucination.BLOCK_ROT, this::blockRot);
        effects.put(Hallucination.LIGHTS_OUT, this::lightsOut);
        effects.put(Hallucination.MASKED_PLAYERS, this::maskedPlayers);
        effects.put(Hallucination.RED_VIGNETTE, this::redVignette);
        effects.put(Hallucination.STALKER, (p, e, now, i) -> spawnPhantom(p, e, now, PhantomClone.Behaviour.STALKER,
            behind(p, settings.phantoms().maxDistance(), random.nextDouble() * 0.4), randomIdentity(p), 90 * 20L) != null);
        effects.put(Hallucination.GLITCH_TITLE, this::glitchTitle);
        effects.put(Hallucination.FAKE_EXPLOSION, this::fakeExplosion);
        effects.put(Hallucination.CAMERA_SNAP, this::cameraSnap);
        effects.put(Hallucination.ELDER_GUARDIAN, this::elderGuardian);
        effects.put(Hallucination.ETERNAL_NIGHT, this::eternalNight);
        effects.put(Hallucination.FAKE_THUNDER, this::fakeThunder);
        effects.put(Hallucination.SWARM, this::swarm);
        effects.put(Hallucination.FAKE_DEATH, this::fakeDeath);
        effects.put(Hallucination.MUSIC_BOX, this::musicBox);
        effects.put(Hallucination.WALL_WRITING, this::wallWriting);
        effects.put(Hallucination.RIGHT_BEHIND_YOU, this::rightBehindYou);
        effects.put(Hallucination.THE_VISITOR, this::theVisitor);
        effects.put(Hallucination.CHASE, this::chase);
        effects.put(Hallucination.YOUR_NAME, this::yourName);
        effects.put(Hallucination.CHAT_FLOOD, this::chatFlood);
        effects.put(Hallucination.SCREEN_SHAKE, this::screenShake);
        effects.put(Hallucination.BLOOD_RAIN, this::bloodRain);
        effects.put(Hallucination.CROWD, this::crowd);
        effects.put(Hallucination.HUNTED, this::hunted);
        effects.put(Hallucination.CAMERA_SPIN, this::cameraSpin);
        effects.put(Hallucination.FLESH_ROOM, this::fleshRoom);
        effects.put(Hallucination.BREAKDOWN, this::breakdown);
        effects.put(Hallucination.VOID_VISIT, this::voidVisit);
        effects.put(Hallucination.ALTERNATE, this::alternate);
        effects.put(Hallucination.INTRUDER_ALERT, this::intruderAlert);
        effects.put(Hallucination.FACE_FLASH, this::faceFlash);
    }

    /** Plays the hallucination; returns false when the surroundings could not support it. */
    boolean play(Hallucination hallucination, Player player, Episode episode, long now, int intensity) {
        boolean played = effects.get(hallucination).play(player, episode, now, intensity);
        if (played && intensity >= 4 && random.nextDouble() < 0.15) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false, false));
        }
        return played;
    }

    // ---------------------------------------------------------------- audio

    private boolean footsteps(Player player, Episode episode, long now, int intensity) {
        Location start = behind(player, 14 + random.nextInt(6), random.nextDouble() * 0.3);
        footstep(player, episode, start, now, 6, 0);
        return true;
    }

    private void footstep(Player player, Episode episode, Location at, long tick, int delay, int step) {
        episode.timeline().at(tick + delay, () -> {
            if (!player.isOnline() || at.getWorld() != player.getWorld()) {
                return;
            }
            Location target = player.getLocation();
            double distance = at.distance(target);
            if (distance < 1.6 || step > 40) {
                arrival(player);
                return;
            }
            playStepSound(player, at);
            Vector towards = target.toVector().subtract(at.toVector()).setY(0);
            if (towards.lengthSquared() > 1.0E-4) {
                towards.normalize().multiply(Math.min(1.1, distance));
            }
            Location next = at.clone().add(towards);
            footstep(player, episode, next, tick + delay, Math.max(2, delay - (step % 2 == 0 ? 1 : 0)), step + 1);
        });
    }

    private void arrival(Player player) {
        switch (random.nextInt(3)) {
            case 0 -> {
                player.sendHurtAnimation(random.nextFloat() * 360f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 0.8f);
            }
            case 1 -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1f, 0.6f);
                whisperChat(player, "detrás tuyo");
            }
            default -> {
                // The steps simply stop. Silence is worse.
            }
        }
    }

    private void playStepSound(Player player, Location at) {
        Block ground = at.getBlock().getRelative(BlockFace.DOWN);
        Sound step = ground.getType().isAir() ? Sound.BLOCK_STONE_STEP : ground.getBlockData().getSoundGroup().getStepSound();
        player.playSound(at, step, SoundCategory.PLAYERS, 0.9f, 0.85f + random.nextFloat() * 0.2f);
    }

    private boolean creeperHiss(Player player, Episode episode, long now, int intensity) {
        Location behind = behind(player, 1.3, random.nextDouble() * 0.2);
        player.playSound(behind, Sound.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1f, 0.5f);
        return true;
    }

    private boolean distantRoar(Player player, Episode episode, long now, int intensity) {
        Location far = around(player, 28 + random.nextInt(12));
        player.playSound(far, pick(ROARS), SoundCategory.HOSTILE, 3f, 0.45f + random.nextFloat() * 0.3f);
        return true;
    }

    private boolean whispers(Player player, Episode episode, long now, int intensity) {
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 0.5f + random.nextFloat() * 0.5f);
        int lines = 1 + random.nextInt(Math.max(1, intensity - 1));
        for (int i = 0; i < lines; i++) {
            String line = SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random);
            episode.timeline().at(now + 20L + i * 30L, () -> whisperChat(player, line));
        }
        return true;
    }

    private boolean miningBehind(Player player, Episode episode, long now, int intensity) {
        Location wall = behind(player, 3 + random.nextInt(3), random.nextDouble() * 0.5);
        int blocks = 2 + random.nextInt(3);
        long tick = now;
        for (int b = 0; b < blocks; b++) {
            for (int hit = 0; hit < 5; hit++) {
                tick += 4;
                episode.timeline().at(tick, () -> player.playSound(wall, Sound.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f, 0.9f));
            }
            tick += 4;
            episode.timeline().at(tick, () -> player.playSound(wall, Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1f, 0.9f));
            tick += 10;
        }
        return true;
    }

    private boolean mobSounds(Player player, Episode episode, long now, int intensity) {
        Sound sound = pick(MOB_SOUNDS);
        Location near = around(player, 2 + random.nextInt(5));
        player.playSound(near, sound, SoundCategory.HOSTILE, 1f, 0.7f + random.nextFloat() * 0.3f);
        if (random.nextBoolean()) {
            episode.timeline().at(now + 12, () -> {
                player.playSound(around(player, 8), Sound.ENTITY_SKELETON_SHOOT, SoundCategory.HOSTILE, 1f, 1f);
                episode.timeline().at(now + 22, () ->
                    player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, SoundCategory.HOSTILE, 1f, 1f));
            });
        }
        return true;
    }

    private boolean doorKnocking(Player player, Episode episode, long now, int intensity) {
        Location door = around(player, 3 + random.nextInt(4));
        if (random.nextBoolean()) {
            for (int i = 0; i < 3; i++) {
                episode.timeline().at(now + i * 14L, () ->
                    player.playSound(door, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 0.9f, 0.8f));
            }
        } else {
            player.playSound(door, Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.9f);
            episode.timeline().at(now + 40, () ->
                player.playSound(door, Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 0.9f));
            episode.timeline().at(now + 70, () -> playStepSound(player, around(player, 2)));
        }
        return true;
    }

    private boolean heartbeat(Player player, Episode episode, long now, int intensity) {
        long tick = now;
        int beats = 8 + intensity * 2;
        for (int beat = 0; beat < beats; beat++) {
            tick += Math.max(5, 18 - beat);
            boolean strong = beat % 2 == 0;
            episode.timeline().at(tick, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.2f, 1f);
                player.sendActionBar(Component.text(strong ? "♥ ♥ ♥" : "♥", NamedTextColor.DARK_RED));
            });
        }
        episode.timeline().at(tick + 10, () -> {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1f, 0.7f);
            player.sendActionBar(Component.empty());
        });
        return true;
    }

    private boolean fakeChat(Player player, Episode episode, long now, int intensity) {
        String name = randomName(player);
        switch (random.nextInt(3)) {
            case 0 -> player.sendMessage(Component.text(name + " te susurra: "
                + SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random), NamedTextColor.GRAY, TextDecoration.ITALIC));
            case 1 -> {
                player.sendMessage(Component.text(name + " se ha unido a la partida", NamedTextColor.YELLOW));
                episode.timeline().at(now + 60 + random.nextInt(80), () ->
                    player.sendMessage(Component.text(name + " ha abandonado la partida", NamedTextColor.YELLOW)));
            }
            default -> player.sendMessage(Component.text(player.getName() + " te susurra: "
                + SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random), NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        return true;
    }

    // ---------------------------------------------------------------- visuals

    private boolean silhouette(Player player, Episode episode, long now, int intensity) {
        Location at = ground(behind(player, between(10, 18), 0.6 + random.nextDouble() * 0.4));
        if (at == null) {
            return false;
        }
        int frames = 30 + intensity * 6;
        boolean[] gone = {false};
        for (int frame = 0; frame < frames; frame++) {
            episode.timeline().at(now + frame * 3L, () -> drawSilhouette(player, at, gone));
        }
        return true;
    }

    private void drawSilhouette(Player player, Location feet, boolean[] gone) {
        if (gone[0] || !player.isOnline() || feet.getWorld() != player.getWorld()) {
            return;
        }
        if (looksAt(player, feet.clone().add(0, 1.0, 0), 9.0)) {
            player.spawnParticle(Particle.LARGE_SMOKE, feet.clone().add(0, 1, 0), 12, 0.2, 0.6, 0.2, 0.01);
            gone[0] = true;
            return;
        }
        float yaw = GazeMath.yawTowards(feet.getX(), feet.getZ(), player.getX(), player.getZ());
        Vector right = new Vector(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw))).multiply(0.22);
        for (double y = 0.1; y < 1.45; y += 0.15) {
            double width = y > 0.75 ? 1.0 : 0.6;
            dust(player, feet.clone().add(right.clone().multiply(width)).add(0, y, 0), SHADOW);
            dust(player, feet.clone().add(right.clone().multiply(-width)).add(0, y, 0), SHADOW);
        }
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 5) {
            dust(player, feet.clone().add(Math.cos(a) * 0.25, 1.75 + Math.sin(a) * 0.25, Math.sin(a) * 0.05), SHADOW);
        }
        Vector face = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))).multiply(0.3);
        dust(player, feet.clone().add(face).add(right.clone().multiply(0.4)).add(0, 1.78, 0), EYES);
        dust(player, feet.clone().add(face).add(right.clone().multiply(-0.4)).add(0, 1.78, 0), EYES);
    }

    private boolean fakeHurt(Player player, Episode episode, long now, int intensity) {
        player.sendHurtAnimation(random.nextFloat() * 360f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
        fakeLowHealth(player, episode, now, 4 + random.nextInt(8));
        return true;
    }

    private void fakeLowHealth(Player player, Episode episode, long now, double lost) {
        double shown = Math.max(1.0, player.getHealth() - lost);
        player.sendHealthUpdate(shown, Math.max(0, player.getFoodLevel() - random.nextInt(6)), 0f);
        episode.timeline().restoreAt(now + 50 + random.nextInt(40), () -> {
            if (player.isOnline()) {
                player.sendHealthUpdate();
            }
        });
    }

    private boolean doppelganger(Player player, Episode episode, long now, int intensity) {
        Location at = behind(player, between(8, 14), 0.7 + random.nextDouble() * 0.3);
        PhantomClone clone = spawnPhantom(player, episode, now, PhantomClone.Behaviour.MIRROR, at,
            new Identity(ResolvableProfile.resolvableProfile(player.getPlayerProfile()), player.getName()), 60 * 20L);
        if (clone != null) {
            episode.timeline().at(now + 40, () -> whisperChat(player, "soy vos"));
        }
        return clone != null;
    }

    private boolean blockRot(Player player, Episode episode, long now, int intensity) {
        return rot(player, episode, now, 7, 40 + intensity * 15);
    }

    private boolean rot(Player player, Episode episode, long now, int radius, int limit) {
        List<Block> visible = visibleSolidBlocks(player, radius, limit);
        if (visible.isEmpty()) {
            return false;
        }
        Location origin = player.getLocation();
        visible.sort((a, b) -> Double.compare(
            a.getLocation().distanceSquared(origin), b.getLocation().distanceSquared(origin)));
        player.playSound(origin, Sound.ENTITY_ZOMBIE_INFECT, SoundCategory.AMBIENT, 0.8f, 0.5f);
        long restoreAt = now + 140 + random.nextInt(80);
        BlockData flesh = pick(ROT_BLOCKS).createBlockData();
        for (int i = 0; i < visible.size(); i++) {
            Block block = visible.get(i);
            Location location = block.getLocation();
            episode.timeline().at(now + i / 2, () -> player.sendBlockChange(location, flesh));
            episode.timeline().restoreAt(restoreAt + (visible.size() - i) / 3, () -> restoreBlock(player, location));
        }
        return true;
    }

    private boolean lightsOut(Player player, Episode episode, long now, int intensity) {
        List<Block> lights = nearby(player, 12, block -> LIGHT_SOURCES.contains(block.getType()), 30);
        if (lights.isEmpty()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false, false));
            return true;
        }
        long restoreAt = now + 160 + random.nextInt(80);
        BlockData air = Material.AIR.createBlockData();
        BlockData dead = Material.BLACK_CONCRETE.createBlockData();
        for (int i = 0; i < lights.size(); i++) {
            Block block = lights.get(i);
            Location location = block.getLocation();
            BlockData replacement = block.getType().isOccluding() ? dead : air;
            episode.timeline().at(now + i * 3L, () -> {
                player.sendBlockChange(location, replacement);
                player.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 0.6f);
            });
            episode.timeline().restoreAt(restoreAt, () -> restoreBlock(player, location));
        }
        return true;
    }

    private boolean maskedPlayers(Player player, Episode episode, long now, int intensity) {
        List<Player> others = player.getWorld().getPlayers().stream()
            .filter(other -> other != player && other.getLocation().distanceSquared(player.getLocation()) < 48 * 48)
            .filter(player::canSee)
            .toList();
        if (others.isEmpty()) {
            return doppelganger(player, episode, now, intensity);
        }
        long restoreAt = now + 200 + random.nextInt(100);
        for (Player other : others) {
            Map<EquipmentSlot, ItemStack> mask = Map.of(
                EquipmentSlot.HEAD, new ItemStack(pick(MASKS)),
                EquipmentSlot.HAND, new ItemStack(pick(WEAPONS)));
            player.sendEquipmentChange(other, mask);
            episode.timeline().restoreAt(restoreAt, () -> {
                if (player.isOnline() && other.isOnline() && other.getWorld() == player.getWorld()) {
                    EntityEquipment real = other.getEquipment();
                    player.sendEquipmentChange(other, Map.of(
                        EquipmentSlot.HEAD, real.getHelmet(),
                        EquipmentSlot.HAND, real.getItemInMainHand()));
                }
            });
        }
        whisperChat(player, "ellos no son quienes dicen ser");
        return true;
    }

    private boolean redVignette(Player player, Episode episode, long now, int intensity) {
        WorldBorder border = plugin.getServer().createWorldBorder();
        border.setCenter(player.getLocation());
        border.setSize(2000);
        border.setWarningDistance(4000);
        player.setWorldBorder(border);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.5f, 0.6f);
        episode.timeline().restoreAt(now + 120 + random.nextInt(100), () -> {
            if (player.isOnline()) {
                player.setWorldBorder(null);
            }
        });
        return true;
    }

    private boolean glitchTitle(Player player, Episode episode, long now, int intensity) {
        String text = SchizophreniaLines.pick(SchizophreniaLines.TITLES, random);
        boolean[] mask = SchizophreniaLines.glitchMask(text, 0.15 + intensity * 0.05, random);
        TextComponent.Builder title = Component.text().color(NamedTextColor.DARK_RED);
        for (int i = 0; i < text.length(); i++) {
            Component letter = Component.text(text.charAt(i));
            title.append(mask[i] ? letter.decorate(TextDecoration.OBFUSCATED) : letter);
        }
        Component subtitle = Component.text(SchizophreniaLines.pick(SchizophreniaLines.SUBTITLES, random), NamedTextColor.GRAY);
        player.showTitle(Title.title(title.build(), subtitle,
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900 + random.nextInt(900)), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.PLAYERS, 1f, 0.5f);
        return true;
    }

    private boolean fakeExplosion(Player player, Episode episode, long now, int intensity) {
        Location behind = behind(player, 1.2, 0.1);
        player.playSound(behind, Sound.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1f, 0.5f);
        episode.timeline().at(now + 30, () -> {
            if (random.nextDouble() < 0.25) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1f, 0.5f);
                return;
            }
            Location blast = behind(player, 1.2, 0.1);
            player.playSound(blast, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4f, 0.9f);
            player.spawnParticle(Particle.EXPLOSION_EMITTER, blast, 1);
            player.sendHurtAnimation(random.nextFloat() * 360f);
            fakeLowHealth(player, episode, now + 30, 10);
        });
        return true;
    }

    private boolean cameraSnap(Player player, Episode episode, long now, int intensity) {
        Location at = behind(player, 4 + random.nextInt(3), 0.0);
        PhantomClone clone = spawnPhantom(player, episode, now, PhantomClone.Behaviour.SNAP, at, randomIdentity(player), 40L);
        if (clone == null) {
            return false;
        }
        episode.timeline().at(now + 4, () -> {
            if (player.isOnline() && clone.entity().isValid()) {
                player.lookAt(clone.entity(), LookAnchor.EYES, LookAnchor.EYES);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1f, 0.5f);
            }
        });
        episode.timeline().at(now + 18, () -> vanish(player, clone, false));
        return true;
    }

    private boolean elderGuardian(Player player, Episode episode, long now, int intensity) {
        player.showElderGuardian(false);
        whisperChat(player, SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random));
        return true;
    }

    private boolean eternalNight(Player player, Episode episode, long now, int intensity) {
        player.setPlayerTime(18000L, false);
        player.setPlayerWeather(WeatherType.DOWNFALL);
        long end = now + 600 + random.nextInt(300);
        for (long t = now + 80; t < end; t += 120 + random.nextInt(120)) {
            episode.timeline().at(t, () -> player.playSound(around(player, 20), Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER, 1.5f, 0.6f));
        }
        episode.timeline().restoreAt(end, () -> {
            if (player.isOnline()) {
                player.resetPlayerTime();
                player.resetPlayerWeather();
            }
        });
        return true;
    }

    private boolean fakeThunder(Player player, Episode episode, long now, int intensity) {
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 2f, 0.8f);
        Location strike = ground(around(player, 5 + random.nextInt(5)));
        if (strike != null) {
            Location fire = strike.getBlock().getLocation();
            player.sendBlockChange(fire, Material.FIRE.createBlockData());
            player.spawnParticle(Particle.LARGE_SMOKE, fire.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.6, 0.3, 0.02);
            episode.timeline().restoreAt(now + 60, () -> restoreBlock(player, fire));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 12, 0, false, false, false));
        return true;
    }

    private boolean swarm(Player player, Episode episode, long now, int intensity) {
        int count = 6 + random.nextInt(4);
        double offset = random.nextDouble() * Math.PI * 2;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = offset + i * (Math.PI * 2 / count);
            double distance = between(7, 12);
            Location at = player.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (spawnPhantom(player, episode, now, PhantomClone.Behaviour.STARE, at, randomIdentity(player), 30 * 20L, true) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 1f, 0.6f);
            whisperChat(player, "todos te estamos mirando");
        }
        return spawned > 0;
    }

    private boolean fakeDeath(Player player, Episode episode, long now, int intensity) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 70, 0, false, false, false));
        player.sendHurtAnimation(random.nextFloat() * 360f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1f, 1f);
        player.showTitle(Title.title(
            Component.text("¡Has muerto!", NamedTextColor.RED),
            Component.text("Puntuación: 0", NamedTextColor.WHITE),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(200))));
        episode.timeline().at(now + 55, () -> player.showTitle(Title.title(
            Component.empty(),
            Component.text("...todavía no", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500)))));
        return true;
    }

    // ---------------------------------------------------------------- set pieces

    private boolean rightBehindYou(Player player, Episode episode, long now, int intensity) {
        PhantomClone clone = spawnPhantom(player, episode, now, PhantomClone.Behaviour.BEHIND,
            behind(player, 1.7, 0.0), randomIdentity(player), 10 * 20L, true);
        if (clone == null) {
            return false;
        }
        player.playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 1f, 0.5f);
        episode.timeline().at(now + 30, () -> whisperChat(player, "date vuelta"));
        return true;
    }

    private boolean theVisitor(Player player, Episode episode, long now, int intensity) {
        player.stopSound(SoundCategory.MUSIC);
        lightsOut(player, episode, now, intensity);
        Location door = around(player, 4 + random.nextInt(3));
        for (int knock = 0; knock < 3; knock++) {
            episode.timeline().at(now + 30 + knock * 16L, () ->
                player.playSound(door, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 1f, 0.6f));
        }
        episode.timeline().at(now + 90, () ->
            player.playSound(door, Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.7f));
        for (int step = 0; step < 6; step++) {
            double distance = 8 - step * 1.2;
            episode.timeline().at(now + 110 + step * 12L, () -> playStepSound(player, behind(player, distance, 0.0)));
        }
        // Then nothing. Long enough to relax.
        episode.timeline().at(now + 230, () -> {
            if (player.isOnline()) {
                rightBehindYou(player, episode, now + 230, intensity);
            }
        });
        return true;
    }

    private boolean chase(Player player, Episode episode, long now, int intensity) {
        long length = 15 * 20L;
        PhantomClone chaser = spawnPhantom(player, episode, now, PhantomClone.Behaviour.CHASER,
            behind(player, 13, 0.1), randomIdentity(player), length, true);
        if (chaser == null) {
            return false;
        }
        player.stopSound(SoundCategory.MUSIC);
        player.playSound(chaser.entity().getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2f, 0.7f);
        player.showTitle(Title.title(
            Component.text("CORRÉ", NamedTextColor.DARK_RED, TextDecoration.BOLD),
            Component.text("no mires atrás", NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
        for (long t = 10; t < length; t += 10) {
            boolean accent = t % 40 == 0;
            episode.timeline().at(now + t, () -> {
                if (chaser.entity().isValid()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS,
                        accent ? 1.4f : 0.9f, 0.5f);
                    if (accent) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 1f, 0.5f);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 1f, 0.53f);
                    }
                }
            });
        }
        episode.timeline().at(now + length - 2, () -> {
            if (chaser.entity().isValid()) {
                vanish(player, chaser, true);
                whisperChat(player, "te dejé ir. esta vez.");
            }
        });
        return true;
    }

    private boolean musicBox(Player player, Episode episode, long now, int intensity) {
        Location box = ground(around(player, 12 + random.nextInt(5)));
        if (box == null) {
            return false;
        }
        Sound song = random.nextBoolean() ? Sound.MUSIC_DISC_11 : Sound.MUSIC_DISC_13;
        player.playSound(box, song, SoundCategory.RECORDS, 1.2f, 0.9f);
        long end = now + 200 + random.nextInt(200);
        boolean[] stopped = {false};
        for (long t = now + 20; t < end; t += 20) {
            episode.timeline().at(t, () -> {
                if (!stopped[0] && player.isOnline() && box.getWorld() == player.getWorld()
                    && box.distanceSquared(player.getLocation()) < 25) {
                    stopped[0] = true;
                    player.stopSound(song, SoundCategory.RECORDS);
                    whisperChat(player, "no toques eso");
                }
            });
        }
        episode.timeline().restoreAt(end, () -> {
            if (player.isOnline()) {
                player.stopSound(song, SoundCategory.RECORDS);
            }
        });
        return true;
    }

    private boolean wallWriting(Player player, Episode episode, long now, int intensity) {
        Location sign = findWritingSpot(player);
        if (sign == null) {
            return false;
        }
        BlockFace facing = facingTowards(sign, player);
        WallSign data = (WallSign) Material.DARK_OAK_WALL_SIGN.createBlockData();
        data.setFacing(facing);
        List<Component> lines = SchizophreniaLines.pick(SchizophreniaLines.WALL_MESSAGES, random).stream()
            .map(line -> (Component) Component.text(line)).toList();
        player.sendBlockChange(sign, data);
        player.sendSignChange(sign, lines, DyeColor.RED, true);
        player.playSound(sign, Sound.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 0.4f, 0.5f);
        episode.timeline().restoreAt(now + 300 + random.nextInt(300), () -> restoreBlock(player, sign));
        return true;
    }

    private Location findWritingSpot(Player player) {
        Block eye = player.getEyeLocation().getBlock();
        BlockFace forward = player.getFacing();
        for (BlockFace face : List.of(forward, rotate(forward), rotate(rotate(rotate(forward))))) {
            for (int distance = 2; distance <= 7; distance++) {
                Block air = eye.getRelative(face, distance - 1);
                Block wall = eye.getRelative(face, distance);
                if (!air.getType().isAir()) {
                    break;
                }
                if (wall.getType().isOccluding()) {
                    return air.getLocation();
                }
            }
        }
        return null;
    }

    private static BlockFace facingTowards(Location sign, Player player) {
        double dx = player.getX() - (sign.getX() + 0.5);
        double dz = player.getZ() - (sign.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static BlockFace rotate(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    // ---------------------------------------------------------------- heavy set pieces

    private boolean yourName(Player player, Episode episode, long now, int intensity) {
        String name = player.getName();
        for (int i = 0; i < 3 + intensity; i++) {
            String spoken = i % 2 == 0 ? name.toLowerCase(java.util.Locale.ROOT) + "..." : name + ".";
            episode.timeline().at(now + i * 18L, () -> whisperChat(player, spoken));
        }
        episode.timeline().at(now + (3 + intensity) * 18L + 10, () -> {
            player.sendActionBar(Component.text(name.toUpperCase(java.util.Locale.ROOT), NamedTextColor.DARK_RED,
                TextDecoration.BOLD));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1f, 0.5f);
        });
        return true;
    }

    private boolean chatFlood(Player player, Episode episode, long now, int intensity) {
        int lines = 25 + intensity * 8;
        for (int i = 0; i < lines; i++) {
            episode.timeline().at(now + i * 2L, () -> {
                if (!player.isOnline()) {
                    return;
                }
                switch (random.nextInt(4)) {
                    case 0 -> player.sendMessage(Component.text("TE VEO", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                    case 1 -> whisperChat(player, SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random));
                    default -> player.sendMessage(Component.text(randomName(player) + " te susurra: "
                        + SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random), NamedTextColor.GRAY, TextDecoration.ITALIC));
                }
                if (random.nextInt(5) == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4f, 0.5f);
                }
            });
        }
        episode.timeline().at(now + lines * 2L + 20, () -> {
            for (int i = 0; i < 100; i++) {
                player.sendMessage(Component.empty());
            }
            whisperChat(player, "shhh");
        });
        return true;
    }

    private boolean screenShake(Player player, Episode episode, long now, int intensity) {
        for (int i = 0; i < 12 + intensity * 2; i++) {
            episode.timeline().at(now + i * 2L, () -> player.sendHurtAnimation(random.nextFloat() * 360f));
        }
        player.playSound(around(player, 20), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 2f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 1f, 0.6f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false, false));
        return true;
    }

    private boolean bloodRain(Player player, Episode episode, long now, int intensity) {
        Particle.DustOptions blood = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.3f);
        for (long t = 0; t < 240; t += 4) {
            boolean sound = t % 20 == 0;
            episode.timeline().at(now + t, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location above = player.getLocation().add(0, 4, 0);
                player.spawnParticle(Particle.DUST, above, 30, 5, 1.5, 5, 0, blood);
                player.spawnParticle(Particle.FALLING_LAVA, above, 10, 5, 1, 5, 0);
                if (sound) {
                    player.playSound(player.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_LAVA, SoundCategory.WEATHER, 1f, 0.6f);
                    player.playSound(player.getLocation(), Sound.WEATHER_RAIN, SoundCategory.WEATHER, 0.6f, 0.5f);
                }
            });
        }
        redVignette(player, episode, now, intensity);
        whisperChat(player, "es tuya");
        return true;
    }

    private boolean crowd(Player player, Episode episode, long now, int intensity) {
        int count = 10 + intensity * 2;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = between(14, 24);
            Location at = player.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (spawnPhantom(player, episode, now, PhantomClone.Behaviour.CROWD, at, randomIdentity(player), 50 * 20L, true) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            whisperChat(player, "somos muchos");
        }
        return spawned > 0;
    }

    private boolean hunted(Player player, Episode episode, long now, int intensity) {
        int count = 4 + (intensity >= 5 ? 2 : 0);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count) + random.nextDouble() * 0.5;
            double distance = between(18, 24);
            Location at = player.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (spawnPhantom(player, episode, now, PhantomClone.Behaviour.STALKER, at, randomIdentity(player), 45 * 20L, true) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, SoundCategory.HOSTILE, 1.2f, 0.6f);
            player.showTitle(Title.title(Component.empty(),
                Component.text("no los pierdas de vista", NamedTextColor.DARK_RED),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
        }
        return spawned > 0;
    }

    private boolean cameraSpin(Player player, Episode episode, long now, int intensity) {
        List<PhantomClone> ring = new ArrayList<>();
        int count = 6;
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            Location at = player.getLocation().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
            PhantomClone clone = spawnPhantom(player, episode, now, PhantomClone.Behaviour.SNAP, at, randomIdentity(player), 80L, true);
            if (clone != null) {
                ring.add(clone);
            }
        }
        if (ring.size() < 2) {
            ring.forEach(clone -> vanish(player, clone, false));
            return false;
        }
        for (int k = 0; k < 14; k++) {
            PhantomClone target = ring.get(k % ring.size());
            float pitch = 0.5f + k * 0.08f;
            episode.timeline().at(now + 4 + k * 3L, () -> {
                if (player.isOnline() && target.entity().isValid()) {
                    player.lookAt(target.entity(), LookAnchor.EYES, LookAnchor.EYES);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.HOSTILE, 1f, pitch);
                }
            });
        }
        episode.timeline().at(now + 50, () -> {
            jumpscare(player, episode, now + 50);
            ring.forEach(clone -> vanish(player, clone, true));
        });
        return true;
    }

    private boolean fleshRoom(Player player, Episode episode, long now, int intensity) {
        boolean rotted = rot(player, episode, now, 10, 260);
        redVignette(player, episode, now, intensity);
        lightsOut(player, episode, now, intensity);
        for (int i = 0; i < 3; i++) {
            episode.timeline().at(now + 20 + i * 15L, () -> wallWriting(player, episode, now, intensity));
        }
        heartbeat(player, episode, now, intensity);
        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 1f, 0.5f);
        return rotted;
    }

    /** Everything at once. Reserved for the worst stage. */
    private boolean breakdown(Player player, Episode episode, long now, int intensity) {
        screenShake(player, episode, now, intensity);
        fleshRoom(player, episode, now, intensity);
        episode.timeline().at(now + 20, () -> chatFlood(player, episode, now + 20, intensity));
        episode.timeline().at(now + 40, () -> swarm(player, episode, now + 40, intensity));
        episode.timeline().at(now + 110, () -> cameraSpin(player, episode, now + 110, intensity));
        episode.timeline().at(now + 190, () -> fakeDeath(player, episode, now + 190, intensity));
        player.showTitle(Title.title(
            Component.text("NO", NamedTextColor.DARK_RED, TextDecoration.BOLD, TextDecoration.OBFUSCATED),
            Component.text("no no no no no no", NamedTextColor.RED),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(200))));
        return true;
    }

    // ---------------------------------------------------------------- the black place

    /**
     * Taken away to the black place. A tall black figure waits in front of you and, every time
     * your eyes close, it is closer. Then you are thrown back as if nothing happened.
     */
    private boolean voidVisit(Player player, Episode episode, long now, int intensity) {
        if (voidRealm == null || episode.inVoid() || player.isInsideVehicle() || player.isDead()) {
            return false;
        }
        episode.inVoid(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        player.playSound(player.getLocation(), Sound.WEATHER_RAIN, SoundCategory.MASTER, 1.5f, 2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1f, 0.5f);
        episode.timeline().at(now + 12, () -> {
            if (!player.isOnline() || voidRealm.takeAway(player) == null) {
                episode.inVoid(false);
                return;
            }
            player.stopAllSounds();
            ShadowFigure[] figure = {null};
            double[] distances = {10, 7.5, 5.5, 3.8, 2.4, 1.3};
            long first = now + 80;
            for (int i = 0; i < distances.length; i++) {
                double distance = distances[i];
                boolean blink = i > 0;
                float pitch = 0.5f + i * 0.04f;
                episode.timeline().at(first + i * 45L, () -> {
                    if (!player.isOnline() || !voidRealm.contains(player)) {
                        return;
                    }
                    if (blink) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 8, 0, false, false, false));
                    }
                    Location feet = inFront(player, distance);
                    feet.setY(VoidRealm.FLOOR_Y + 1);
                    if (figure[0] == null) {
                        figure[0] = ShadowFigure.spawn(plugin, player, feet);
                    }
                    figure[0].moveTo(feet, player.getX(), player.getZ());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.HOSTILE, 1.4f, pitch);
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.2f, 0.8f);
                    if (distance < 4) {
                        whisperChat(player, SchizophreniaLines.pick(SchizophreniaLines.VOID_LINES, random));
                    }
                });
            }
            long end = first + distances.length * 45L + 25;
            episode.timeline().at(end - 12, () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.5f);
                player.showTitle(Title.title(
                    Component.text("NO ES QUIEN DICE SER", NamedTextColor.WHITE, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ZERO)));
            });
            episode.timeline().restoreAt(end, () -> {
                if (figure[0] != null) {
                    figure[0].remove();
                }
                if (player.isOnline()) {
                    voidRealm.bringBack(player);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));
                    player.playSound(player.getLocation(), Sound.WEATHER_RAIN, SoundCategory.MASTER, 1.5f, 2f);
                }
                episode.inVoid(false);
            });
        });
        return true;
    }

    /** Keeps a victim who walks off the black floor from falling forever. */
    void guardVoid(Player player, Episode episode) {
        if (voidRealm != null && episode.inVoid() && voidRealm.contains(player)
            && player.getY() < VoidRealm.FLOOR_Y - 4) {
            voidRealm.catchFall(player);
        }
    }

    private boolean alternate(Player player, Episode episode, long now, int intensity) {
        Identity identity = randomIdentity(player);
        PhantomClone clone = spawnPhantom(player, episode, now, PhantomClone.Behaviour.ALTERNATE,
            inFront(player, 16), identity, 45 * 20L, true);
        if (clone == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            String line = SchizophreniaLines.pick(SchizophreniaLines.ALTERNATE_LINES, random);
            episode.timeline().at(now + 20 + i * 45L, () -> {
                if (clone.entity().isValid()) {
                    player.sendMessage(Component.text(identity.name() + " te susurra: " + line,
                        NamedTextColor.GRAY, TextDecoration.ITALIC));
                }
            });
        }
        return true;
    }

    private boolean intruderAlert(Player player, Episode episode, long now, int intensity) {
        player.stopSound(SoundCategory.MUSIC);
        for (int i = 0; i < 40; i++) {
            float pitch = i % 2 == 0 ? 1.68f : 1.89f;
            episode.timeline().at(now + i * 2L, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.9f, pitch);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.MASTER, 0.6f, pitch);
            });
        }
        player.showTitle(Title.title(
            Component.text("⚠ ALERTA DE INTRUSO ⚠", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("permanezca en su hogar", NamedTextColor.WHITE),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(3500), Duration.ofMillis(500))));
        episode.timeline().at(now + 82, () ->
            player.sendMessage(Component.text("⚠ SISTEMA DE ALERTA DE EMERGENCIA ⚠", NamedTextColor.RED, TextDecoration.BOLD)));
        List<String> broadcast = SchizophreniaLines.BROADCAST;
        for (int i = 0; i < broadcast.size(); i++) {
            String line = broadcast.get(i);
            episode.timeline().at(now + 100 + i * 30L, () -> {
                player.sendMessage(Component.text(line, NamedTextColor.WHITE));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.MASTER, 0.4f, 0.5f);
            });
        }
        episode.timeline().at(now + 100 + broadcast.size() * 30L + 40, () ->
            player.playSound(player.getLocation(), Sound.WEATHER_RAIN, SoundCategory.MASTER, 1f, 2f));
        return true;
    }

    private boolean faceFlash(Player player, Episode episode, long now, int intensity) {
        Location at = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.1));
        TextDisplay face = at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.addScoreboardTag(PHANTOM_TAG);
            display.text(Component.text(SchizophreniaLines.FACE, NamedTextColor.WHITE));
            display.setBackgroundColor(Color.fromARGB(255, 0, 0, 0));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setLineWidth(400);
        });
        player.showEntity(plugin, face);
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.5f);
        player.playSound(player.getLocation(), Sound.WEATHER_RAIN, SoundCategory.MASTER, 1.5f, 2f);
        episode.timeline().restoreAt(now + 10 + random.nextInt(6), () -> {
            if (face.isValid()) {
                face.remove();
            }
        });
        return true;
    }

    // ---------------------------------------------------------------- atmosphere

    /** Played the moment the pacing moves to a new phase. */
    void onPhase(Player player, Episode episode, long now, HorrorPacing.Phase phase) {
        switch (phase) {
            case DREAD -> {
                player.stopSound(SoundCategory.MUSIC);
                drone(player);
            }
            case SCARE -> {
                // Dissonant stinger, like the cue before something lunges at the camera.
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 1.5f, 0.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 1.5f, 0.53f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 0.7f, 0.5f);
            }
            case AFTERMATH -> {
                // The world goes quiet. Only your own breathing.
                player.stopAllSounds();
                episode.timeline().at(now + 14, () ->
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.8f, 0.6f));
                episode.timeline().at(now + 50, () ->
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.6f, 0.7f));
            }
            case CALM -> {
            }
        }
    }

    /**
     * Continuous layer under the beats: sanity bar, music cut, permanent watchers, drones,
     * heartbeat, frost, darkness, screen jolts and a slow drip of whispers. It stays faint for a
     * long time; only from intensity 4 on is there no real calm any more.
     */
    void atmosphere(Player player, Episode episode, long now, int intensity) {
        HorrorPacing.Phase phase = episode.pacing().phase();
        double tension = episode.pacing().tension(now);
        if (now % 20 == 0) {
            updateSanity(player, episode, intensity, tension);
        }
        if (now % 100 == 0) {
            player.stopSound(SoundCategory.MUSIC);
        }
        if (now % 40 == 0) {
            maintainWatchers(player, episode, now, intensity);
        }
        boolean restless = intensity >= 4;
        if ((phase == HorrorPacing.Phase.CALM && !restless) || (phase == HorrorPacing.Phase.AFTERMATH && intensity < 4)) {
            return;
        }
        if (now % 100 == 0) {
            drone(player);
        }
        if (tension > 0.25 && now >= episode.nextHeartbeatTick()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS,
                (float) (0.7 + tension), 1f);
            episode.nextHeartbeatTick(now + Math.max(10, Math.round(40 - tension * 30)));
        }
        int frost = (int) Math.round(Math.max(0.0, Math.min(1.0, (tension - 0.2) / 0.8)) * 130);
        if (frost > 0 && player.getFreezeTicks() < frost) {
            player.setFreezeTicks(frost);
        }
        if (tension > 0.7 && now % 100 == 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        }
        if (tension > 0.8 && now % 50 == 0) {
            player.sendHurtAnimation(random.nextFloat() * 360f);
        }
        if (tension > 0.6 && now % 80 == 0 && random.nextDouble() < 0.4) {
            String line = SchizophreniaLines.pick(SchizophreniaLines.WHISPERS, random);
            if (random.nextBoolean()) {
                player.sendActionBar(Component.text(line, NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            } else {
                whisperChat(player, line);
            }
        }
    }

    /** Someone watching from the edge of sight: nobody at first, then one, up to three at the end. */
    private void maintainWatchers(Player player, Episode episode, long now, int intensity) {
        long watchers = episode.phantoms().stream()
            .filter(clone -> clone.behaviour() == PhantomClone.Behaviour.WATCHER).count();
        if (watchers < Math.min(3, (intensity - 1) / 1.5)) {
            spawnPhantom(player, episode, now, PhantomClone.Behaviour.WATCHER,
                behind(player, between(18, 30), 0.4 + random.nextDouble() * 0.6), randomIdentity(player), 6000L);
        }
    }

    void showSanity(Player player, Episode episode) {
        player.showBossBar(episode.sanity());
    }

    void clearAtmosphere(Player player, Episode episode) {
        player.hideBossBar(episode.sanity());
        if (player.getFreezeTicks() <= 120 && player.getLocation().getBlock().getType() != Material.POWDER_SNOW) {
            player.setFreezeTicks(0);
        }
        player.stopSound(Sound.MUSIC_DISC_11, SoundCategory.RECORDS);
        player.stopSound(Sound.MUSIC_DISC_13, SoundCategory.RECORDS);
    }

    private void updateSanity(Player player, Episode episode, int intensity, double tension) {
        float sanity = EpisodeMath.sanity(intensity, tension);
        episode.sanity().progress(sanity);
        episode.sanity().color(sanity < 0.35f ? net.kyori.adventure.bossbar.BossBar.Color.RED
            : net.kyori.adventure.bossbar.BossBar.Color.PURPLE);
        Component name = Component.text("Cordura", sanity < 0.35f ? NamedTextColor.DARK_RED : NamedTextColor.DARK_PURPLE);
        if (tension > 0.7 && random.nextDouble() < 0.35) {
            name = name.decorate(TextDecoration.OBFUSCATED);
        }
        episode.sanity().name(name);
    }

    private void drone(Player player) {
        Sound drone = random.nextBoolean() ? Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD : Sound.AMBIENT_BASALT_DELTAS_MOOD;
        player.playSound(player.getLocation(), drone, SoundCategory.AMBIENT, 1f, 0.5f + random.nextFloat() * 0.15f);
    }

    // ---------------------------------------------------------------- phantoms

    record Identity(ResolvableProfile profile, String name) {
    }

    PhantomClone spawnPhantom(
        Player player, Episode episode, long now, PhantomClone.Behaviour behaviour,
        Location near, Identity identity, long lifetimeTicks
    ) {
        return spawnPhantom(player, episode, now, behaviour, near, identity, lifetimeTicks, false);
    }

    private PhantomClone spawnPhantom(
        Player player, Episode episode, long now, PhantomClone.Behaviour behaviour,
        Location near, Identity identity, long lifetimeTicks, boolean ignoreCap
    ) {
        if (!ignoreCap && episode.phantoms().size() >= settings.phantoms().maxPerPlayer()) {
            return null;
        }
        Location at = ground(near);
        if (at == null) {
            return null;
        }
        at.setYaw(GazeMath.yawTowards(at.getX(), at.getZ(), player.getX(), player.getZ()));
        at.setPitch(0f);
        Mannequin mannequin = at.getWorld().spawn(at, Mannequin.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setAI(false);
            entity.setCollidable(false);
            entity.setGravity(false);
            entity.setImmovable(true);
            entity.addScoreboardTag(PHANTOM_TAG);
            entity.setProfile(identity.profile());
            entity.customName(Component.text(identity.name()));
            entity.setCustomNameVisible(true);
            entity.setDescription(null);
        });
        player.showEntity(plugin, mannequin);
        PhantomClone clone = new PhantomClone(mannequin, behaviour, now + lifetimeTicks);
        episode.phantoms().add(clone);
        return clone;
    }

    /** Moves, turns or dissolves every phantom of one victim. */
    void updatePhantoms(Player player, Episode episode, long now) {
        SchizophreniaSettings.Phantoms config = settings.phantoms();
        for (PhantomClone clone : new ArrayList<>(episode.phantoms())) {
            Mannequin entity = clone.entity();
            if (!entity.isValid() || entity.getWorld() != player.getWorld()
                || entity.getLocation().distanceSquared(player.getLocation()) > 80 * 80 || clone.expired(now)) {
                clone.remove();
                episode.phantoms().remove(clone);
                continue;
            }
            Location eye = entity.getLocation().add(0, 1.6, 0);
            boolean watched = looksAt(player, eye, config.gazeThresholdDegrees()) && player.hasLineOfSight(entity);
            double distance = entity.getLocation().distance(player.getLocation());
            switch (clone.behaviour()) {
                case STARE -> {
                    if (watched || distance < config.vanishDistance()) {
                        vanish(player, clone, true);
                    } else {
                        face(entity, player);
                    }
                }
                case MIRROR -> {
                    face(entity, player);
                    if (watched) {
                        clone.watchedFor(clone.watchedFor() + 2);
                    }
                    if (clone.watchedFor() > 60 || distance < config.vanishDistance()) {
                        player.playSound(eye, Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1f, 0.4f);
                        vanish(player, clone, false);
                    }
                }
                case STALKER -> stalk(player, episode, clone, watched, distance, now);
                case SNAP -> face(entity, player);
                case BEHIND -> lurkBehind(player, episode, clone, now);
                case CHASER -> chaseStep(player, episode, clone, distance, now);
                case WATCHER -> {
                    face(entity, player);
                    if (watched) {
                        clone.watchedFor(clone.watchedFor() + 2);
                    }
                    if (clone.watchedFor() > 24 || distance < 8) {
                        vanish(player, clone, true);
                    }
                }
                case CROWD -> creep(player, episode, clone, watched, distance, now);
                case ALTERNATE -> approachAsFriend(player, clone, distance);
            }
        }
    }

    private void lurkBehind(Player player, Episode episode, PhantomClone clone, long now) {
        Location eye = clone.entity().getLocation().add(0, 1.6, 0);
        if (looksAt(player, eye, 40.0)) {
            jumpscare(player, episode, now);
            vanish(player, clone, false);
            return;
        }
        Location spot = ground(behind(player, 1.7, 0.0));
        if (spot == null) {
            return;
        }
        spot.setYaw(GazeMath.yawTowards(spot.getX(), spot.getZ(), player.getX(), player.getZ()));
        clone.entity().teleport(spot);
        clone.entity().setBodyYaw(spot.getYaw());
    }

    private void chaseStep(Player player, Episode episode, PhantomClone clone, double distance, long now) {
        if (distance < 1.5) {
            jumpscare(player, episode, now);
            vanish(player, clone, false);
            return;
        }
        Mannequin entity = clone.entity();
        Vector step = player.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
        if (step.lengthSquared() < 1.0E-4) {
            return;
        }
        // 5.2 blocks per second: a sprinting player barely gets away.
        step.normalize().multiply(0.52);
        Location next = ground(entity.getLocation().add(step));
        if (next == null) {
            vanish(player, clone, true);
            return;
        }
        next.setYaw(GazeMath.yawTowards(next.getX(), next.getZ(), player.getX(), player.getZ()));
        entity.teleport(next);
        entity.setBodyYaw(next.getYaw());
        if (now % 6 == 0) {
            player.playSound(next, Sound.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1f, 0.7f);
        }
    }

    private void approachAsFriend(Player player, PhantomClone clone, double distance) {
        Mannequin entity = clone.entity();
        face(entity, player);
        if (distance > 3.2 && clone.watchedFor() == 0) {
            Vector step = player.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
            if (step.lengthSquared() > 1.0E-4) {
                Location next = ground(entity.getLocation().add(step.normalize().multiply(0.18)));
                if (next != null) {
                    next.setYaw(GazeMath.yawTowards(next.getX(), next.getZ(), player.getX(), player.getZ()));
                    entity.teleport(next);
                    entity.setBodyYaw(next.getYaw());
                    if (random.nextInt(5) == 0) {
                        playStepSound(player, next);
                    }
                }
            }
            return;
        }
        // Close enough. It stops pretending.
        int stage = clone.watchedFor() + 2;
        clone.watchedFor(stage);
        if (stage == 30) {
            String friend = entity.customName() == null ? "???" : net.kyori.adventure.text.serializer.plain
                .PlainTextComponentSerializer.plainText().serialize(entity.customName());
            entity.customName(Component.text("Dinnerbone"));
            entity.setCustomNameVisible(false);
            player.playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1f, 0.5f);
            player.sendMessage(Component.text(friend + " te susurra: ese no era yo. no le abras.",
                NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        if (stage >= 70) {
            player.playSound(entity.getLocation(), Sound.WEATHER_RAIN, SoundCategory.MASTER, 1.2f, 2f);
            vanish(player, clone, true);
        }
    }

    private void creep(Player player, Episode episode, PhantomClone clone, boolean watched, double distance, long now) {
        Mannequin entity = clone.entity();
        face(entity, player);
        if (distance < 2.0) {
            jumpscare(player, episode, now);
            vanish(player, clone, false);
            return;
        }
        if (watched) {
            return;
        }
        Vector step = player.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
        if (step.lengthSquared() < 1.0E-4) {
            return;
        }
        Location next = ground(entity.getLocation().add(step.normalize().multiply(0.13)));
        if (next != null) {
            next.setYaw(GazeMath.yawTowards(next.getX(), next.getZ(), player.getX(), player.getZ()));
            entity.teleport(next);
            entity.setBodyYaw(next.getYaw());
        }
    }

    private void stalk(Player player, Episode episode, PhantomClone clone, boolean watched, double distance, long now) {
        Mannequin entity = clone.entity();
        if (watched) {
            clone.watchedFor(clone.watchedFor() + 2);
            if (clone.watchedFor() > 50) {
                vanish(player, clone, true);
            }
            return;
        }
        if (distance < 1.8) {
            jumpscare(player, episode, now);
            vanish(player, clone, false);
            return;
        }
        Vector step = player.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
        if (step.lengthSquared() < 1.0E-4) {
            return;
        }
        step.normalize().multiply(0.45);
        Location next = ground(entity.getLocation().add(step));
        if (next == null) {
            vanish(player, clone, true);
            return;
        }
        next.setYaw(GazeMath.yawTowards(next.getX(), next.getZ(), player.getX(), player.getZ()));
        entity.teleport(next);
        entity.setBodyYaw(next.getYaw());
        if (random.nextInt(6) == 0) {
            playStepSound(player, next);
        }
    }

    private void jumpscare(Player player, Episode episode, long now) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.6f);
        player.sendHurtAnimation(random.nextFloat() * 360f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
        player.showTitle(Title.title(
            Component.text("TE ENCONTRÉ", NamedTextColor.DARK_RED, TextDecoration.BOLD),
            Component.empty(),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
        fakeLowHealth(player, episode, now, 6);
    }

    void vanish(Player player, PhantomClone clone, boolean poof) {
        if (clone.entity().isValid() && player.isOnline() && poof) {
            Location at = clone.entity().getLocation().add(0, 1, 0);
            player.spawnParticle(Particle.LARGE_SMOKE, at, 15, 0.25, 0.7, 0.25, 0.01);
            player.playSound(at, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SoundCategory.HOSTILE, 0.6f, 0.5f);
        }
        clone.remove();
    }

    void removeAllPhantoms(Episode episode) {
        episode.phantoms().forEach(PhantomClone::remove);
        episode.phantoms().clear();
    }

    private static void face(Mannequin entity, Player player) {
        Location location = entity.getLocation();
        float yaw = GazeMath.yawTowards(location.getX(), location.getZ(), player.getX(), player.getZ());
        double dy = player.getEyeLocation().getY() - (location.getY() + 1.6);
        double horizontal = Math.hypot(player.getX() - location.getX(), player.getZ() - location.getZ());
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.1, horizontal)));
        entity.setRotation(yaw, pitch);
        entity.setBodyYaw(yaw);
    }

    private Identity randomIdentity(Player victim) {
        List<? extends Player> others = victim.getServer().getOnlinePlayers().stream()
            .filter(other -> !other.equals(victim)).toList();
        if (!others.isEmpty() && random.nextDouble() < 0.75) {
            Player other = others.get(random.nextInt(others.size()));
            return new Identity(ResolvableProfile.resolvableProfile(other.getPlayerProfile()), other.getName());
        }
        if (random.nextBoolean()) {
            return new Identity(ResolvableProfile.resolvableProfile(victim.getPlayerProfile()), victim.getName());
        }
        return new Identity(Mannequin.defaultProfile(), SchizophreniaLines.pick(SchizophreniaLines.PHANTOM_NAMES, random));
    }

    // ---------------------------------------------------------------- helpers

    private String randomName(Player victim) {
        List<String> names = new ArrayList<>(victim.getServer().getOnlinePlayers().stream()
            .filter(other -> !other.equals(victim)).map(Player::getName).toList());
        OfflinePlayer[] offline = victim.getServer().getOfflinePlayers();
        for (int i = 0; i < Math.min(5, offline.length); i++) {
            String name = offline[random.nextInt(offline.length)].getName();
            if (name != null && !name.equals(victim.getName())) {
                names.add(name);
            }
        }
        names.addAll(SchizophreniaLines.PHANTOM_NAMES.subList(0, 2));
        return names.get(random.nextInt(names.size()));
    }

    private void whisperChat(Player player, String line) {
        if (player.isOnline()) {
            player.sendMessage(Component.text(line, NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
        }
    }

    private static void restoreBlock(Player player, Location location) {
        if (player.isOnline() && location.getWorld() == player.getWorld()) {
            player.sendBlockChange(location, location.getBlock().getBlockData());
        }
    }

    private boolean looksAt(Player player, Location target, double thresholdDegrees) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        return GazeMath.angleTo(look.getX(), look.getY(), look.getZ(),
            eye.getX(), eye.getY(), eye.getZ(), target.getX(), target.getY(), target.getZ()) < thresholdDegrees;
    }

    private Location behind(Player player, double distance, double side) {
        double[] offset = GazeMath.offset(player.getLocation().getYaw(), side, random.nextBoolean(), distance);
        return player.getLocation().add(offset[0], 0, offset[1]);
    }

    private Location inFront(Player player, double distance) {
        float yaw = player.getLocation().getYaw();
        return player.getLocation().add(-Math.sin(Math.toRadians(yaw)) * distance, 0, Math.cos(Math.toRadians(yaw)) * distance);
    }

    private Location around(Player player, double distance) {
        double angle = random.nextDouble() * Math.PI * 2;
        return player.getLocation().add(Math.cos(angle) * distance, random.nextDouble() * 2 - 1, Math.sin(angle) * distance);
    }

    /** Closest spot near {@code near} with two passable blocks over solid ground, or null. */
    private static Location ground(Location near) {
        World world = near.getWorld();
        if (world == null) {
            return null;
        }
        int x = near.getBlockX();
        int z = near.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int baseY = near.getBlockY();
        for (int i = 0; i <= 12; i++) {
            int dy = (i % 2 == 0) ? i / 2 : -(i + 1) / 2;
            int y = baseY + dy;
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                continue;
            }
            Block feet = world.getBlockAt(x, y, z);
            if (feet.isPassable() && !feet.isLiquid()
                && feet.getRelative(BlockFace.UP).isPassable()
                && feet.getRelative(BlockFace.DOWN).getType().isSolid()) {
                return new Location(world, near.getX(), y, near.getZ());
            }
        }
        return null;
    }

    private List<Block> visibleSolidBlocks(Player player, int radius, int limit) {
        return nearby(player, radius, block -> block.getType().isOccluding() && touchesAir(block), limit);
    }

    private List<Block> nearby(Player player, int radius, java.util.function.Predicate<Block> filter, int limit) {
        List<Block> found = new ArrayList<>();
        Location origin = player.getLocation();
        World world = player.getWorld();
        for (int x = -radius; x <= radius && found.size() < limit; x++) {
            for (int y = -radius / 2; y <= radius / 2 + 2 && found.size() < limit; y++) {
                for (int z = -radius; z <= radius && found.size() < limit; z++) {
                    if (random.nextInt(3) != 0 || x * x + z * z > radius * radius) {
                        continue;
                    }
                    int by = origin.getBlockY() + y;
                    if (by <= world.getMinHeight() || by >= world.getMaxHeight()) {
                        continue;
                    }
                    Block block = world.getBlockAt(origin.getBlockX() + x, by, origin.getBlockZ() + z);
                    if (filter.test(block)) {
                        found.add(block);
                    }
                }
            }
        }
        return found;
    }

    private static boolean touchesAir(Block block) {
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (block.getRelative(face).getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void dust(Player player, Location at, Particle.DustOptions options) {
        player.spawnParticle(Particle.DUST, at.getX(), at.getY(), at.getZ(), 1, 0, 0, 0, 0, options);
    }

    private double between(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
