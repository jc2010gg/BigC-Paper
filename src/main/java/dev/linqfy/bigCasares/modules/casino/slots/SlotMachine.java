package dev.linqfy.bigCasares.modules.casino.slots;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A one-armed bandit: pull the lever and three reels of item displays spin, stop one by one and pay on the
 * middle row. Near misses hold the last reel a little longer. Three coals shock the player (cosmetic only),
 * 7-7-7 wins the shared progressive jackpot and turns the whole casino into a party.
 */
public final class SlotMachine extends Attraction {

    private static final double[] REEL_RIGHT = {0.26, 0.0, -0.26};
    private static final double[] ROW_UP = {1.74, 1.52, 1.30};
    private static final int[] STOP_TICKS = {26, 38, 50};
    private static final int SUSPENSE_TICKS = 26;

    private final CasinoSettings.Slots settings;
    private final SlotEngine engine;
    private final ItemDisplay[][] reels = new ItemDisplay[3][3];
    private final List<BlockDisplay> lamps = new ArrayList<>();
    private final Map<UUID, Integer> betIndex = new HashMap<>();
    private BlockDisplay leverStick;
    private BlockDisplay leverKnob;
    private TextDisplay jackpotLabel;
    private TextDisplay betLabel;
    private SlotSpin spin;
    private UUID player;
    private long bet;
    private int spinTick = -1;
    private int lastStop;
    /** Bet shown on the cabinet: the one last chosen or played here, until somebody picks another. */
    private long shownBet = -1;

    public SlotMachine(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
        this.settings = context.settings().slots();
        this.engine = new SlotEngine(settings.paytable(), settings.nearMissChance(), context.random());
    }

