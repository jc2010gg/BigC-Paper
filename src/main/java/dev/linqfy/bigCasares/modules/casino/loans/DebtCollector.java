package dev.linqfy.bigCasares.modules.casino.loans;

import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "El Cobrador de la AFIP": a mannequin in a cheap suit that walks after every online debtor with an overdue
 * loan. When it catches them it takes their cash, then their tools, then the contents of chests they opened
 * nearby, and sells everything through the pawn shop window. It cannot be hurt; punching it costs extra.
 */
public final class DebtCollector {

    private static final double SPEED = 0.2;
    private static final double REACH = 1.9;
    private static final double LEASH = 48;
    private static final Set<String> TOOL_SUFFIXES = Set.of("_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD", "_SPEAR");
    private static final Set<Material> TOOLS = Set.of(Material.TRIDENT, Material.MACE, Material.BOW, Material.CROSSBOW,
        Material.SHEARS, Material.FISHING_ROD, Material.BRUSH, Material.FLINT_AND_STEEL);
    /** Seized goods count for half their appraisal: the AFIP is not generous. */
    private static final double SEIZURE_CREDIT = 0.5;

    private final CasinoContext context;
    private final LoanService loans;
    private final Map<UUID, Agent> agents = new HashMap<>();
    private long tick;

    public DebtCollector(CasinoContext context, LoanService loans) {
        this.context = context;
        this.loans = loans;
    }

    public void tick() {
        tick++;
        if (tick % 40 == 0) {
            for (Loan loan : loans.loans()) {
                Player debtor = Bukkit.getPlayer(loan.debtor());
                if (debtor != null && !agents.containsKey(loan.debtor()) && loans.overdue(loan.debtor())
                    && !debtor.isDead()) {
                    dispatch(debtor);
                }
            }
        }
        for (Agent agent : List.copyOf(agents.values())) {
            step(agent);
        }
    }

    public boolean isCollector(Entity entity) {
        return agents.values().stream().anyMatch(agent -> agent.npc.equals(entity));
    }

    public void punched(Player player, Entity entity) {
        Agent agent = agents.values().stream().filter(candidate -> candidate.npc.equals(entity)).findFirst().orElse(null);
        if (agent == null) {
            return;
        }
        if (!agent.debtor.equals(player.getUniqueId())) {
            player.sendMessage("§c[AFIP] §7Usted no está en mi lista. Todavía.");
            return;
        }
        long extra = loans.surcharge(player.getUniqueId());
        player.setVelocity(player.getLocation().toVector().subtract(agent.npc.getLocation().toVector()).setY(0)
            .normalize().multiply(0.8).setY(0.3));
        agent.npc.swingMainHand();
        player.sendMessage("§c[AFIP] §fResistencia a la autoridad: §c+" + context.chips(extra) + " §fa su deuda.");
        context.sound(agent.npc.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.6f);
    }

    public void dismissAll() {
        for (Agent agent : List.copyOf(agents.values())) {
            dismiss(agent, null);
        }
    }

