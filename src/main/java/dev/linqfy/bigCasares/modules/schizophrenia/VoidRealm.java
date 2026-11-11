package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Random;
import java.util.UUID;

/**
 * The black place: an empty world where victims are taken for a moment. Each player gets their
 * own spot far from everyone else, a small black floor that is removed again, and a stored way
 * home that survives logouts and crashes.
 */
final class VoidRealm {

    static final int FLOOR_Y = 100;
    private static final int FLOOR_RADIUS = 3;

    private final Plugin plugin;
    private final String worldName;
    private final NamespacedKey returnKey;
    private final NamespacedKey protectedKey;
    private World world;

    VoidRealm(Plugin plugin, String worldName) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.returnKey = new NamespacedKey(plugin, "schizophrenia_void_return");
        this.protectedKey = new NamespacedKey(plugin, "schizophrenia_void_was_invulnerable");
    }

    /** Loads or creates the void world. Returns null when the server refuses to create it. */
    World world() {
        if (world != null && plugin.getServer().getWorld(world.getUID()) != null) {
            return world;
        }
        World existing = plugin.getServer().getWorld(worldName);
        world = existing != null ? existing : new WorldCreator(worldName)
            .environment(World.Environment.NORMAL)
            .generator(new Nothing())
            .generateStructures(false)
            .createWorld();
        if (world != null) {
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_MONSTERS, false);
            world.setGameRule(GameRules.SPAWN_PATROLS, false);
            world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
            world.setGameRule(GameRules.SPAWN_WARDENS, false);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            world.setGameRule(GameRules.FALL_DAMAGE, false);
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setStorm(false);
            world.setTime(18000L);
        }
        return world;
    }

    boolean contains(Player player) {
        return player.getWorld().getName().equals(worldName);
    }

    /** Takes the player to their own black spot. Returns the spot or null when it failed. */
    Location takeAway(Player player) {
        World target = world();
        if (target == null) {
            return null;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (!data.has(returnKey)) {
            Location home = player.getLocation();
            data.set(returnKey, PersistentDataType.STRING, home.getWorld().getUID() + ";" + home.getX() + ";"
                + home.getY() + ";" + home.getZ() + ";" + home.getYaw() + ";" + home.getPitch());
            data.set(protectedKey, PersistentDataType.BYTE, (byte) (player.isInvulnerable() ? 1 : 0));
        }
        Location spot = spotFor(target, player.getUniqueId());
        buildFloor(spot, Material.BLACK_CONCRETE);
        player.setInvulnerable(true);
        player.setFallDistance(0f);
        return player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN) ? spot : null;
    }

    /** Sends the player back where they were taken from, if they were taken at all. */
    void bringBack(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        String raw = data.get(returnKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        Location home = parse(raw);
        if (home == null) {
            home = plugin.getServer().getWorlds().getFirst().getSpawnLocation();
        }
        Byte wasProtected = data.get(protectedKey, PersistentDataType.BYTE);
        data.remove(returnKey);
        data.remove(protectedKey);
        player.setFallDistance(0f);
        player.teleport(home, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setInvulnerable(wasProtected != null && wasProtected == 1);
        World target = plugin.getServer().getWorld(worldName);
        if (target != null) {
            buildFloor(spotFor(target, player.getUniqueId()), Material.AIR);
        }
    }

    /** Puts someone who walked off the floor back on it. */
    void catchFall(Player player) {
        World target = plugin.getServer().getWorld(worldName);
        if (target != null) {
            Location spot = spotFor(target, player.getUniqueId());
            spot.setYaw(player.getLocation().getYaw());
            player.setFallDistance(0f);
            player.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    boolean isAway(Player player) {
        return player.getPersistentDataContainer().has(returnKey);
    }

    static int slot(UUID playerId) {
        return Math.floorMod(playerId.hashCode(), 4096);
    }

    private static Location spotFor(World world, UUID playerId) {
        return new Location(world, 10_000.5 + slot(playerId) * 512.0, FLOOR_Y + 1, 0.5, 0f, 0f);
    }

    private static void buildFloor(Location spot, Material material) {
        World world = spot.getWorld();
        for (int x = -FLOOR_RADIUS; x <= FLOOR_RADIUS; x++) {
            for (int z = -FLOOR_RADIUS; z <= FLOOR_RADIUS; z++) {
                world.getBlockAt(spot.getBlockX() + x, FLOOR_Y, spot.getBlockZ() + z).setType(material, false);
            }
        }
    }

    private Location parse(String raw) {
        String[] parts = raw.split(";");
        if (parts.length != 6) {
            return null;
        }
        try {
            World home = plugin.getServer().getWorld(UUID.fromString(parts[0]));
            if (home == null) {
                return null;
            }
            return new Location(home, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Generates nothing at all. */
    private static final class Nothing extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, FLOOR_Y + 1, 0.5);
        }
    }
}
