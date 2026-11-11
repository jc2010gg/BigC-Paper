package dev.linqfy.bigCasares.modules.casino.table;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.EntityGroup;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * A green-felt table with four physical seats and a mannequin dealer. Players sit by right-clicking a stool;
 * sneaking stands them up. Cards fly from the shoe as item displays; each seat has floating buttons that
 * only its occupant can see.
 */
public abstract class CardTable extends Attraction {

    public static final int SEATS = 4;
    protected static final double TABLE_HEIGHT = 0.95;
    private static final double[] SEAT_RIGHT = {-1.65, -0.55, 0.55, 1.65};
    private static final double[] SEAT_FORWARD = {1.45, 1.75, 1.75, 1.45};
    /** Bet spot, between the cards and the rail so chips never cover the cards. */
    private static final double CHIP_FORWARD = 0.9;
    protected static final float CARD_SCALE = 0.42f;
    protected static final float BIG_CARD_SCALE = 0.55f;

    protected final Seat[] seats = new Seat[SEATS];
    protected final EntityGroup dealerCards = new EntityGroup();
    protected Mannequin dealer;
    protected TextDisplay status;

    protected CardTable(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
    }

    protected abstract String dealerName();

    protected abstract String tableTitle();

    /** A player sat down. Return false to refuse (the seat is then released). */
    protected abstract boolean onSit(int seat, Player player);

    /** A player stood up or disconnected. */
    protected abstract void onLeave(int seat, UUID player);