    @Override
    public void spawn() {
        DisplayKit kit = context.kit();
        kit.block(props, frame.at(0, 0, 0), Material.BLACK_CONCRETE.createBlockData(), 1.0f, 1.0f, 0.8f);
        kit.block(props, frame.at(0, 0.98, 0.42), Material.GOLD_BLOCK.createBlockData(), 1.0f, 0.06f, 0.12f);
        kit.block(props, frame.at(0, 1.0, -0.05), Material.RED_CONCRETE.createBlockData(), 1.0f, 1.05f, 0.7f);
        kit.block(props, frame.at(0, 1.18, 0.3), Material.BLACK_CONCRETE.createBlockData(), 0.84f, 0.72f, 0.12f);
        kit.block(props, frame.at(0, 1.47, 0.37), Material.WHITE_CONCRETE.createBlockData(), 0.8f, 0.17f, 0.005f);
        kit.block(props, frame.at(0, 2.05, -0.05), Material.GOLD_BLOCK.createBlockData(), 1.08f, 0.08f, 0.78f);
        kit.block(props, frame.at(0, 2.13, -0.05), Material.BLACK_CONCRETE.createBlockData(), 1.0f, 0.42f, 0.4f);
        for (double side : new double[]{-0.42, 0.42}) {
            BlockDisplay lamp = kit.block(props, frame.at(side, 2.13, 0.2), Material.REDSTONE_LAMP.createBlockData(),
                0.2f, 0.2f, 0.2f);
            lamps.add(lamp);
        }
        for (double side : new double[]{-0.46, 0.46}) {
            BlockDisplay strip = kit.block(props, frame.at(side, 1.12, 0.3), Material.REDSTONE_LAMP.createBlockData(),
                0.06f, 0.8f, 0.06f);
            lamps.add(strip);
        }
        jackpotLabel = kit.text(props, frame.at(0, 2.2, 0.21), CasinoText.legacy(""), 0.42f, false, 0x00000000);
        betLabel = kit.text(props, frame.at(0, 1.03, 0.43), CasinoText.legacy(""), 0.3f, false, 0x00000000);
        betLabel.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(
            (float) -Math.PI / 4, 1, 0, 0)), new Vector3f(0.3f), new Quaternionf()));

        for (int reel = 0; reel < 3; reel++) {
            for (int row = 0; row < 3; row++) {
                Location at = frame.at(REEL_RIGHT[reel], ROW_UP[row], 0.38);
                SlotSymbol symbol = SlotSymbol.values()[context.random().nextInt(SlotSymbol.values().length)];
                reels[reel][row] = kit.item(props, at, CasinoItems.symbol(symbol), 0.2f, DisplayKit.NONE);
            }
        }

        Location pivot = frame.at(-0.58, 1.25, 0.0);
        kit.block(props, frame.at(-0.55, 1.15, 0.0), Material.IRON_BLOCK.createBlockData(), 0.1f, 0.2f, 0.2f);
        leverStick = kit.block(props, pivot, Material.IRON_BLOCK.createBlockData(), 0.05f, 0.6f, 0.05f);
        leverKnob = kit.block(props, pivot, Material.RED_CONCRETE.createBlockData(), 0.14f, 0.14f, 0.14f);
        setLever(0, 0);

        Interaction lever = kit.interaction(props, frame.at(-0.6, 1.1, 0.1), 0.45f, 1.0f);
        context.router().bind(lever, this::pull);
        Interaction front = kit.interaction(props, frame.at(0, 0, 0.25), 0.9f, 2.4f);
        context.router().bind(front, this::cycleBet);
        refreshLabels(null);
    }

    @Override
    protected void onTick(long tick) {
        if (spinTick < 0) {
            if (tick % 40 == 0) {
                refreshLabels(null);
            }
            if (tick % 30 == 0) {
                lamps(tick % 60 == 0);
            }
            return;
        }
        spinTick++;
        int[] stops = stopTicks();
        for (int reel = 0; reel < 3; reel++) {
            if (spinTick < stops[reel]) {
                if (spinTick % 2 == 0) {
                    scroll(reel);
                }
            } else if (spinTick == stops[reel]) {
                land(reel);
            }
        }
        if (spinTick % 4 == 0) {
            lamps(spinTick % 8 == 0);
        }
        if (spinTick % 6 == 0 && spinTick < stops[2]) {
            float pitch = spinTick > stops[1] ? 0.8f + (spinTick - stops[1]) / (float) (stops[2] - stops[1]) : 1.4f;
            context.sound(frame.at(0, 1.5, 0.4), Sound.BLOCK_NOTE_BLOCK_BELL, 0.35f, Math.min(2f, pitch));
        }
        if (spinTick == stops[2] + 8) {
            finish();
        }
    }

    private void cycleBet(Player clicker) {
        if (spinTick >= 0) {
            return;
        }
        int index = (betIndex.getOrDefault(clicker.getUniqueId(), 0) + 1) % settings.bets().size();
        betIndex.put(clicker.getUniqueId(), index);
        context.sound(frame.at(0, 1, 0.4), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.5f);
        refreshLabels(clicker);
        clicker.sendActionBar(Component.text("Apuesta: " + context.chips(settings.bets().get(index))
            + " · tirá de la palanca"));
    }

    private void pull(Player clicker) {
        if (spinTick >= 0) {
            clicker.sendActionBar(Component.text("La máquina está girando..."));
            return;
        }
        long amount = settings.bets().get(betIndex.getOrDefault(clicker.getUniqueId(), 0) % settings.bets().size());
        if (!context.escrow().take(key(clicker.getUniqueId()), clicker.getUniqueId(), amount)) {
            clicker.sendMessage("§cNo te alcanza para " + context.chips(amount) + ". Click en la pantalla para bajar la apuesta.");
            return;
        }
        player = clicker.getUniqueId();
        bet = amount;
        context.jackpot().feed(amount);
        context.saveState().run();
        spin = engine.spin();
        spinTick = 0;
        lastStop = -1;
        setLever(1, 4);
        later(8, () -> setLever(0, 6));
        context.sound(frame.at(-0.6, 1.3, 0), Sound.BLOCK_PISTON_EXTEND, 0.7f, 1.4f);
        context.sound(frame.at(-0.6, 1.3, 0), Sound.BLOCK_CHAIN_PLACE, 0.9f, 0.8f);
        refreshLabels(clicker);
    }

    /** Extra wait on the last reel when the first two already match a premium symbol. */
    private int[] stopTicks() {
        List<SlotSymbol> line = spin.payline();
        boolean tease = line.get(0) == line.get(1)
            && (line.get(0) == SlotSymbol.SEVEN || line.get(0) == SlotSymbol.DIAMOND || spin.nearMiss());
        return new int[]{STOP_TICKS[0], STOP_TICKS[1], STOP_TICKS[2] + (tease ? SUSPENSE_TICKS : 0)};
    }

    private void scroll(int reel) {
        for (int row = 2; row > 0; row--) {
            reels[reel][row].setItemStack(reels[reel][row - 1].getItemStack());
        }
        SlotSymbol symbol = SlotSymbol.values()[context.random().nextInt(SlotSymbol.values().length)];
        reels[reel][0].setItemStack(CasinoItems.symbol(symbol));
    }

    private void land(int reel) {
        List<SlotSymbol> column = spin.window().get(reel);
        for (int row = 0; row < 3; row++) {
            reels[reel][row].setItemStack(CasinoItems.symbol(column.get(row)));
        }
        // A little bounce as the reel locks.
        for (int row = 0; row < 3; row++) {
            ItemDisplay display = reels[reel][row];
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(3);
            display.setTransformation(scaled(0.23f));
            later(3, () -> {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(3);
                display.setTransformation(scaled(0.2f));
            });
        }
        context.sound(frame.at(REEL_RIGHT[reel], 1.5, 0.4), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.2f + reel * 0.2f);
        lastStop = reel;
    }

    private void finish() {
        SlotSpin result = spin;
        UUID winner = player;
        long stake = bet;
        spinTick = -1;
        spin = null;
        player = null;
        Player online = Bukkit.getPlayer(winner);
        switch (result.outcome()) {
            case JACKPOT -> {
                long pool = context.jackpot().claim();
                context.saveState().run();
                context.escrow().settle(key(winner), pool);
                jackpot(online, pool);
            }
            case COAL -> {
                context.escrow().settle(key(winner), 0);
                shock(online);
            }
            case LOSE -> {
                context.escrow().settle(key(winner), 0);
                if (online != null) {
                    online.sendActionBar(Component.text(result.nearMiss() ? "¡Uff! Casi..." : "Nada esta vez."));
                    context.sound(frame.at(0, 1.5, 0.4), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                }
            }
            default -> {
                long payout = stake * engine.paytable().multiplier(result.outcome());
                context.escrow().settle(key(winner), payout);
                if (online != null) {
                    online.sendMessage("§6[Tragamonedas] §a¡" + result.outcome().symbol().label() + " x3§a! Cobrás "
                        + context.chips(payout) + ".");
                    online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
                }
                coins(payout >= stake * 10 ? 40 : 12);
            }
        }
        refreshLabels(online);
    }

    private void shock(Player victim) {
        Location front = frame.at(0, 1.5, 0.6);
        front.getWorld().strikeLightningEffect(frame.at(0, 2.2, 0));
        front.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, front, 60, 0.4, 0.5, 0.4, 0.2);
        context.sound(front, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 2.0f);
        if (victim != null) {
            victim.playHurtAnimation(0);
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
            victim.setVelocity(frame.forwardVector().multiply(0.6).setY(0.35));
            victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.1);
            victim.sendMessage("§8[Tragamonedas] §7Tres carbones... §e⚡ §7la máquina te dio una patada y se quedó con todo.");
        }
    }

    private void jackpot(Player winner, long pool) {
        String name = winner == null ? "Alguien" : winner.getName();
        Bukkit.broadcast(CasinoText.legacy("§6§l★ JACKPOT 777 ★ §e" + name + " §fse llevó §6§l" + context.chips(pool)
            + " §fen el Gran Casares."));
        double radius = context.settings().ambientRadius();
        for (Player near : context.playersNear(frame.origin(), radius)) {
            near.showTitle(Title.title(CasinoText.legacy("§6§l7 7 7"), CasinoText.legacy("§e¡JACKPOT de " + name + "!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(800))));
        }
        for (int beat = 0; beat < 16; beat++) {
            final boolean high = beat % 2 == 0;
            later(beat * 6, () -> {
                for (Player near : context.playersNear(frame.origin(), radius)) {
                    near.playSound(near.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f,
                        high ? 1.8f : 1.2f);
                }
                lamps(high);
            });
        }
        later(1, () -> context.playersNear(frame.origin(), radius).forEach(near ->
            near.playSound(near.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.MASTER, 1f, 1f)));
        for (int burst = 0; burst < 12; burst++) {
            later(10 + burst * 7, this::ceilingFirework);
        }
        for (int wave = 0; wave < 30; wave++) {
            later(wave * 3, () -> coins(6));
        }
        if (winner != null) {
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private void ceilingFirework() {
        Location at = frame.at((context.random().nextDouble() - 0.5) * 16, 6.5, (context.random().nextDouble() - 0.5) * 16);
        at.getWorld().spawn(at, Firework.class, firework -> {
            context.kit().tag(firework);
            firework.setPersistent(false);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                .with(context.random().nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                .withColor(Color.YELLOW, Color.ORANGE, Color.RED).withFade(Color.WHITE).flicker(true).trail(true).build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.setVelocity(new Vector(0, -0.05, 0));
        }).detonate();
    }

    /** Gold nuggets raining from the ceiling. Nobody can pick them up and they vanish on landing. */
    private void coins(int count) {
        for (int index = 0; index < count; index++) {
            Location at = frame.at((context.random().nextDouble() - 0.5) * 10, 6, (context.random().nextDouble() - 0.5) * 10 + 2);
            Item nugget = at.getWorld().dropItem(at, new ItemStack(Material.GOLD_NUGGET), item -> {
                context.kit().tag(item);
                item.setPersistent(false);
                item.setCanPlayerPickup(false);
                item.setCanMobPickup(false);
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setVelocity(new Vector(0, -0.2, 0));
            });
            later(70 + context.random().nextInt(30), () -> {
                if (nugget.isValid()) {
                    nugget.getWorld().spawnParticle(Particle.WAX_ON, nugget.getLocation(), 2, 0.1, 0.1, 0.1, 0);
                    nugget.remove();
                }
            });
        }
    }

    private void lamps(boolean on) {
        for (int index = 0; index < lamps.size(); index++) {
            BlockDisplay lamp = lamps.get(index);
            if (lamp.getBlock() instanceof Lightable data) {
                data.setLit(index % 2 == 0 == on);
                lamp.setBlock(data);
            }
        }
    }

    /** 0 is up, 1 pulled all the way down. */
    private void setLever(double progress, int ticks) {
        Quaternionf q = new Quaternionf().rotateX((float) (progress * Math.toRadians(75)));
        Vector3f stickOffset = new Vector3f(-0.025f, 0, -0.025f);
        q.transform(stickOffset);
        Vector3f knobOffset = new Vector3f(-0.07f, 0.6f, -0.07f);
        q.transform(knobOffset);
        for (BlockDisplay part : new BlockDisplay[]{leverStick, leverKnob}) {
            part.setInterpolationDelay(0);
            part.setInterpolationDuration(ticks);
        }
        leverStick.setTransformation(new Transformation(stickOffset, new Quaternionf(q), new Vector3f(0.05f, 0.6f, 0.05f),
            new Quaternionf()));
        leverKnob.setTransformation(new Transformation(knobOffset, new Quaternionf(q), new Vector3f(0.14f), new Quaternionf()));
    }

    private void refreshLabels(Player viewer) {
        jackpotLabel.text(CasinoText.legacy("§6§l★ JACKPOT ★\n§e" + context.chips(context.jackpot().value())));
        if (viewer != null) {
            shownBet = settings.bets().get(betIndex.getOrDefault(viewer.getUniqueId(), 0) % settings.bets().size());
        }
        long shown = shownBet < 0 ? settings.bets().getFirst() : shownBet;
        betLabel.text(CasinoText.legacy("§fApuesta §e" + context.chips(shown)));
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    @Override
    protected void onDespawn() {
        context.escrow().refundPrefix("slot:" + installation.id() + ":");
        spinTick = -1;
    }

    private String key(UUID player) {
        return "slot:" + installation.id() + ":" + player;
    }
}
