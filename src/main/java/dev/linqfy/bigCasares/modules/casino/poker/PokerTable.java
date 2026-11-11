package dev.linqfy.bigCasares.modules.casino.poker;

import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.Shoe;
import dev.linqfy.bigCasares.modules.casino.table.CardTable;
import dev.linqfy.bigCasares.modules.casino.table.TableButton;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.EntityGroup;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Fixed-limit Texas Hold'em between seated players; the house only deals. Sitting down buys in for a fixed
 * stack that is held in escrow and cashed out on standing up. Hole cards are private displays: the owner sees
 * them face up, everyone else sees their backs.
 */
public final class PokerTable extends CardTable {

    private final CasinoSettings.Poker settings;
    private final Shoe shoe;
    private final EntityGroup board = new EntityGroup();
    private final EntityGroup pot = new EntityGroup();
    private final Map<Integer, UUID> handPlayers = new HashMap<>();
    private final Map<Integer, String> lastAction = new HashMap<>();
    private final Set<Integer> leaving = new java.util.HashSet<>();
    private HoldemHand hand;
    private int button;
    private int countdown = -1;
    private int turnTicks;
    /** Seat whose buttons are showing; the turn clock only runs for it, never while cards are flying. */
    private int promptedSeat = -1;
    private boolean settling;
    private int shownBoard;
    private TextDisplay potLabel;

    public PokerTable(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
        this.settings = context.settings().poker();
        this.shoe = new Shoe(1, 1.0, context.random());
    }

    @Override
    protected String dealerName() {
        return "§9§lCrupier §f· §bPóker";
    }

    @Override
    protected String tableTitle() {
        return "§b§lTEXAS HOLD'EM\n§7Límite fijo · ciegas " + settings.smallBlind() + "/" + settings.bigBlind();
    }

    @Override
    public void spawn() {
        super.spawn();
        potLabel = context.kit().text(props, frame.at(0, 1.35, -0.05), CasinoText.legacy(""), 0.6f, true, 0x00000000);
    }

    @Override
    protected boolean onSit(int seat, Player player) {
        if (leaving.contains(seat)) {
            player.sendMessage("§cEsa silla se libera cuando termine la mano.");
            return false;
        }
        String key = key(seat, player.getUniqueId());
        if (!context.escrow().take(key, player.getUniqueId(), settings.buyIn())) {
            player.sendMessage("§cPara sentarte necesitás " + context.chips(settings.buyIn()) + ".");
            return false;
        }
        player.sendMessage("§9[Póker] §7Compraste " + context.chips(settings.buyIn())
            + " §7en fichas. Al levantarte (agachate) te las cambiamos.");
        lastAction.remove(seat);
        later(2, () -> refreshLabel(seat));
        maybeScheduleHand();
        return true;
    }

    @Override
    protected void onLeave(int seat, UUID player) {
        if (hand != null && player.equals(handPlayers.get(seat))) {
            // Chips already in the pot stay there: the stack is cashed out once the hand is over.
            leaving.add(seat);
            if (!hand.finished() && !hand.folded(seat)) {
                if (hand.actingSeat().orElse(-2) == seat) {
                    act(seat, PokerAction.FOLD);
                } else {
                    forceFoldOutOfTurn(seat);
                }
            }
            return;
        }
        cashOut(seat, player);
    }

    @Override
    protected void tableTick(long tick) {
        if (countdown > 0) {
            if (countdown % 20 == 0) {
                setStatus("§b§lNueva mano en §f" + countdown / 20 + "s");
            }
            if (--countdown == 0) {
                countdown = -1;
                startHand();
            }
        }
        if (hand != null && !hand.finished()) {
            OptionalInt acting = hand.actingSeat();
            if (acting.isPresent() && acting.getAsInt() == promptedSeat) {
                if (turnTicks % 20 == 0) {
                    Player player = occupant(acting.getAsInt());
                    setStatus("§fHabla §e" + (player == null ? "?" : player.getName()) + " §7(" + turnTicks / 20 + "s)");
                }
                if (--turnTicks <= 0) {
                    int seat = acting.getAsInt();
                    act(seat, hand.legalActions(seat).contains(PokerAction.CHECK) ? PokerAction.CHECK : PokerAction.FOLD);
                }
            }
        }
    }

