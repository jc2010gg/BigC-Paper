package dev.linqfy.bigCasares.modules.casino.roulette;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.EntityGroup;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A big single-zero wheel built from display entities. Bets are open for most of each cycle; then the
 * croupier calls "¡No va más!", the wheel and the ball spin in real time for everyone, and the ball's
 * ratchet clicks slow down until it settles on the winning pocket.
 */
public final class RouletteTable extends Attraction {

    private static final double WHEEL_HEIGHT = 1.05;
    private static final double RIM_RADIUS = 3.55;
    private static final double POCKET_RADIUS = 2.75;
    private static final double BOARD_FORWARD = 5.2;
    private static final double CELL = 0.42;
    private static final List<RouletteBet> OUTSIDE = List.of(
        RouletteBet.of(RouletteBet.Kind.RED), RouletteBet.of(RouletteBet.Kind.BLACK),
        RouletteBet.of(RouletteBet.Kind.EVEN), RouletteBet.of(RouletteBet.Kind.ODD),
        RouletteBet.of(RouletteBet.Kind.LOW), RouletteBet.of(RouletteBet.Kind.HIGH),
        new RouletteBet(RouletteBet.Kind.DOZEN, 1), new RouletteBet(RouletteBet.Kind.DOZEN, 2),
        new RouletteBet(RouletteBet.Kind.DOZEN, 3));

    private enum Phase {
        BETTING,
        SPINNING,
        PAYING
    }

    private final CasinoSettings.Roulette settings;
    private final List<Rotating> rotating = new ArrayList<>();
    private final Map<Integer, BlockDisplay> pocketDisplays = new HashMap<>();
    private final Map<UUID, Map<RouletteBet, Long>> bets = new LinkedHashMap<>();
    private final Map<UUID, Long> selectedChip = new HashMap<>();
    private final EntityGroup chips = new EntityGroup();
    private final List<Integer> history = new ArrayList<>();
    private Phase phase = Phase.BETTING;
    private int countdown;
    private SpinPlan plan;
    private int spinTick;
    private int lastPocket = -1;
    private double wheelAngle;
    private BlockDisplay ball;
    private TextDisplay sign;
    private Mannequin croupier;

    public RouletteTable(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
        this.settings = context.settings().roulette();
        this.countdown = bettingTicks();
    }

    @Override
    public void spawn() {
        DisplayKit kit = context.kit();
        Location centre = frame.at(0, 0, 0);
        // Wheel parts use world angles, so they are spawned unrotated.
        Location wheel = frame.at(0, WHEEL_HEIGHT, 0);
        wheel.setYaw(0);
        // Pedestal and bowl.
        kit.block(props, centre, Material.POLISHED_BLACKSTONE.createBlockData(), 5.4f, 0.7f, 5.4f);
        kit.block(props, frame.at(0, 0.7, 0), Material.DARK_OAK_PLANKS.createBlockData(), 8.2f, 0.25f, 8.2f);
        for (int side = 0; side < 16; side++) {
            double angle = side * Math.PI / 8;
            Quaternionf q = yawFor(angle);
            BlockDisplay rim = kit.block(props, wheel.clone().subtract(0, 0.1, 0),
                Material.GOLD_BLOCK.createBlockData(), 1.6f, 0.35f, 0.18f);
            rim.setTransformation(placed(q, RIM_RADIUS + 0.35, -0.1 + 0.1, 1.6f, 0.35f, 0.18f));
        }
        kit.block(props, frame.at(0, 0.95, 0), Material.BROWN_TERRACOTTA.createBlockData(), 7.4f, 0.1f, 7.4f);

        // Rotating wheel: pockets, separators, numbers and the gold turret.
        for (int index = 0; index < RoulettePockets.COUNT; index++) {
            int number = RoulettePockets.WHEEL_ORDER.get(index);
            double angle = index * RoulettePockets.pocketWidth();
            BlockData data = switch (RoulettePockets.color(number)) {
                case RED -> Material.RED_CONCRETE.createBlockData();
                case BLACK -> Material.BLACK_CONCRETE.createBlockData();
                case GREEN -> Material.LIME_CONCRETE.createBlockData();
            };
            BlockDisplay pocket = spawnRotating(data, angle, (RIM_RADIUS + POCKET_RADIUS) / 2 - 0.15, 0.0,
                0.52f, 0.1f, 1.2f);
            pocketDisplays.put(number, pocket);
            spawnRotating(Material.GOLD_BLOCK.createBlockData(), angle + RoulettePockets.pocketWidth() / 2,
                POCKET_RADIUS + 0.25, 0.1, 0.04f, 0.08f, 0.55f);
            TextDisplay label = kit.text(props, wheel,
                CasinoText.legacy("§f§l" + number), 0.5f, false, 0x00000000);
            label.setInterpolationDuration(2);
            rotating.add(new Rotating(label, angle, RIM_RADIUS - 0.3, 0.11, 0.5f, 0.5f, 0.5f, true));
        }
        spawnRotating(Material.MANGROVE_PLANKS.createBlockData(), 0, 0, 0.0, 4.9f, 0.08f, 4.9f);
        spawnRotating(Material.MANGROVE_PLANKS.createBlockData(), Math.PI / 4, 0, 0.0, 4.9f, 0.08f, 4.9f);
        for (int arm = 0; arm < 4; arm++) {
            spawnRotating(Material.GOLD_BLOCK.createBlockData(), arm * Math.PI / 2, 0.55, 0.08, 0.12f, 0.12f, 1.1f);
        }
        spawnRotating(Material.GOLD_BLOCK.createBlockData(), 0, 0, 0.08, 0.5f, 0.45f, 0.5f);

        ball = kit.block(props, ballLocation(0, 1.0, 0), Material.WHITE_CONCRETE.createBlockData(), 0.16f, 0.16f, 0.16f);
        ball.setTeleportDuration(1);
        ball.setGlowing(true);
        ball.setGlowColorOverride(Color.WHITE);

        sign = kit.text(props, frame.at(0, 4.4, 0), CasinoText.legacy("§6§lLA GRAN RULETA"), 1.6f, true, 0x88000000);
        croupier = kit.npc(props, frame.at(-4.4, 0, BOARD_FORWARD - 0.2, 90), "§6§lCrupier §f· §eRuleta",
            "§7¡Hagan sus apuestas!", "");
        context.router().bind(croupier, this::openMenu);
        spawnBoard();
        applyWheel(0);
        placeBall(0, 1.0, 0);
    }

