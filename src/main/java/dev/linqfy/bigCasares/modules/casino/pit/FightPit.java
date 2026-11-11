package dev.linqfy.bigCasares.modules.casino.pit;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A fenced pit for 1v1 fights with identical wooden swords. Two players pay the entry fee, spectators bet with
 * the bookmaker during the betting window, and the winner takes the fees minus the house cut. Nobody dies:
 * the blow that would kill ends the fight instead.
 */
public final class FightPit extends Attraction {

    public static final int RADIUS = 4;

    private enum Phase {
        WAITING,
        BETTING,
        FIGHTING,
        CLOSING
    }

    private final CasinoSettings.Pit settings;
    private final InventoryStash stash;
    private final List<UUID> queue = new ArrayList<>();
    private final Map<UUID, Long> selectedChip = new HashMap<>();
    private ParimutuelBook book;
    private Phase phase = Phase.WAITING;
    private UUID red;
    private UUID blue;
    private int countdown;
    private TextDisplay board;

    public FightPit(CasinoContext context, Installation installation, Frame frame, InventoryStash stash) {
        super(context, installation, frame);
        this.settings = context.settings().pit();
        this.stash = stash;
        this.book = new ParimutuelBook(settings.houseCut());
    }

    /** Real blocks: a sunken-looking floor and a fence ring with posts and lanterns. Placed once, by command. */
    public static void build(Frame frame) {
        World world = frame.world();
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                Location at = frame.at(dx, 0, dz);
                Block floor = world.getBlockAt(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ());
                boolean edge = Math.abs(dx) == RADIUS + 1 || Math.abs(dz) == RADIUS + 1;
                floor.setType(edge ? Material.POLISHED_BLACKSTONE_BRICKS
                    : (dx + dz) % 3 == 0 ? Material.COARSE_DIRT : Material.PACKED_MUD, false);
                for (int up = 0; up < 3; up++) {
                    world.getBlockAt(at.getBlockX(), at.getBlockY() + up, at.getBlockZ()).setType(Material.AIR, false);
                }
                if (edge) {
                    boolean post = Math.abs(dx) == RADIUS + 1 && Math.abs(dz) == RADIUS + 1 || dx == 0 || dz == 0;
                    world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ())
                        .setType(post ? Material.STRIPPED_DARK_OAK_LOG : Material.DARK_OAK_FENCE);
                    world.getBlockAt(at.getBlockX(), at.getBlockY() + 1, at.getBlockZ())
                        .setType(post ? Material.STRIPPED_DARK_OAK_LOG : Material.IRON_BARS);
                    if (post) {
                        world.getBlockAt(at.getBlockX(), at.getBlockY() + 2, at.getBlockZ()).setType(Material.LANTERN);
                    }
                }
            }
        }
    }

    @Override
    public void spawn() {
        board = context.kit().text(props, frame.at(0, 4.2, 0), CasinoText.legacy("§4§lFOSO DE RIÑAS"), 1.4f, true,
            0x88000000);
        Location registerAt = frame.at(RADIUS + 2.5, 0, 0, 90);
        context.kit().block(props, registerAt, Material.DARK_OAK_PLANKS.createBlockData(), 0.9f, 1.0f, 0.6f);
        context.kit().text(props, registerAt.clone().add(0, 1.5, 0), CasinoText.legacy(
            "§c§lINSCRIBIRSE\n§fEntrada " + context.chips(settings.entryFee())), 0.6f, true, 0x99000000);
        Interaction register = context.kit().interaction(props, registerAt, 1.0f, 1.3f);
        context.router().bind(register, this::openRegistration);
        Mannequin bookie = context.kit().npc(props, frame.at(-RADIUS - 2.5, 0, 0, -90),
            "§e§lEl Corredor de Apuestas", "§7Paga lo que dice la pizarra", "");
        bookie.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        context.router().bind(bookie, this::openBook);
        refreshBoard();
    }

    @Override
    protected void onTick(long tick) {
        if (phase == Phase.BETTING) {
            if (countdown % 20 == 0) {
                refreshBoard();
                if (countdown / 20 == 10) {
                    announce("§eQuedan 10 segundos para apostar con el Corredor.");
                }
            }
            if (--countdown <= 0) {
                startFight();
            }
        } else if (phase == Phase.FIGHTING) {
            if (countdown % 20 == 0) {
                refreshBoard();
            }
            keepInside(red);
            keepInside(blue);
            if (--countdown <= 0) {
                announce("§7Tiempo cumplido: §fempate. §7Se devuelven entradas y apuestas.");
                finish(null);
            }
        }
    }

    @Override
    public boolean onPlayerDamage(Player victim, Player attacker, double finalDamage) {
        boolean victimFights = phase == Phase.FIGHTING && isFighter(victim.getUniqueId());
        boolean attackerFights = attacker != null && phase == Phase.FIGHTING && isFighter(attacker.getUniqueId());
        if (!victimFights) {
            return attackerFights;
        }
        if (attacker != null && !attackerFights) {
            return true;
        }
        if (finalDamage >= victim.getHealth()) {
            UUID winner = victim.getUniqueId().equals(red) ? blue : red;
            victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            context.sound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            finish(winner);
            return true;
        }
        return false;
    }

    @Override
    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        if (queue.remove(id)) {
            context.escrow().refund(feeKey(id));
        }
        if (isFighter(id) && (phase == Phase.FIGHTING || phase == Phase.BETTING)) {
            announce("§e" + player.getName() + " §7se escapó del foso.");
            finish(phase == Phase.FIGHTING ? (id.equals(red) ? blue : red) : null);
        }
    }

    @Override
    protected void onDespawn() {
        if (phase == Phase.FIGHTING) {
            for (UUID fighter : new UUID[]{red, blue}) {
                Player player = fighter == null ? null : Bukkit.getPlayer(fighter);
                if (player != null) {
                    returnFighter(player);
                }
            }
        }
        context.escrow().refundPrefix("pit:" + installation.id() + ":");
    }

    private void openRegistration(Player player) {
        CasinoMenu menu = new CasinoMenu(3, "§4Foso de Riñas");
        boolean queued = queue.contains(player.getUniqueId());
        menu.set(11, CasinoItems.button(Material.WOODEN_SWORD, queued ? "§7Ya estás anotado" : "§c§lAnotarme a pelear",
            "§7Entrada: §f" + context.chips(settings.entryFee()),
            "§7Premio: §a" + context.chips(prize()),
            "§7Se pelea con espada de madera, sin armadura.",
            "§7Tu inventario queda guardado durante la pelea."), (clicker, click) -> {
            join(clicker);
            clicker.closeInventory();
        });
        menu.set(15, CasinoItems.button(Material.BARRIER, "§7Darme de baja"), (clicker, click) -> {
            if (queue.remove(clicker.getUniqueId())) {
                context.escrow().refund(feeKey(clicker.getUniqueId()));
                clicker.sendMessage("§7Te bajaste del foso. Te devolvimos la entrada.");
                refreshBoard();
            }
            clicker.closeInventory();
        });
        menu.set(13, CasinoItems.button(Material.PAPER, "§eEn espera: §f" + queue.size()), null);
        menu.fill(CasinoItems.filler());
        menu.open(player);
    }

    private void join(Player player) {
        UUID id = player.getUniqueId();
        if (queue.contains(id) || isFighter(id)) {
            return;
        }
        if (stash.has(id)) {
            player.sendMessage("§cTodavía tenés cosas guardadas de otra pelea. Reconectate para recuperarlas.");
            return;
        }
        if (!context.escrow().take(feeKey(id), id, settings.entryFee())) {
            player.sendMessage("§cNo te alcanza para la entrada.");
            return;
        }
        queue.add(id);
        announce("§e" + player.getName() + " §fse anotó para pelear.");
        tryPair();
    }

    private void tryPair() {
        queue.removeIf(queued -> Bukkit.getPlayer(queued) == null && context.escrow().refund(feeKey(queued)) >= 0);
        if (phase == Phase.WAITING && queue.size() >= 2) {
            red = queue.removeFirst();
            blue = queue.removeFirst();
            book = new ParimutuelBook(settings.houseCut());
            phase = Phase.BETTING;
            countdown = settings.bettingSeconds() * 20;
            announce("§c§l" + nameOf(red) + " §fvs §9§l" + nameOf(blue) + "§f. ¡Apuesten con el Corredor!");
        }
        refreshBoard();
    }

    private void openBook(Player player) {
        if (phase != Phase.BETTING) {
            player.sendMessage("§e[Corredor] §7" + (phase == Phase.FIGHTING ? "Ya se están dando. Esperá la próxima."
                : "No hay pelea anunciada todavía."));
            return;
        }
        CasinoMenu menu = new CasinoMenu(3, "§eEl Corredor de Apuestas");
        renderBook(menu, player);
        menu.open(player);
    }

    private void renderBook(CasinoMenu menu, Player player) {
        long chip = selectedChip.getOrDefault(player.getUniqueId(), context.settings().chips().getFirst());
        menu.set(11, corner(ParimutuelBook.Corner.RED, red, player),
            (clicker, click) -> bet(menu, clicker, ParimutuelBook.Corner.RED));
        menu.set(15, corner(ParimutuelBook.Corner.BLUE, blue, player),
            (clicker, click) -> bet(menu, clicker, ParimutuelBook.Corner.BLUE));
        int slot = 19;
        for (long value : context.settings().chips()) {
            if (slot > 25) {
                break;
            }
            menu.set(slot++, CasinoItems.chipButton(value, value == chip), (clicker, click) -> {
                selectedChip.put(clicker.getUniqueId(), value);
                renderBook(menu, clicker);
            });
        }
        menu.fill(CasinoItems.filler());
    }

    private ItemStack corner(ParimutuelBook.Corner corner, UUID fighter, Player viewer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(fighter));
            meta.displayName(CasinoText.legacy((corner == ParimutuelBook.Corner.RED ? "§c§l" : "§9§l") + nameOf(fighter)));
            double odds = book.odds(corner);
            meta.lore(List.of(
                CasinoText.legacy("§7Pozo: §f" + context.chips(book.total(corner))),
                CasinoText.legacy("§7Paga: §e" + (odds == 0 ? "—" : String.format(java.util.Locale.ROOT, "x%.2f", odds))),
                CasinoText.legacy("§7Tu apuesta: §a" + context.chips(book.stake(viewer.getUniqueId(), corner))),
                CasinoText.legacy("§8Click para apostar la ficha elegida")));
            head.setItemMeta(meta);
        }
        return head;
    }

    private void bet(CasinoMenu menu, Player player, ParimutuelBook.Corner corner) {
        UUID id = player.getUniqueId();
        if (phase != Phase.BETTING) {
            return;
        }
        if (isFighter(id)) {
            player.sendMessage("§cLos peleadores no apuestan. Andá a calentar.");
            return;
        }
        long chip = selectedChip.getOrDefault(id, context.settings().chips().getFirst());
        if (!context.escrow().take(betKey(id), id, chip)) {
            player.sendMessage("§cNo te alcanza.");
            return;
        }
        book.bet(id, corner, chip);
        context.sound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.5f);
        renderBook(menu, player);
        refreshBoard();
    }

    private void startFight() {
        Player redPlayer = Bukkit.getPlayer(red);
        Player bluePlayer = Bukkit.getPlayer(blue);
        if (redPlayer == null || bluePlayer == null) {
            announce("§7Uno de los peleadores no está. Se suspende.");
            finish(null);
            return;
        }
        if (!stash.stash(redPlayer)) {
            finish(null);
            return;
        }
        if (!stash.stash(bluePlayer)) {
            returnFighter(redPlayer);
            finish(null);
            return;
        }
        phase = Phase.FIGHTING;
        countdown = settings.fightSeconds() * 20;
        prepare(redPlayer, frame.at(-2.5, 0, 0, -90));
        prepare(bluePlayer, frame.at(2.5, 0, 0, 90));
        for (int second = 3; second >= 1; second--) {
            final int shown = second;
            later((3 - second) * 20 + 1, () -> titleAll("§c§l" + shown, "", Sound.BLOCK_NOTE_BLOCK_BASEDRUM));
        }
        later(61, () -> titleAll("§4§l¡PELEEN!", "§7Sin armas de verdad, sin piedad", Sound.EVENT_RAID_HORN));
        refreshBoard();
    }

    private void prepare(Player player, Location spot) {
        player.eject();
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        player.teleport(spot);
        player.getActivePotionEffects().stream().map(PotionEffect::getType).forEach(player::removePotionEffect);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setFireTicks(0);
        ItemStack sword = CasinoItems.button(Material.WOODEN_SWORD, "§6Espada de la casa", "§7Idéntica para los dos.");
        player.getInventory().setItem(0, sword);
        player.getInventory().setHeldItemSlot(0);
    }

    private void finish(UUID winner) {
        if (phase == Phase.CLOSING || phase == Phase.WAITING) {
            return;
        }
        boolean fought = phase == Phase.FIGHTING;
        phase = Phase.CLOSING;
        for (UUID fighter : new UUID[]{red, blue}) {
            Player player = Bukkit.getPlayer(fighter);
            if (player != null && fought) {
                returnFighter(player);
            }
        }
        if (winner == null) {
            context.escrow().refund(feeKey(red));
            context.escrow().refund(feeKey(blue));
            book.refundAll().keySet().forEach(bettor -> context.escrow().refund(betKey(bettor)));
        } else {
            UUID loser = winner.equals(red) ? blue : red;
            context.escrow().settle(feeKey(loser), 0);
            context.escrow().settle(feeKey(winner), prize());
            Map<UUID, Long> payouts = book.settle(winner.equals(red) ? ParimutuelBook.Corner.RED : ParimutuelBook.Corner.BLUE);
            for (String key : context.escrow().snapshot().keySet()) {
                if (key.startsWith("pit:" + installation.id() + ":bet:")) {
                    UUID bettor = UUID.fromString(key.substring(key.lastIndexOf(':') + 1));
                    context.escrow().settle(key, payouts.getOrDefault(bettor, 0L));
                    Player online = Bukkit.getPlayer(bettor);
                    long paid = payouts.getOrDefault(bettor, 0L);
                    if (online != null) {
                        online.sendMessage(paid > 0 ? "§e[Corredor] §aCobrás " + context.chips(paid) + "."
                            : "§e[Corredor] §cPerdiste la apuesta.");
                    }
                }
            }
            String colour = winner.equals(red) ? "§c§l" : "§9§l";
            announce(colour + nameOf(winner) + " §fganó la riña y se lleva §6" + context.chips(prize()) + "§f.");
            Player champion = Bukkit.getPlayer(winner);
            if (champion != null) {
                champion.playSound(champion.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        later(60, () -> {
            red = null;
            blue = null;
            phase = Phase.WAITING;
            book = new ParimutuelBook(settings.houseCut());
            refreshBoard();
            tryPair();
        });
    }

    private void returnFighter(Player player) {
        if (!stash.has(player.getUniqueId())) {
            return;
        }
        player.getInventory().clear();
        Location back = stash.restore(player);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20 : maxHealth.getValue());
        player.teleport(back != null && back.getWorld() != null ? back : frame.at(RADIUS + 3, 0, 0));
    }

    private void keepInside(UUID fighter) {
        Player player = fighter == null ? null : Bukkit.getPlayer(fighter);
        if (player == null) {
            return;
        }
        Location location = player.getLocation();
        double dx = location.getX() - frame.x();
        double dz = location.getZ() - frame.z();
        if (location.getWorld() != frame.world() || Math.abs(dx) > RADIUS + 0.7 || Math.abs(dz) > RADIUS + 0.7
            || Math.abs(location.getY() - frame.y()) > 4) {
            player.teleport(frame.at(0, 0, 0));
            player.sendActionBar(Component.text("¡De acá no se sale hasta que termine!"));
        }
    }

    private long prize() {
        return Math.round(settings.entryFee() * 2 * (1.0 - settings.houseCut()));
    }

    private void refreshBoard() {
        if (board == null || !board.isValid()) {
            return;
        }
        String text = switch (phase) {
            case WAITING -> "§4§lFOSO DE RIÑAS\n§7Anotados: §f" + queue.size() + "/2 §7· Premio §a" + context.chips(prize());
            case BETTING -> "§4§lFOSO DE RIÑAS\n§c" + nameOf(red) + " §7(x" + odds(ParimutuelBook.Corner.RED) + ") §fvs §9"
                + nameOf(blue) + " §7(x" + odds(ParimutuelBook.Corner.BLUE) + ")\n§eApuestas: §f" + countdown / 20 + "s";
            case FIGHTING -> "§4§l¡PELEA!\n§c" + nameOf(red) + " §fvs §9" + nameOf(blue) + "\n§7" + countdown / 20 + "s";
            case CLOSING -> "§4§lFOSO DE RIÑAS\n§7Limpiando la sangre...";
        };
        board.text(CasinoText.legacy(text));
    }

    private String odds(ParimutuelBook.Corner corner) {
        double odds = book.odds(corner);
        return odds == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.2f", odds);
    }

    private void titleAll(String title, String subtitle, Sound sound) {
        for (Player player : context.playersNear(frame.origin(), context.settings().ambientRadius())) {
            player.showTitle(Title.title(CasinoText.legacy(title), CasinoText.legacy(subtitle),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
            player.playSound(player.getLocation(), sound, 0.8f, 1f);
        }
    }

    private boolean isFighter(UUID player) {
        return player.equals(red) || player.equals(blue);
    }

    private void announce(String text) {
        context.broadcastNear(frame.origin(), context.settings().ambientRadius(), "§4[Foso] §f" + text);
    }

    private String nameOf(UUID player) {
        if (player == null) {
            return "?";
        }
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? "?" : name;
    }

    private String feeKey(UUID player) {
        return "pit:" + installation.id() + ":fee:" + player;
    }

    private String betKey(UUID player) {
        return "pit:" + installation.id() + ":bet:" + player;
    }
}