    @Override
    protected void onDespawn() {
        context.escrow().refundPrefix("poker:" + installation.id() + ":");
        board.removeAll();
        pot.removeAll();
        super.onDespawn();
    }

    private void maybeScheduleHand() {
        if (hand != null || countdown > 0) {
            return;
        }
        if (playersWithChips().size() >= 2) {
            countdown = 8 * 20;
        } else {
            setStatus("§7Esperando jugadores (mínimo 2)");
        }
    }

    private List<Integer> playersWithChips() {
        List<Integer> ready = new ArrayList<>();
        for (int seat = 0; seat < SEATS; seat++) {
            Player player = occupant(seat);
            if (player != null && context.escrow().held(key(seat, player.getUniqueId())) > 0) {
                ready.add(seat);
            }
        }
        return ready;
    }

    private void startHand() {
        List<Integer> ready = playersWithChips();
        if (ready.size() < 2) {
            setStatus("§7Esperando jugadores (mínimo 2)");
            return;
        }
        shoe.shuffle();
        button = nextSeat(ready, button);
        List<HoldemHand.Participant> participants = new ArrayList<>();
        handPlayers.clear();
        lastAction.clear();
        for (int seat : ready) {
            UUID player = occupant(seat).getUniqueId();
            participants.add(new HoldemHand.Participant(seat, context.escrow().held(key(seat, player))));
            handPlayers.put(seat, player);
        }
        hand = new HoldemHand(participants, ready.indexOf(button), settings.smallBlind(), settings.bigBlind(), shoe);
        hand.start();
        settling = false;
        promptedSeat = -1;
        shownBoard = 0;
        tableMessage("Nueva mano. Botón: " + nameOf(button) + ".");
        int delay = 3;
        for (int pass = 0; pass < 2; pass++) {
            for (int seat : ready) {
                final int n = pass;
                later(delay, () -> dealHole(seat, n));
                delay += 5;
            }
        }
        later(delay + 4, this::afterAction);
    }

    private void dealHole(int seat, int n) {
        Player owner = occupant(seat);
        Card card = hand.holeCards(seat).get(n);
        Location spot = seatCardSpot(seat, n);
        if (owner != null) {
            flyCard(seats[seat].privateCards, spot, card, owner, false);
            flyCard(seats[seat].cards, spot, null, owner, true);
        } else {
            flyCard(seats[seat].cards, spot, null, null, false);
        }
    }

    private void act(int seat, PokerAction action) {
        if (hand == null || hand.finished() || !hand.legalActions(seat).contains(action)) {
            return;
        }
        long owed = hand.toCall(seat);
        promptedSeat = -1;
        hand.act(seat, action);
        clearButtons(seat);
        String verb = switch (action) {
            case FOLD -> "§cSe retira";
            case CHECK -> "§7Pasa";
            case CALL -> "§eIguala " + owed;
            case RAISE -> "§6Sube a " + hand.committedThisStreet(seat);
        };
        lastAction.put(seat, verb);
        if (action == PokerAction.FOLD) {
            seats[seat].cards.removeAll();
            seats[seat].privateCards.removeAll();
        }
        context.sound(frame.origin(), action == PokerAction.RAISE ? Sound.BLOCK_AMETHYST_BLOCK_HIT
            : Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.7f, 1.2f);
        refreshLabel(seat);
        later(6, this::afterAction);
    }