    private void spawnBoard() {
        DisplayKit kit = context.kit();
        Frame board = frame.child(0, 0, BOARD_FORWARD, 0);
        kit.block(props, board.at(0, 0, 0), Material.DARK_OAK_PLANKS.createBlockData(), 6.4f, 0.85f, 2.4f);
        kit.block(props, board.at(0, 0.85, 0), Material.GREEN_WOOL.createBlockData(), 6.3f, 0.06f, 2.3f);
        for (int number = 0; number <= 36; number++) {
            Location cell = cellLocation(number);
            BlockData data = switch (RoulettePockets.color(number)) {
                case RED -> Material.RED_CONCRETE.createBlockData();
                case BLACK -> Material.BLACK_CONCRETE.createBlockData();
                case GREEN -> Material.LIME_CONCRETE.createBlockData();
            };
            float depth = number == 0 ? (float) (CELL * 3 - 0.04) : (float) (CELL - 0.04);
            kit.block(props, cell, data, (float) (CELL - 0.04), 0.01f, depth);
            flatText(cell.clone().add(0, 0.012, 0), "§f§l" + number, 0.45f);
        }
        for (RouletteBet bet : OUTSIDE) {
            Location spot = spotFor(bet);
            kit.block(props, spot, Material.GREEN_TERRACOTTA.createBlockData(), 0.62f, 0.01f,
                (float) (CELL - 0.05));
            flatText(spot.clone().add(0, 0.012, 0), bet.label(), 0.32f);
        }
        for (int part = -2; part <= 2; part++) {
            Interaction hit = kit.interaction(props, board.at(part * 1.25, 0, 0.6), 1.3f, 1.0f);
            context.router().bind(hit, this::openMenu);
        }
    }