    private void dispatch(Player debtor) {
        Location spawn = ambushSpot(debtor, 12 + context.random().nextInt(6));
        Mannequin npc = spawn.getWorld().spawn(spawn, Mannequin.class, mannequin -> {
            context.kit().tag(mannequin);
            mannequin.setPersistent(false);
            mannequin.setInvulnerable(true);
            mannequin.setImmovable(true);
            mannequin.setGravity(false);
            mannequin.setCollidable(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.customName(CasinoText.legacy("§c§lEl Cobrador de la AFIP"));
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(CasinoText.legacy("§7Orden de embargo N° " + (1000 + context.random().nextInt(9000))));
            mannequin.getEquipment().setChestplate(suit(Material.LEATHER_CHESTPLATE));
            mannequin.getEquipment().setLeggings(suit(Material.LEATHER_LEGGINGS));
            mannequin.getEquipment().setBoots(suit(Material.LEATHER_BOOTS));
            mannequin.getEquipment().setItemInMainHand(CasinoItems.button(Material.PAPER, "§fOrden de embargo"));
            mannequin.getEquipment().setItemInOffHand(new ItemStack(Material.BOOK));
            DisplayKit.applySkin(mannequin, loans.settings().collectorSkin());
        });
        Agent agent = new Agent(debtor.getUniqueId(), npc);
        agents.put(debtor.getUniqueId(), agent);
        debtor.showTitle(Title.title(CasinoText.legacy("§c§lLA AFIP TE ENCONTRÓ"),
            CasinoText.legacy("§7Debés §c" + context.chips(loans.balance(debtor.getUniqueId())) + " §7a Don Casares"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
        debtor.playSound(debtor.getLocation(), Sound.EVENT_MOB_EFFECT_RAID_OMEN, 1f, 0.7f);
        spawn.getWorld().spawnParticle(Particle.LARGE_SMOKE, spawn.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
    }

    private void step(Agent agent) {
        Player debtor = Bukkit.getPlayer(agent.debtor);
        if (!agent.npc.isValid()) {
            agents.remove(agent.debtor);
            return;
        }
        if (debtor == null) {
            dismiss(agent, null);
            return;
        }
        if (!loans.overdue(agent.debtor)) {
            dismiss(agent, "§c[AFIP] §fDeuda regularizada. Que tenga buen día.");
            return;
        }
        if (debtor.isDead()) {
            return;
        }
        Location here = agent.npc.getLocation();
        Location target = debtor.getLocation();
        if (here.getWorld() != target.getWorld() || here.distanceSquared(target) > LEASH * LEASH) {
            teleport(agent, ambushSpot(debtor, 8));
            debtor.sendMessage("§c[AFIP] §7La AFIP siempre te encuentra.");
            return;
        }
        double distance = here.distance(target);
        if (distance <= REACH) {
            face(agent, target);
            if (tick - agent.lastSeizureTick >= loans.settings().seizureCooldownSeconds() * 20L) {
                agent.lastSeizureTick = tick;
                seize(agent, debtor);
            }
            return;
        }
        Vector direction = target.toVector().subtract(here.toVector()).setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }
        direction.normalize().multiply(SPEED);
        Location next = here.clone().add(direction);
        Double ground = groundAt(next);
        if (ground == null) {
            agent.stuckTicks++;
            if (agent.stuckTicks > 30) {
                agent.stuckTicks = 0;
                teleport(agent, ambushSpot(debtor, 3));
            }
            return;
        }
        agent.stuckTicks = 0;
        next.setY(ground);
        next.setDirection(target.toVector().subtract(next.toVector()));
        next.setPitch(0);
        agent.npc.teleport(next);
        if (tick % 9 == 0) {
            context.sound(next, Sound.BLOCK_STONE_STEP, 0.5f, 1.1f);
        }
        if (tick % 60 == 0) {
            agent.npc.swingMainHand();
            String line = switch (context.random().nextInt(4)) {
                case 0 -> "¡Alto ahí, contribuyente!";
                case 1 -> "Tengo una orden de embargo a su nombre.";
                case 2 -> "No corra, que es peor.";
                default -> "Monotributo vencido, préstamo vencido... ¡qué vergüenza!";
            };
            debtor.sendMessage("§c[AFIP] §f" + line);
        }
    }

    private void seize(Agent agent, Player debtor) {
        UUID id = debtor.getUniqueId();
        long before = loans.balance(id);
        long cash = loans.repayFromBalance(id, before);
        long goods = 0;
        int items = 0;
        agent.npc.swingMainHand();
        context.sound(debtor.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);

        Inventory inventory = debtor.getInventory();
        for (int slot = 0; slot < inventory.getSize() && loans.balance(id) > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isTool(item)) {
                continue;
            }
            long value = context.services().appraise(item);
            if (value <= 0) {
                continue;
            }
            inventory.setItem(slot, null);
            context.services().consign(item, value);
            goods += loans.credit(id, Math.max(1, Math.round(value * SEIZURE_CREDIT)));
            items++;
        }

        int bustedChests = 0;
        if (loans.balance(id) > 0 && loans.settings().seizeChests()) {
            double radius = loans.settings().chestRadius();
            for (LoanService.ChestRef ref : loans.chests(id)) {
                if (loans.balance(id) <= 0) {
                    break;
                }
                Location location = ref.location();
                if (location == null || location.getWorld() != debtor.getWorld()
                    || location.distanceSquared(debtor.getLocation()) > radius * radius
                    || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    continue;
                }
                Block block = location.getBlock();
                if (!(block.getState() instanceof Container container)) {
                    continue;
                }
                Inventory chest = container.getInventory();
                boolean took = false;
                for (int slot = 0; slot < chest.getSize() && loans.balance(id) > 0; slot++) {
                    ItemStack item = chest.getItem(slot);
                    long value = item == null ? 0 : context.services().appraise(item);
                    if (value <= 0) {
                        continue;
                    }
                    chest.setItem(slot, null);
                    context.services().consign(item, value);
                    goods += loans.credit(id, Math.max(1, Math.round(value * SEIZURE_CREDIT)));
                    items++;
                    took = true;
                }
                if (took) {
                    bustedChests++;
                    World world = location.getWorld();
                    Location centre = location.clone().add(0.5, 0.6, 0.5);
                    world.spawnParticle(Particle.EXPLOSION, centre, 1);
                    world.spawnParticle(Particle.BLOCK, centre, 30, 0.3, 0.3, 0.3, block.getBlockData());
                    world.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
                    world.playSound(centre, Sound.BLOCK_CHEST_OPEN, 1f, 0.5f);
                }
            }
        }

        long after = loans.balance(id);
        StringBuilder report = new StringBuilder("§c[AFIP] §fEmbargo ejecutado:");
        if (cash > 0) {
            report.append(" §e").append(context.chips(cash)).append(" §fen efectivo");
        }
        if (items > 0) {
            report.append(cash > 0 ? "," : "").append(" §e").append(items).append(" §fítems (")
                .append(context.chips(goods)).append(")");
        }
        if (bustedChests > 0) {
            report.append(" §fde §c").append(bustedChests).append(" §fcofre(s) reventado(s)");
        }
        if (cash == 0 && items == 0) {
            report.append(" §7no tiene nada que valga la pena. Volveré.");
        }
        debtor.sendMessage(report.toString());
        if (after <= 0) {
            dismiss(agent, "§c[AFIP] §fDeuda saldada. El Estado le agradece su contribución.");
        } else {
            debtor.sendMessage("§c[AFIP] §7Saldo pendiente: §c" + context.chips(after) + "§7. Nos volveremos a ver.");
            debtor.sendActionBar(Component.text("Deuda: " + context.chips(after)));
        }
    }

    private void dismiss(Agent agent, String message) {
        agents.remove(agent.debtor);
        if (agent.npc.isValid()) {
            Location at = agent.npc.getLocation();
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
            agent.npc.remove();
        }
        Player debtor = Bukkit.getPlayer(agent.debtor);
        if (debtor != null && message != null) {
            debtor.sendMessage(message);
        }
    }

    private void teleport(Agent agent, Location location) {
        agent.npc.getWorld().spawnParticle(Particle.LARGE_SMOKE, agent.npc.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
        agent.npc.teleport(location);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
    }

    private void face(Agent agent, Location target) {
        Location here = agent.npc.getLocation();
        here.setDirection(target.toVector().subtract(here.toVector()));
        here.setPitch(0);
        agent.npc.setRotation(here.getYaw(), 0);
    }

    /** A standable spot about {@code distance} blocks from the player, behind them if possible. */
    private Location ambushSpot(Player player, double distance) {
        Location base = player.getLocation();
        Vector behind = base.getDirection().setY(0);
        if (behind.lengthSquared() < 1.0E-4) {
            behind = new Vector(1, 0, 0);
        }
        behind.normalize().multiply(-1);
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = (context.random().nextDouble() - 0.5) * Math.PI * (attempt < 6 ? 0.6 : 2.0);
            Vector offset = behind.clone().rotateAroundY(angle).multiply(distance);
            Location candidate = base.clone().add(offset);
            Double ground = groundAt(candidate, 6);
            if (ground != null) {
                candidate.setY(ground);
                candidate.setDirection(base.toVector().subtract(candidate.toVector()));
                candidate.setPitch(0);
                return candidate;
            }
        }
        return base.clone().add(behind.multiply(1.5));
    }

    private static Double groundAt(Location location) {
        return groundAt(location, 2);
    }

    /** Feet height of a spot with solid ground and two free blocks, searched from one block up to {@code drop} down. */
    private static Double groundAt(Location location, int drop) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int start = (int) Math.floor(location.getY()) + 1;
        for (int y = start; y >= start - drop - 1; y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (below.isSolid() && feet.isPassable() && head.isPassable()) {
                return (double) y;
            }
        }
        return null;
    }

    private static boolean isTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String name = item.getType().name().toUpperCase(Locale.ROOT);
        return TOOLS.contains(item.getType()) || TOOL_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private static ItemStack suit(Material material) {
        ItemStack piece = new ItemStack(material);
        if (piece.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(0x1C1C2E));
            piece.setItemMeta(meta);
        }
        return piece;
    }

    private static final class Agent {
        private final UUID debtor;
        private final Mannequin npc;
        private long lastSeizureTick = -100_000;
        private int stuckTicks;

        private Agent(UUID debtor, Mannequin npc) {
            this.debtor = debtor;
            this.npc = npc;
        }
    }
}
