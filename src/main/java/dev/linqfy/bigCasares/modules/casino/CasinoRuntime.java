package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.bar.CasinoBar;
import dev.linqfy.bigCasares.modules.casino.bar.DrinkEffects;
import dev.linqfy.bigCasares.modules.casino.bar.DrinkItems;
import dev.linqfy.bigCasares.modules.casino.blackjack.BlackjackTable;
import dev.linqfy.bigCasares.modules.casino.decor.Grime;
import dev.linqfy.bigCasares.modules.casino.decor.ManagementSign;
import dev.linqfy.bigCasares.modules.casino.loans.DebtCollector;
import dev.linqfy.bigCasares.modules.casino.loans.LoanService;
import dev.linqfy.bigCasares.modules.casino.loans.LoanShark;
import dev.linqfy.bigCasares.modules.casino.pawn.PawnService;
import dev.linqfy.bigCasares.modules.casino.pawn.PawnShop;
import dev.linqfy.bigCasares.modules.casino.pit.FightPit;
import dev.linqfy.bigCasares.modules.casino.pit.InventoryStash;
import dev.linqfy.bigCasares.modules.casino.poker.PokerTable;
import dev.linqfy.bigCasares.modules.casino.roulette.RouletteTable;
import dev.linqfy.bigCasares.modules.casino.slots.JackpotPool;
import dev.linqfy.bigCasares.modules.casino.slots.SlotMachine;
import dev.linqfy.bigCasares.modules.casino.world.CasinoKeys;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import dev.linqfy.bigCasares.modules.casino.world.InteractionRouter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/** Owns every placed attraction plus the casino-wide services, and drives them from one tick task. */
public final class CasinoRuntime {

    private static final int BLOCKS_PER_TICK = 4000;

    private final Plugin plugin;
    private final File folder;
    private final CasinoKeys keys;
    private final DisplayKit kit;
    private final InteractionRouter router = new InteractionRouter();
    private final CasinoContext context;
    private final InstallationStore installationStore;
    private final List<Installation> installations = new ArrayList<>();
    private final Map<String, Attraction> attractions = new LinkedHashMap<>();
    private final Set<String> tickets = new HashSet<>();
    private final LoanService loans;
    private final PawnService pawn;
    private final DebtCollector collector;
    private final DrinkItems drinks;
    private final DrinkEffects drinkEffects;
    private final InventoryStash stash;
    private final JackpotPool jackpot;
    private final java.util.ArrayDeque<Runnable> buildQueue = new java.util.ArrayDeque<>();