    private void flatText(Location at, String text, float scale) {
        TextDisplay display = context.kit().text(props, at, CasinoText.legacy(text), scale, false, 0x00000000);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(
            (float) -Math.PI / 2, 1, 0, 0)), new Vector3f(scale), new Quaternionf()));
    }

    /** Cell of a number: twelve columns along the frame's right axis, three rows toward the wheel. */
    private Location cellLocation(int number) {
        Frame board = frame.child(0, 0, BOARD_FORWARD, 0);
        if (number == 0) {
            return board.at(-6.5 * CELL, 0.91, -0.15);
        }
        int column = (number - 1) / 3;
        int row = (number - 1) % 3;
        return board.at((column - 5.5) * CELL, 0.91, -0.15 + (1 - row) * CELL);
    }

    private Location spotFor(RouletteBet bet) {
        Frame board = frame.child(0, 0, BOARD_FORWARD, 0);
        if (bet.kind() == RouletteBet.Kind.STRAIGHT) {
            return cellLocation(bet.number());
        }
        int index = OUTSIDE.indexOf(bet);
        return board.at((index - 4) * 0.66, 0.91, 0.62);
    }

    @Override
    protected void onTick(long tick) {
        if (context.playersNear(frame.origin(), context.settings().ambientRadius()).isEmpty() && bets.isEmpty()
            && phase == Phase.BETTING) {
            idleSpin();
            return;
        }
        switch (phase) {
            case BETTING -> {
                idleSpin();
                if (countdown % 20 == 0) {
                    int seconds = countdown / 20;
                    sign.text(CasinoText.legacy("§6§lLA GRAN RULETA\n§e¡Hagan sus apuestas! §f" + seconds + "s"
                        + historyLine()));
                    if (seconds == 10) {
                        announce("§e¡Últimos 10 segundos para apostar!");
                    }
                }
                if (--countdown <= 0) {
                    startSpin();
                }
            }
            case SPINNING -> spinStep();
            case PAYING -> {
            }
        }
    }

    private void idleSpin() {
        wheelAngle += 0.004;
        if (ticks() % 2 == 0) {
            applyWheel(wheelAngle);
            placeBall(wheelAngle + RoulettePockets.angleOf(history.isEmpty() ? 0 : history.getLast()), 0.0, 0);
        }
    }

    private void startSpin() {
        phase = Phase.SPINNING;
        int result = RoulettePockets.WHEEL_ORDER.get(context.random().nextInt(RoulettePockets.COUNT));
        double ballStart = wheelAngle + context.random().nextDouble() * Math.PI * 2;
        plan = new SpinPlan(result, settings.spinSeconds() * 20, wheelAngle, ballStart, 2, 6 + context.random().nextInt(3));
        spinTick = 0;
        lastPocket = -1;
        announce("§6§l¡No va más!");
        for (Player player : context.playersNear(frame.origin(), context.settings().ambientRadius())) {
            player.showTitle(Title.title(Component.empty(), CasinoText.legacy("§6§l¡No va más!"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))));
        }
        context.sound(frame.origin(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f);
        sign.text(CasinoText.legacy("§6§lLA GRAN RULETA\n§c§l¡No va más!" + historyLine()));
    }

    private void spinStep() {
        spinTick++;
        if (spinTick % 2 == 0) {
            applyWheel(plan.wheelAngle(spinTick));
        }
        placeBall(plan.ballAngle(spinTick), plan.ballRadiusFactor(spinTick), plan.ballHeight(spinTick, 0.35));
        int pocket = plan.pocketUnderBall(spinTick);
        if (pocket != lastPocket) {
            lastPocket = pocket;
            float pitch = (float) Math.min(2.0, 0.8 + (1.0 - spinTick / (double) plan.durationTicks()) * 1.2);
            context.sound(ball.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.55f, pitch);
        }
        if (plan.ballHeight(spinTick, 1) > 0 && plan.ballHeight(spinTick - 1, 1) < plan.ballHeight(spinTick, 1)
            && plan.ballHeight(spinTick - 1, 1) <= plan.ballHeight(spinTick - 2, 1)) {
            context.sound(ball.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.6f);
        }
        if (plan.finished(spinTick)) {
            wheelAngle = plan.wheelAngle(spinTick);
            resolve(plan.result());
        }
    }

    private void resolve(int result) {
        phase = Phase.PAYING;
        history.add(result);
        while (history.size() > 8) {
            history.removeFirst();
        }
        String color = RoulettePockets.legacyColor(result);
        String colorName = switch (RoulettePockets.color(result)) {
            case RED -> "ROJO";
            case BLACK -> "NEGRO";
            case GREEN -> "VERDE";
        };
        announce("§fSalió el " + color + "§l" + result + " §f(" + color + colorName + "§f)");
        sign.text(CasinoText.legacy("§6§lLA GRAN RULETA\n§fSalió " + color + "§l" + result + historyLine()));
        BlockDisplay winner = pocketDisplays.get(result);
        winner.setGlowColorOverride(Color.YELLOW);
        winner.setGlowing(true);
        Location burst = ball.getLocation();
        burst.getWorld().spawnParticle(Particle.FIREWORK, burst, 30, 0.3, 0.3, 0.3, 0.05);
        context.sound(burst, Sound.BLOCK_BELL_USE, 1.0f, 1.2f);

        for (Map.Entry<UUID, Map<RouletteBet, Long>> entry : bets.entrySet()) {
            long payout = 0;
            long staked = 0;
            for (Map.Entry<RouletteBet, Long> bet : entry.getValue().entrySet()) {
                staked += bet.getValue();
                payout += bet.getKey().payout(bet.getValue(), result);
            }
            context.escrow().settle(key(entry.getKey()), payout);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                if (payout > 0) {
                    player.sendMessage("§6[Ruleta] §a¡Cobrás " + context.chips(payout) + "! §7(apostaste "
                        + context.chips(staked) + ")");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                } else {
                    player.sendMessage("§6[Ruleta] §cLa casa se queda con " + context.chips(staked) + ".");
                }
            }
        }
        bets.clear();
        later(100, () -> {
            chips.removeAll();
            winner.setGlowing(false);
            phase = Phase.BETTING;
            countdown = bettingTicks();
        });
    }

    private void openMenu(Player player) {
        CasinoMenu menu = new CasinoMenu(6, "§6La Gran Ruleta · Apuestas");
        renderMenu(menu, player);
        menu.open(player);
    }

    /** Fills (or refreshes in place) the betting menu, so fast clicks never land on a closing window. */
    private void renderMenu(CasinoMenu menu, Player player) {
        long chip = selectedChip.getOrDefault(player.getUniqueId(), context.settings().chips().getFirst());
        Map<RouletteBet, Long> mine = bets.getOrDefault(player.getUniqueId(), Map.of());
        for (int number = 0; number <= 36; number++) {
            RouletteBet bet = RouletteBet.straight(number);
            Material material = switch (RoulettePockets.color(number)) {
                case RED -> Material.RED_CONCRETE;
                case BLACK -> Material.BLACK_CONCRETE;
                case GREEN -> Material.LIME_CONCRETE;
            };
            menu.set(number, betButton(material, bet, mine), (clicker, click) -> clickBet(menu, clicker, bet, click));
        }
        Material[] outsideIcons = {Material.RED_WOOL, Material.BLACK_WOOL, Material.WHITE_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.BROWN_WOOL};
        for (int index = 0; index < OUTSIDE.size(); index++) {
            RouletteBet bet = OUTSIDE.get(index);
            menu.set(37 + index, betButton(outsideIcons[index], bet, mine), (clicker, click) -> clickBet(menu, clicker, bet, click));
        }
        long total = mine.values().stream().mapToLong(Long::longValue).sum();
        menu.set(46, CasinoItems.button(Material.GOLD_INGOT, "§6Tus fichas en la mesa: §e" + context.chips(total),
            "§7Saldo: §f" + context.chips(context.bank().balance(player.getUniqueId())),
            "§7Click izquierdo: poner ficha", "§7Click derecho: sacar ficha"), null);
        int slot = 48;
        for (long value : context.settings().chips()) {
            if (slot > 53) {
                break;
            }
            menu.set(slot++, CasinoItems.chipButton(value, value == chip), (clicker, click) -> {
                selectedChip.put(clicker.getUniqueId(), value);
                renderMenu(menu, clicker);
            });
        }
        menu.fill(CasinoItems.filler());
    }

    private org.bukkit.inventory.ItemStack betButton(Material material, RouletteBet bet, Map<RouletteBet, Long> mine) {
        long stake = mine.getOrDefault(bet, 0L);
        org.bukkit.inventory.ItemStack item = CasinoItems.button(material, bet.label(),
            "§7Paga §f" + bet.kind().odds() + " a 1",
            stake > 0 ? "§aTu apuesta: §e" + context.chips(stake) : "§8Sin apuesta");
        if (stake > 0) {
            var meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void clickBet(CasinoMenu menu, Player player, RouletteBet bet, ClickType click) {
        if (phase != Phase.BETTING) {
            player.sendMessage("§c¡No va más! Esperá la próxima bola.");
            return;
        }
        UUID id = player.getUniqueId();
        long chip = selectedChip.getOrDefault(id, context.settings().chips().getFirst());
        Map<RouletteBet, Long> mine = bets.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
        if (click.isRightClick()) {
            long current = mine.getOrDefault(bet, 0L);
            long removed = Math.min(chip, current);
            if (removed > 0) {
                long held = context.escrow().held(key(id));
                context.escrow().adjust(key(id), id, held - removed);
                context.bank().deposit(id, removed);
                if (current - removed == 0) {
                    mine.remove(bet);
                } else {
                    mine.put(bet, current - removed);
                }
            }
        } else {
            long total = mine.values().stream().mapToLong(Long::longValue).sum();
            if (total + chip > settings.maxBet()) {
                player.sendMessage("§cTope de la mesa: " + context.chips(settings.maxBet()) + " por bola.");
                return;
            }
            if (!context.escrow().take(key(id), id, chip)) {
                player.sendMessage("§cNo te alcanza para esa ficha.");
                return;
            }
            mine.merge(bet, chip, Long::sum);
            context.sound(spotFor(bet), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.7f);
        }
        if (mine.isEmpty()) {
            bets.remove(id);
        }
        refreshChips();
        renderMenu(menu, player);
    }

    private void refreshChips() {
        chips.removeAll();
        Map<RouletteBet, Long> totals = new LinkedHashMap<>();
        bets.values().forEach(map -> map.forEach((bet, amount) -> totals.merge(bet, amount, Long::sum)));
        totals.forEach((bet, amount) -> {
            Location spot = spotFor(bet).add(0, 0.03, 0);
            spot.setYaw(frame.yaw());
            context.kit().item(chips, spot, CasinoItems.chip(amount), 0.28f, DisplayKit.FLAT);
        });
    }

    @Override
    protected void onDespawn() {
        context.escrow().refundPrefix("roulette:" + installation.id() + ":");
        bets.clear();
        chips.removeAll();
    }

    private BlockDisplay spawnRotating(BlockData data, double angle, double radius, double height,
                                       float sx, float sy, float sz) {
        Location wheel = frame.at(0, WHEEL_HEIGHT, 0);
        wheel.setYaw(0);
        BlockDisplay display = context.kit().block(props, wheel, data, sx, sy, sz);
        display.setInterpolationDuration(2);
        rotating.add(new Rotating(display, angle, radius, height, sx, sy, sz, false));
        return display;
    }

    private void applyWheel(double angle) {
        for (Rotating part : rotating) {
            Quaternionf q = yawFor(angle + part.angle);
            Transformation transformation;
            if (part.text) {
                Vector3f translation = new Vector3f(0, (float) part.height, (float) part.radius);
                q.transform(translation);
                Quaternionf rotation = new Quaternionf(q).rotateX((float) -Math.PI / 2).rotateZ((float) Math.PI);
                transformation = new Transformation(translation, rotation, new Vector3f(part.sx), new Quaternionf());
            } else {
                transformation = placed(q, part.radius, part.height, part.sx, part.sy, part.sz);
            }
            part.display.setInterpolationDelay(0);
            part.display.setTransformation(transformation);
        }
    }

    /** A box of size (sx, sy, sz) whose centre sits {@code radius} out along the rotated +Z axis. */
    private static Transformation placed(Quaternionf q, double radius, double height, float sx, float sy, float sz) {
        Vector3f translation = new Vector3f(-sx / 2, (float) height, (float) radius - sz / 2);
        new Quaternionf(q).transform(translation);
        return new Transformation(translation, new Quaternionf(q), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** Rotation about Y that points local +Z at world angle {@code angle} (measured from +X toward +Z). */
    private static Quaternionf yawFor(double angle) {
        return new Quaternionf().rotateY((float) (Math.PI / 2 - angle));
    }

    private void placeBall(double angle, double radiusFactor, double height) {
        double radius = POCKET_RADIUS + 0.2 + (RIM_RADIUS - POCKET_RADIUS) * radiusFactor;
        Location at = ballLocation(angle, radius, height);
        if (ball != null && ball.isValid()) {
            ball.teleport(at);
        }
    }

    private Location ballLocation(double angle, double radius, double height) {
        Location centre = frame.at(0, WHEEL_HEIGHT + 0.1 + height, 0);
        centre.add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
        centre.setYaw(0);
        return centre;
    }

    private String historyLine() {
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder("\n§7Últimos: ");
        for (int index = history.size() - 1; index >= 0; index--) {
            int number = history.get(index);
            line.append(RoulettePockets.legacyColor(number)).append(number).append(' ');
        }
        return line.toString().stripTrailing();
    }

    private void announce(String text) {
        context.broadcastNear(frame.origin(), context.settings().ambientRadius(), "§6[Ruleta] " + text);
    }

    private int bettingTicks() {
        return (settings.cycleSeconds() - settings.spinSeconds() - 5) * 20;
    }

    private String key(UUID player) {
        return "roulette:" + installation.id() + ":" + player;
    }

    private record Rotating(Display display, double angle, double radius, double height, float sx, float sy, float sz,
                            boolean text) {
    }
}