    @Override
    public void spawn() {
        DisplayKit kit = context.kit();
        // Table body: dark wood base, gold rail, green felt.
        kit.block(props, frame.at(0, 0, 0), Material.DARK_OAK_PLANKS.createBlockData(), 1.2f, 0.75f, 0.9f);
        kit.block(props, frame.at(0, 0.75, 0), Material.DARK_OAK_PLANKS.createBlockData(), 4.6f, 0.12f, 2.6f);
        kit.block(props, frame.at(0, 0.87, 0), Material.GOLD_BLOCK.createBlockData(), 4.7f, 0.04f, 2.7f);
        kit.block(props, frame.at(0, 0.88, 0), Material.GREEN_WOOL.createBlockData(), 4.4f, 0.06f, 2.4f);
        kit.block(props, frame.at(0, 0.885, 1.2), Material.DARK_OAK_PLANKS.createBlockData(), 4.6f, 0.1f, 0.25f);
        // Felt markings: a betting arc and the shoe.
        for (int seat = 0; seat < SEATS; seat++) {
            kit.block(props, frame.at(SEAT_RIGHT[seat], 0.941, CHIP_FORWARD), Material.YELLOW_CONCRETE.createBlockData(),
                0.34f, 0.005f, 0.3f);
            kit.block(props, frame.at(SEAT_RIGHT[seat], 0.942, CHIP_FORWARD), Material.GREEN_WOOL.createBlockData(),
                0.28f, 0.005f, 0.24f);
        }
        kit.block(props, frame.at(1.75, 0.94, -0.7), Material.BLACK_CONCRETE.createBlockData(), 0.35f, 0.22f, 0.5f);
        kit.item(props, frame.at(1.75, 1.17, -0.7), CasinoItems.cardBack(), 0.3f, DisplayKit.FLAT);
        kit.text(props, frame.at(0, 0.95, -0.15, 0), CasinoText.legacy(tableTitle()), 0.55f, false, 0x00000000)
            .setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(
                (float) -Math.PI / 2, 1, 0, 0)), new Vector3f(0.55f), new Quaternionf()));

        dealer = kit.npc(props, frame.at(0, 0, -1.55, 0), dealerName(), "§7Crupier de la casa", "");
        dealer.getEquipment().setChestplate(dyed(Material.LEATHER_CHESTPLATE, org.bukkit.Color.fromRGB(0x111111)));
        dealer.getEquipment().setLeggings(dyed(Material.LEATHER_LEGGINGS, org.bukkit.Color.fromRGB(0x111111)));
        context.router().bind(dealer, this::onDealerClick);

        status = kit.text(props, frame.at(0, 2.75, -1.55), CasinoText.legacy("§7Sentate para jugar"), 0.9f,
            true, 0x66000000);

        for (int index = 0; index < SEATS; index++) {
            Location sit = frame.at(SEAT_RIGHT[index], 0.55, SEAT_FORWARD[index], 180);
            kit.block(props, frame.at(SEAT_RIGHT[index], 0, SEAT_FORWARD[index]),
                Material.DARK_OAK_FENCE.createBlockData(), 0.3f, 0.45f, 0.3f);
            kit.block(props, frame.at(SEAT_RIGHT[index], 0.45, SEAT_FORWARD[index]),
                Material.RED_WOOL.createBlockData(), 0.6f, 0.12f, 0.6f);
            ArmorStand stand = kit.seat(props, sit);
            Interaction box = kit.interaction(props, frame.at(SEAT_RIGHT[index], 0, SEAT_FORWARD[index]), 0.8f, 1.0f);
            final int seat = index;
            context.router().bind(box, player -> sit(seat, player));
            context.router().bind(stand, player -> sit(seat, player));
            // Above the seated player's head: everybody else reads it, the player gets the same line in the action bar.
            TextDisplay label = kit.text(props, frame.at(SEAT_RIGHT[index], 2.45, SEAT_FORWARD[index]),
                CasinoText.legacy(""), 0.5f, true, 0x66000000);
            seats[index] = new Seat(index, stand, label);
        }
    }

    protected void onDealerClick(Player player) {
        player.sendMessage("§2[" + dealerName() + "§2] §7Sentate en una silla libre para jugar. Agachate para levantarte.");
    }

    protected Seat seatOf(UUID player) {
        for (Seat seat : seats) {
            if (player.equals(seat.occupant)) {
                return seat;
            }
        }
        return null;
    }

    protected Player occupant(int seat) {
        UUID id = seats[seat].occupant;
        return id == null ? null : Bukkit.getPlayer(id);
    }

    protected void sit(int index, Player player) {
        Seat seat = seats[index];
        if (seat.occupant != null) {
            if (!seat.occupant.equals(player.getUniqueId())) {
                player.sendMessage("§cEsa silla está ocupada.");
            }
            return;
        }
        if (player.isInsideVehicle()) {
            player.sendMessage("§cPrimero bajate de donde estás.");
            return;
        }
        if (!seat.stand.addPassenger(player)) {
            player.sendMessage("§cNo pudiste sentarte, probá de nuevo.");
            return;
        }
        seat.occupant = player.getUniqueId();
        if (!onSit(index, player)) {
            seat.occupant = null;
            seat.stand.removePassenger(player);
            return;
        }
        context.sound(seat.stand.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.8f, 0.8f);
    }

    protected void stand(int index) {
        Seat seat = seats[index];
        UUID occupant = seat.occupant;
        if (occupant == null) {
            return;
        }
        seat.occupant = null;
        clearButtons(index);
        Player player = Bukkit.getPlayer(occupant);
        if (player != null && seat.stand.getPassengers().contains(player)) {
            seat.stand.removePassenger(player);
        }
        onLeave(index, occupant);
        seat.label.text(CasinoText.legacy(""));
    }

    @Override
    protected final void onTick(long tick) {
        if (tick % 20 == 0) {
            releaseGhostSeats();
        }
        tableTick(tick);
    }

    protected void tableTick(long tick) {
    }

    /**
     * Frees seats whose player is gone or no longer riding the stool (a teleport, a plugin or a lag spike can
     * dismount without the usual event), so nobody keeps playing from across the casino.
     */
    private void releaseGhostSeats() {
        for (Seat seat : seats) {
            if (seat == null || seat.occupant == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(seat.occupant);
            if (player == null || !seat.stand.getPassengers().contains(player)) {
                stand(seat.index);
            }
        }
    }

    @Override
    public boolean onDismount(Player player, Entity vehicle) {
        for (Seat seat : seats) {
            if (seat.stand.equals(vehicle) && player.getUniqueId().equals(seat.occupant)) {
                stand(seat.index);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onQuit(Player player) {
        Seat seat = seatOf(player.getUniqueId());
        if (seat != null) {
            stand(seat.index);
        }
    }

    @Override
    protected void onDespawn() {
        for (Seat seat : seats) {
            if (seat != null) {
                clearButtons(seat.index);
                seat.cards.removeAll();
                seat.privateCards.removeAll();
                seat.chips.removeAll();
            }
        }
        dealerCards.removeAll();
    }

    /** Replaces the seat's floating buttons. They sit in a row over the table edge in front of the seat. */
    protected void buttons(int index, List<TableButton> buttons) {
        clearButtons(index);
        Player player = occupant(index);
        if (player == null || buttons.isEmpty()) {
            return;
        }
        Seat seat = seats[index];
        double spacing = 0.74;
        double start = -(buttons.size() - 1) * spacing / 2;
        for (int position = 0; position < buttons.size(); position++) {
            TableButton button = buttons.get(position);
            // Buttons are read by the seated player, who faces the dealer: their right is the frame's left.
            Location at = frame.at(SEAT_RIGHT[index] - (start + position * spacing), 1.08, 1.12);
            TextDisplay label = context.kit().text(seat.buttons, at, CasinoText.legacy(button.label()), 0.32f, false,
                button.backgroundArgb());
            label.setVisibleByDefault(false);
            context.kit().showOnlyTo(label, player);
            Interaction hit = context.kit().interaction(seat.buttons, at.clone().subtract(0, 0.05, 0), 0.66f, 0.22f);
            hit.setVisibleByDefault(false);
            context.kit().showOnlyTo(hit, player);
            context.router().bind(hit, clicker -> {
                if (clicker.getUniqueId().equals(seat.occupant)) {
                    context.sound(at, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                    button.action().accept(clicker);
                }
            });
        }
    }

    protected void clearButtons(int index) {
        Seat seat = seats[index];
        context.router().unbindAll(seat.buttons);
        seat.buttons.removeAll();
    }

    /** Where the {@code n}th card of a seat lies on the felt. */
    protected Location seatCardSpot(int index, int n) {
        return frame.at(SEAT_RIGHT[index] - 0.15 + n * 0.19, TABLE_HEIGHT + 0.01 + n * 0.002, 0.4 - n * 0.03);
    }

    protected Location dealerCardSpot(int n) {
        return frame.at(-0.55 + n * 0.36, TABLE_HEIGHT + 0.01 + n * 0.002, -0.3);
    }

    protected Location shoe() {
        return frame.at(1.75, 1.2, -0.7);
    }

    /**
     * Sends a card from the shoe to {@code target}. {@code viewer} non-null makes it a private card that only
     * that player sees (others get a separate face-down copy from the caller).
     */
    protected ItemDisplay flyCard(EntityGroup group, Location target, Card card, Player viewer, boolean hideFromViewer) {
        return flyCard(group, target, card, viewer, hideFromViewer, group == dealerCards ? BIG_CARD_SCALE : CARD_SCALE);
    }

    protected ItemDisplay flyCard(EntityGroup group, Location target, Card card, Player viewer, boolean hideFromViewer,
                                  float scale) {
        Location from = shoe();
        from.setYaw(frame.yaw());
        ItemDisplay display = context.kit().item(group, from, card == null ? CasinoItems.cardBack()
            : CasinoItems.card(card), scale, DisplayKit.FLAT);
        display.setTeleportDuration(6);
        if (viewer != null) {
            if (hideFromViewer) {
                context.kit().hideFrom(display, viewer);
            } else {
                display.setVisibleByDefault(false);
                context.kit().showOnlyTo(display, viewer);
            }
        }
        Location destination = target.clone();
        destination.setYaw(frame.yaw());
        later(1, () -> {
            if (display.isValid()) {
                display.teleport(destination);
            }
        });
        context.sound(from, Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.3f + context.random().nextFloat() * 0.3f);
        return display;
    }

    /** Turns a card over in place: it flips edge-on, swaps faces and lands face up. */
    protected void flip(ItemDisplay display, Card card) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(4);
        Transformation current = display.getTransformation();
        Quaternionf edge = new Quaternionf(DisplayKit.FLAT).rotateLocalZ((float) Math.PI / 2);
        display.setTransformation(new Transformation(current.getTranslation(), edge, current.getScale(),
            current.getRightRotation()));
        later(4, () -> {
            if (!display.isValid()) {
                return;
            }
            display.setItemStack(CasinoItems.card(card));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(4);
            display.setTransformation(new Transformation(current.getTranslation(), new Quaternionf(DisplayKit.FLAT),
                current.getScale(), current.getRightRotation()));
        });
        context.sound(display.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
    }

    /** Chip stack in front of a seat for its current bet. */
    protected void showChips(int index, long amount) {
        Seat seat = seats[index];
        seat.chips.removeAll();
        if (amount <= 0) {
            return;
        }
        int stack = (int) Math.min(6, Math.max(1, Math.round(Math.log10(amount))));
        for (int level = 0; level < stack; level++) {
            Location at = frame.at(SEAT_RIGHT[index], TABLE_HEIGHT + 0.012 + level * 0.025, CHIP_FORWARD);
            context.kit().item(seat.chips, at, CasinoItems.chip(amount), 0.32f, DisplayKit.FLAT);
        }
    }

    protected void setStatus(String text) {
        if (status != null && status.isValid()) {
            status.text(CasinoText.legacy(text));
        }
    }

    protected void setSeatLabel(int index, String text) {
        TextDisplay label = seats[index].label;
        if (label.isValid()) {
            label.text(CasinoText.legacy(text));
        }
        Player player = occupant(index);
        if (player != null && !text.isEmpty()) {
            // The label floats over the player's own head, so they get it in the action bar instead.
            int newline = text.indexOf('\n');
            String own = newline < 0 ? text : text.substring(newline + 1).replace('\n', ' ');
            player.sendActionBar(CasinoText.legacy(own));
        }
    }

    protected void message(int index, String text) {
        Player player = occupant(index);
        if (player != null) {
            player.sendMessage(text);
        }
    }

    protected void tableMessage(String text) {
        context.broadcastNear(frame.origin(), 12, "§2[" + dealerName() + "§2] §f" + text);
    }

    private static org.bukkit.inventory.ItemStack dyed(Material material, org.bukkit.Color color) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    protected static final class Seat {
        final int index;
        final ArmorStand stand;
        final TextDisplay label;
        final EntityGroup buttons = new EntityGroup();
        public final EntityGroup cards = new EntityGroup();
        public final EntityGroup privateCards = new EntityGroup();
        final EntityGroup chips = new EntityGroup();
        UUID occupant;

        Seat(int index, ArmorStand stand, TextDisplay label) {
            this.index = index;
            this.stand = stand;
            this.label = label;
        }

        public UUID occupant() {
            return occupant;
        }
    }
}