    public CasinoRuntime(Plugin plugin, CasinoSettings settings, CasinoBank bank) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "casino");
        this.keys = new CasinoKeys(plugin);
        this.kit = new DisplayKit(plugin, keys);
        RandomGenerator random = new SecureRandom();
        File stateFile = new File(folder, "state.yml");
        double savedJackpot = YamlConfiguration.loadConfiguration(stateFile).getDouble("jackpot", 0);
        this.jackpot = new JackpotPool(settings.slots().jackpotSeed(), settings.slots().jackpotContribution(), savedJackpot);
        CasinoEscrow escrow = new CasinoEscrow(bank, new YamlEscrowStore(new File(folder, "escrow.yml"), plugin.getLogger()));
        int refunded = escrow.refundAll();
        if (refunded > 0) {
            plugin.getLogger().warning("[Casino] Refunded " + refunded + " bet(s) left in play by the last shutdown.");
        }
        this.pawn = new PawnService(settings.pawn(), bank, new File(folder, "pawn.yml"), plugin.getLogger(),
            System::currentTimeMillis);
        this.context = new CasinoContext(plugin, settings, bank, escrow, kit, router, keys, random, jackpot,
            this::saveState, pawn, System::currentTimeMillis);
        this.loans = new LoanService(settings.loans(), bank, new File(folder, "loans.yml"), plugin.getLogger(),
            System::currentTimeMillis);
        this.collector = new DebtCollector(context, loans);
        this.drinks = new DrinkItems(keys);
        this.drinkEffects = new DrinkEffects(kit, keys, random);
        this.stash = new InventoryStash(new File(folder, "stash"), plugin.getLogger());
        this.installationStore = new InstallationStore(new File(folder, "installations.yml"), plugin.getLogger());
    }

    public void start() {
        removeStrayEntities();
        installations.addAll(installationStore.load());
        for (Installation installation : installations) {
            spawn(installation);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            recover(player);
        }
        plugin.getLogger().info("[Casino] " + attractions.size() + "/" + installations.size() + " attractions open.");
    }

    public void tick() {
        for (int processed = 0; processed < BLOCKS_PER_TICK && !buildQueue.isEmpty(); processed++) {
            buildQueue.pollFirst().run();
        }
        for (Attraction attraction : List.copyOf(attractions.values())) {
            try {
                attraction.tick();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "[Casino] Attraction " + attraction.installation().id()
                    + " failed and was closed", failure);
                close(attraction.installation().id());
            }
        }
        collector.tick();
        drinkEffects.tick();
    }

    public void stop() {
        buildQueue.clear();
        for (String id : List.copyOf(attractions.keySet())) {
            close(id);
        }
        collector.dismissAll();
        drinkEffects.shutdown();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (stash.has(player.getUniqueId())) {
                stash.restore(player);
            }
        }
        context.escrow().refundAll();
        for (String ticket : List.copyOf(tickets)) {
            releaseTicket(ticket);
        }
        router.clear();
        loans.save();
        saveState();
    }

    public Installation place(AttractionType type, Location location, String variant) {
        Frame frame = Frame.of(location);
        Installation installation = new Installation(type.id() + "-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36),
            type, location.getWorld().getName(), frame.x(), frame.y(), frame.z(), frame.yaw(), variant == null ? "" : variant);
        installations.add(installation);
        installationStore.save(installations);
        if (type == AttractionType.FIGHT_PIT) {
            FightPit.build(frame);
        }
        spawn(installation);
        return installation;
    }

    /** Removes the closest installation within {@code radius}. */
    public Optional<Installation> removeNearest(Location location, double radius) {
        Installation nearest = null;
        double best = radius * radius;
        for (Installation installation : installations) {
            double distance = installation.distanceSquared(location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ());
            if (distance <= best) {
                best = distance;
                nearest = installation;
            }
        }
        if (nearest == null) {
            return Optional.empty();
        }
        close(nearest.id());
        installations.remove(nearest);
        installationStore.save(installations);
        return Optional.of(nearest);
    }

    /** Builds the whole casino hall in front of {@code entrance} and installs every attraction. */
    public int build(Location entrance) {
        Location snapped = entrance.getBlock().getLocation().add(0.5, 0, 0.5);
        snapped.setYaw(entrance.getYaw());
        Frame frame = Frame.of(snapped);
        List<CasinoBlueprint.Piece> pieces = CasinoBlueprint.shell();
        for (CasinoBlueprint.Piece piece : pieces) {
            buildQueue.add(() -> CasinoBlueprint.place(frame, piece));
        }
        buildQueue.add(() -> {
            CasinoBlueprint.buildPit(frame);
            for (CasinoBlueprint.Spot spot : CasinoBlueprint.spots()) {
                Location at = frame.at(spot.right(), CasinoBlueprint.heightOf(spot), spot.forward(), spot.yawOffset());
                Frame local = Frame.of(at);
                Installation installation = new Installation(spot.type().id() + "-"
                    + Long.toString(System.nanoTime() % 1_000_000_000L, 36), spot.type(), at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(), local.yaw(), spot.variant());
                installations.add(installation);
                spawn(installation);
            }
            installationStore.save(installations);
            plugin.getLogger().info("[Casino] El Gran Casares is open.");
        });
        return pieces.size();
    }

    public List<Installation> installations() {
        return List.copyOf(installations);
    }

    public boolean building() {
        return !buildQueue.isEmpty();
    }

    public CasinoContext context() {
        return context;
    }

    public InteractionRouter router() {
        return router;
    }

    public DisplayKit kit() {
        return kit;
    }

    public CasinoKeys keys() {
        return keys;
    }

    public LoanService loans() {
        return loans;
    }

    public DebtCollector collector() {
        return collector;
    }

    public DrinkItems drinks() {
        return drinks;
    }

    public DrinkEffects drinkEffects() {
        return drinkEffects;
    }

    public JackpotPool jackpot() {
        return jackpot;
    }

    public List<Attraction> attractions() {
        return List.copyOf(attractions.values());
    }

    /** Gives back what a crash may have left behind: a fight-pit inventory and hidden items. */
    public void recover(Player player) {
        if (stash.has(player.getUniqueId())) {
            Location back = stash.restore(player);
            if (back != null && back.getWorld() != null) {
                player.teleport(back);
            }
            player.sendMessage("§7[Casino] Te devolvimos lo que tenías guardado en el foso.");
        }
        drinkEffects.restoreInventory(player);
    }

    public void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jackpot", jackpot.raw());
        try {
            CasinoFiles.atomicSave(yaml, new File(folder, "state.yml"));
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "[Casino] Could not save the jackpot", failure);
        }
    }

    private void spawn(Installation installation) {
        World world = Bukkit.getWorld(installation.world());
        if (world == null) {
            plugin.getLogger().warning("[Casino] World " + installation.world() + " of " + installation.id()
                + " is not loaded; it stays closed.");
            return;
        }
        Location location = installation.location(world);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                claimTicket(world, installation.chunkX() + dx, installation.chunkZ() + dz);
            }
        }
        Frame frame = new Frame(world, location.getX(), location.getY(), location.getZ(), installation.yaw());
        Attraction attraction = switch (installation.type()) {
            case BLACKJACK -> new BlackjackTable(context, installation, frame);
            case POKER -> new PokerTable(context, installation, frame);
            case ROULETTE -> new RouletteTable(context, installation, frame);
            case SLOT -> new SlotMachine(context, installation, frame);
            case FIGHT_PIT -> new FightPit(context, installation, frame, stash);
            case LOAN_SHARK -> new LoanShark(context, installation, frame, loans);
            case PAWN_SHOP -> new PawnShop(context, installation, frame, pawn);
            case BAR -> new CasinoBar(context, installation, frame, drinks);
            case SIGN -> new ManagementSign(context, installation, frame);
            case GRIME -> new Grime(context, installation, frame);
        };
        try {
            attraction.spawn();
            attractions.put(installation.id(), attraction);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Casino] Could not open " + installation.id(), failure);
            attraction.despawn();
        }
    }

    private void close(String id) {
        Attraction attraction = attractions.remove(id);
        if (attraction == null) {
            return;
        }
        try {
            attraction.despawn();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[Casino] Error closing " + id, failure);
        }
    }

    private void claimTicket(World world, int chunkX, int chunkZ) {
        String key = world.getName() + ":" + chunkX + ":" + chunkZ;
        if (tickets.add(key)) {
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
        }
    }

    private void releaseTicket(String key) {
        String[] parts = key.split(":");
        World world = Bukkit.getWorld(parts[0]);
        if (world != null) {
            world.removePluginChunkTicket(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), plugin);
        }
        tickets.remove(key);
    }

    private void removeStrayEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (kit.isCasinoEntity(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("[Casino] Removed " + removed + " leftover casino entities.");
        }
    }

    public static String describe(Installation installation) {
        return String.format(Locale.ROOT, "%s §7(%s) §f%s %.0f %.0f %.0f", installation.type().displayName(),
            installation.id(), installation.world(), installation.x(), installation.y(), installation.z());
    }
}