    /** A player who walks away out of turn is folded the moment their turn comes. */
    private void forceFoldOutOfTurn(int seat) {
        lastAction.put(seat, "§cSe fue");
        seats[seat].cards.removeAll();
        seats[seat].privateCards.removeAll();
    }

    private void afterAction() {
        if (hand == null) {
            return;
        }
        revealBoard();
        updatePot();
        if (hand.finished()) {
            finishHand();
            return;
        }
        int seat = hand.actingSeat().orElse(-1);
        if (seat < 0) {
            return;
        }
        if (leaving.contains(seat) || occupant(seat) == null) {
            act(seat, PokerAction.FOLD);
            return;
        }
        turnTicks = settings.turnSeconds() * 20;
        promptedSeat = seat;
        Set<PokerAction> legal = hand.legalActions(seat);
        List<TableButton> actions = new ArrayList<>();
        if (legal.contains(PokerAction.CHECK)) {
            actions.add(new TableButton("§f§lPASAR", TableButton.GREEN, player -> act(seat, PokerAction.CHECK)));
        } else {
            actions.add(new TableButton("§f§lIGUALAR §e" + hand.toCall(seat), TableButton.GREEN,
                player -> act(seat, PokerAction.CALL)));
        }
        if (legal.contains(PokerAction.RAISE)) {
            actions.add(new TableButton("§f§lSUBIR §e+" + hand.betSize(), TableButton.GOLD,
                player -> act(seat, PokerAction.RAISE)));
        }
        actions.add(new TableButton("§f§lRETIRARSE", TableButton.RED, player -> act(seat, PokerAction.FOLD)));
        buttons(seat, actions);
        Player player = occupant(seat);
        if (player != null) {
            List<Card> cards = hand.holeCards(seat);
            HandValue value = hand.value(seat);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Tus cartas: "
                + cards.get(0).rank().label() + cards.get(0).suit().symbol() + " "
                + cards.get(1).rank().label() + cards.get(1).suit().symbol()
                + (value == null ? "" : " · " + value.label())));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.5f);
        }
    }

    private void revealBoard() {
        List<Card> cards = hand.board();
        int delay = 1;
        while (shownBoard < cards.size()) {
            final int n = shownBoard++;
            Card card = cards.get(n);
            later(delay, () -> flyCard(board, frame.at(-1.0 + n * 0.5, TABLE_HEIGHT + 0.01, -0.15), card, null, false,
                BIG_CARD_SCALE));
            delay += 6;
        }
    }

    private void updatePot() {
        long amount = hand == null ? 0 : hand.pot();
        if (potLabel != null && potLabel.isValid()) {
            potLabel.text(CasinoText.legacy(amount == 0 ? "" : "§6Pozo: §e" + context.chips(amount)));
        }
        pot.removeAll();
        if (amount > 0) {
            int stack = (int) Math.min(6, Math.max(1, Math.round(Math.log10(amount))));
            for (int level = 0; level < stack; level++) {
                context.kit().item(pot, frame.at(0, TABLE_HEIGHT + 0.012 + level * 0.025, 0.25),
                    CasinoItems.chip(amount), 0.36f, DisplayKit.FLAT);
            }
        }
        for (int seat : handPlayers.keySet()) {
            refreshLabel(seat);
        }
    }

    private void finishHand() {
        if (settling) {
            return;
        }
        settling = true;
        promptedSeat = -1;
        HoldemHand finished = hand;
        for (int seat = 0; seat < SEATS; seat++) {
            clearButtons(seat);
        }
        boolean showdown = finished.street() == Street.SHOWDOWN;
        if (showdown) {
            for (int seat : finished.seats()) {
                if (finished.folded(seat)) {
                    continue;
                }
                seats[seat].cards.removeAll();
                seats[seat].privateCards.removeAll();
                List<Card> hole = finished.holeCards(seat);
                for (int n = 0; n < hole.size(); n++) {
                    flyCard(seats[seat].cards, seatCardSpot(seat, n), hole.get(n), null, false);
                }
            }
        }
        for (HoldemHand.Payout payout : finished.payouts()) {
            String how = payout.value() == null ? "sin mostrar" : "con " + payout.value().label();
            tableMessage("§e" + nameOf(payout.seat()) + " §fse lleva §6" + context.chips(payout.amount()) + " §f" + how + ".");
            lastAction.put(payout.seat(), "§a§lGANA " + payout.amount());
            Player winner = occupant(payout.seat());
            if (winner != null) {
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
        }
        for (Map.Entry<Integer, UUID> entry : handPlayers.entrySet()) {
            int seat = entry.getKey();
            context.escrow().adjust(key(seat, entry.getValue()), entry.getValue(), finished.stack(seat));
            refreshLabel(seat);
        }
        later(showdown ? 100 : 50, () -> {
            board.removeAll();
            pot.removeAll();
            for (int seat = 0; seat < SEATS; seat++) {
                seats[seat].cards.removeAll();
                seats[seat].privateCards.removeAll();
            }
            for (int seat : List.copyOf(leaving)) {
                UUID player = handPlayers.get(seat);
                if (player != null) {
                    cashOut(seat, player);
                }
            }
            leaving.clear();
            for (int seat = 0; seat < SEATS; seat++) {
                Player player = occupant(seat);
                if (player != null && context.escrow().held(key(seat, player.getUniqueId())) == 0) {
                    player.sendMessage("§9[Póker] §cTe quedaste sin fichas.");
                    buttons(seat, List.of(new TableButton("§f§lRECOMPRAR §e" + settings.buyIn(), TableButton.GOLD,
                        clicker -> rebuy(clicker))));
                }
            }
            hand = null;
            handPlayers.clear();
            lastAction.clear();
            updatePot();
            maybeScheduleHand();
        });
    }

    private void rebuy(Player player) {
        Seat seat = seatOf(player.getUniqueId());
        if (seat == null || hand != null && handPlayers.containsValue(player.getUniqueId())) {
            return;
        }
        int index = java.util.Arrays.asList(seats).indexOf(seat);
        if (!context.escrow().take(key(index, player.getUniqueId()), player.getUniqueId(), settings.buyIn())) {
            player.sendMessage("§cNo te alcanza para recomprar.");
            return;
        }
        clearButtons(index);
        refreshLabel(index);
        maybeScheduleHand();
    }

    private void cashOut(int seat, UUID player) {
        long refunded = context.escrow().refund(key(seat, player));
        Player online = Bukkit.getPlayer(player);
        if (online != null && refunded > 0) {
            online.sendMessage("§9[Póker] §7Te levantás con " + context.chips(refunded) + ".");
        }
        lastAction.remove(seat);
        setSeatLabel(seat, "");
    }

    private void refreshLabel(int seat) {
        Player player = occupant(seat);
        if (player == null) {
            setSeatLabel(seat, "");
            return;
        }
        long stack = hand != null && handPlayers.containsKey(seat)
            ? hand.stack(seat) : context.escrow().held(key(seat, player.getUniqueId()));
        String action = lastAction.getOrDefault(seat, "");
        String dealer = seat == button && hand != null ? " §f§l(D)" : "";
        setSeatLabel(seat, "§f" + player.getName() + dealer + "\n§e" + context.chips(stack)
            + (action.isEmpty() ? "" : "\n" + action));
    }

    private int nextSeat(List<Integer> ready, int current) {
        for (int seat : ready) {
            if (seat > current) {
                return seat;
            }
        }
        return ready.getFirst();
    }

    private String nameOf(int seat) {
        UUID id = handPlayers.get(seat);
        if (id == null) {
            Player player = occupant(seat);
            return player == null ? "?" : player.getName();
        }
        var offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? "?" : offline.getName();
    }

    private String key(int seat, UUID player) {
        return "poker:" + installation.id() + ":" + seat + ":" + player;
    }
}
