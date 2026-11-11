package dev.linqfy.bigCasares.modules.casino.blackjack;

import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.Shoe;
import dev.linqfy.bigCasares.modules.casino.table.CardTable;
import dev.linqfy.bigCasares.modules.casino.table.ChipMenu;
import dev.linqfy.bigCasares.modules.casino.table.TableButton;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

public final class BlackjackTable extends CardTable {

    private enum Phase {
        IDLE,
        BETTING,
        DEALING,
        PLAYERS,
        DEALER,
        SETTLING
    }

    private final CasinoSettings.Blackjack settings;
    private final Shoe shoe;
    private final Map<Integer, UUID> roundPlayers = new HashMap<>();
    private Phase phase = Phase.IDLE;
    private BlackjackRound round;
    private ItemDisplay holeCard;
    private int countdown;
    private int turnTicks;

    public BlackjackTable(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
        this.settings = context.settings().blackjack();
        this.shoe = new Shoe(settings.decks(), 0.75, context.random());
    }

    @Override
    protected String dealerName() {
        return "§2§lCrupier §f· §aBlackjack";
    }

    @Override
    protected String tableTitle() {
        return "§6§lBLACKJACK PAGA 3 A 2\n§eEl crupier se planta en 17";
    }

    @Override
    protected boolean onSit(int seat, Player player) {
        player.sendMessage("§2[Blackjack] §7Apuesta mínima " + context.chips(settings.minBet()) + "§7, máxima "
            + context.chips(settings.maxBet()) + "§7. Agachate para levantarte.");
        setSeatLabel(seat, "§f" + player.getName());
        later(2, () -> refreshButtons(seat));
        return true;
    }

    @Override
    protected void onLeave(int seat, UUID player) {
        if (phase == Phase.IDLE || phase == Phase.BETTING) {
            context.escrow().refund(key(seat, player));
            showChips(seat, 0);
            return;
        }
        if (round != null && player.equals(roundPlayers.get(seat))
            && round.activeSeat().orElse(-2) == seat) {
            round.stand(seat);
            later(4, this::promptActive);
        }
    }

    @Override
    protected void tableTick(long tick) {
        if (phase == Phase.BETTING) {
            if (countdown % 20 == 0) {
                setStatus("§e§lApuestas abiertas\n§fCierran en §e" + countdown / 20 + "s");
            }
            if (--countdown <= 0) {
                startRound();
            }
        } else if (phase == Phase.PLAYERS) {
            OptionalInt active = round.activeSeat();
            if (active.isEmpty()) {
                return;
            }
            if (turnTicks % 20 == 0) {
                Player player = Bukkit.getPlayer(roundPlayers.get(active.getAsInt()));
                setStatus("§fTurno de §e" + (player == null ? "?" : player.getName()) + "\n§7" + turnTicks / 20 + "s");
            }
            if (--turnTicks <= 0) {
                message(active.getAsInt(), "§7Se te acabó el tiempo: te plantás.");
                standAction(active.getAsInt());
            }
        }
    }

    @Override
    protected void onDespawn() {
        context.escrow().refundPrefix("blackjack:" + installation.id() + ":");
        super.onDespawn();
    }

    private void refreshButtons(int seat) {
        if (occupant(seat) == null) {
            return;
        }
        if (phase == Phase.IDLE || phase == Phase.BETTING) {
            buttons(seat, List.of(
                new TableButton("§f§lAPOSTAR", TableButton.GOLD, this::openBetMenu),
                new TableButton("§f§lRETIRAR", TableButton.GREY, player -> withdraw(seat, player))));
        } else {
            buttons(seat, List.of());
        }
    }

    private void openBetMenu(Player player) {
        int seat = seatIndex(player);
        if (seat < 0) {
            return;
        }
        String key = key(seat, player.getUniqueId());
        ChipMenu.open(context, player, "§2Blackjack · Apuesta", context.settings().chips(),
            () -> context.escrow().held(key),
            (clicker, chip) -> addChip(seat, clicker, chip),
            () -> withdraw(seat, player));
    }

    private void addChip(int seat, Player player, long chip) {
        if (phase != Phase.IDLE && phase != Phase.BETTING) {
            player.sendMessage("§cLa mano ya empezó, esperá la próxima.");
            return;
        }
        String key = key(seat, player.getUniqueId());
        if (context.escrow().held(key) + chip > settings.maxBet()) {
            player.sendMessage("§cLa apuesta máxima de esta mesa es " + context.chips(settings.maxBet()) + ".");
            return;
        }
        if (!context.escrow().take(key, player.getUniqueId(), chip)) {
            player.sendMessage("§cNo te alcanza. Saldo: " + context.chips(context.bank().balance(player.getUniqueId())));
            return;
        }
        context.sound(frame.origin(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.6f);
        showChips(seat, context.escrow().held(key));
        if (phase == Phase.IDLE) {
            phase = Phase.BETTING;
            countdown = settings.bettingSeconds() * 20;
        }
    }

    private void withdraw(int seat, Player player) {
        if (phase != Phase.IDLE && phase != Phase.BETTING) {
            return;
        }
        long refunded = context.escrow().refund(key(seat, player.getUniqueId()));
        if (refunded > 0) {
            player.sendMessage("§7Retiraste " + context.chips(refunded) + ".");
        }
        showChips(seat, 0);
    }

    private void startRound() {
        round = new BlackjackRound(shoe);
        roundPlayers.clear();
        for (int seat = 0; seat < SEATS; seat++) {
            Player player = occupant(seat);
            if (player == null) {
                continue;
            }
            String key = key(seat, player.getUniqueId());
            long held = context.escrow().held(key);
            if (held <= 0) {
                continue;
            }
            if (held < settings.minBet()) {
                context.escrow().refund(key);
                showChips(seat, 0);
                player.sendMessage("§cLa apuesta mínima es " + context.chips(settings.minBet()) + ".");
                continue;
            }
            round.bet(seat, held);
            roundPlayers.put(seat, player.getUniqueId());
        }
        if (!round.hasBets()) {
            phase = Phase.IDLE;
            setStatus("§7Sentate y apostá");
            return;
        }
        phase = Phase.DEALING;
        for (int seat = 0; seat < SEATS; seat++) {
            clearButtons(seat);
        }
        setStatus("§6§lNo va más\n§fRepartiendo...");
        tableMessage("Cartas sobre la mesa.");
        List<BlackjackRound.Deal> deals = round.deal();
        Map<Integer, Integer> counts = new HashMap<>();
        int delay = 4;
        for (BlackjackRound.Deal deal : deals) {
            int n = counts.merge(deal.seat(), 1, Integer::sum) - 1;
            later(delay, () -> {
                if (deal.seat() == BlackjackRound.DEALER) {
                    ItemDisplay display = flyCard(dealerCards, dealerCardSpot(n), deal.faceDown() ? null : deal.card(),
                        null, false);
                    if (deal.faceDown()) {
                        holeCard = display;
                    }
                } else {
                    flyCard(seatCards(deal.seat()), seatCardSpot(deal.seat(), n), deal.card(), null, false);
                    updateSeatLabel(deal.seat());
                }
            });
            delay += 7;
        }
        later(delay + 6, () -> {
            for (int seat : round.seatsInPlay()) {
                BlackjackHand hand = round.hand(seat);
                if (hand.blackjack()) {
                    celebrate(seat, "§6§l¡BLACKJACK!");
                }
            }
            if (round.phase() == BlackjackRound.Phase.DEALER) {
                dealerTurn();
            } else {
                promptActive();
            }
        });
    }

    private void promptActive() {
        if (round == null || phase == Phase.DEALER || phase == Phase.SETTLING) {
            return;
        }
        OptionalInt active = round.activeSeat();
        if (active.isEmpty()) {
            dealerTurn();
            return;
        }
        phase = Phase.PLAYERS;
        int seat = active.getAsInt();
        Player player = occupant(seat);
        if (player == null || !player.getUniqueId().equals(roundPlayers.get(seat))) {
            round.stand(seat);
            later(2, this::promptActive);
            return;
        }
        turnTicks = settings.turnSeconds() * 20;
        List<TableButton> actions = new ArrayList<>();
        actions.add(new TableButton("§f§lPEDIR", TableButton.GREEN, clicker -> hitAction(seat)));
        actions.add(new TableButton("§f§lPLANTARSE", TableButton.RED, clicker -> standAction(seat)));
        if (round.canDouble(seat)) {
            actions.add(new TableButton("§f§lDOBLAR", TableButton.GOLD, clicker -> doubleAction(seat)));
        }
        buttons(seat, actions);
        player.sendActionBar(net.kyori.adventure.text.Component.text("Tenés " + round.hand(seat).describe()
            + " · ¿Pedís o te plantás?"));
        context.sound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
    }

    private void hitAction(int seat) {
        if (round == null || round.activeSeat().orElse(-2) != seat || phase != Phase.PLAYERS) {
            return;
        }
        clearButtons(seat);
        Card card = round.hit(seat);
        BlackjackHand hand = round.hand(seat);
        flyCard(seatCards(seat), seatCardSpot(seat, hand.size() - 1), card, null, false);
        later(5, () -> {
            updateSeatLabel(seat);
            if (hand.bust()) {
                bust(seat);
            }
        });
        later(9, this::promptActive);
    }

    private void standAction(int seat) {
        if (round == null || round.activeSeat().orElse(-2) != seat || phase != Phase.PLAYERS) {
            return;
        }
        clearButtons(seat);
        round.stand(seat);
        later(3, this::promptActive);
    }

    private void doubleAction(int seat) {
        if (round == null || !round.canDouble(seat) || phase != Phase.PLAYERS) {
            return;
        }
        UUID player = roundPlayers.get(seat);
        long extra = round.stake(seat);
        if (!context.escrow().take(key(seat, player), player, extra)) {
            message(seat, "§cNo te alcanza para doblar.");
            return;
        }
        clearButtons(seat);
        Card card = round.doubleDown(seat);
        showChips(seat, round.stake(seat));
        BlackjackHand hand = round.hand(seat);
        flyCard(seatCards(seat), seatCardSpot(seat, hand.size() - 1), card, null, false);
        later(5, () -> {
            updateSeatLabel(seat);
            if (hand.bust()) {
                bust(seat);
            }
        });
        later(10, this::promptActive);
    }

    private void dealerTurn() {
        if (phase == Phase.DEALER || phase == Phase.SETTLING) {
            return;
        }
        phase = Phase.DEALER;
        for (int seat = 0; seat < SEATS; seat++) {
            clearButtons(seat);
        }
        BlackjackHand dealerHand = round.hand(BlackjackRound.DEALER);
        later(8, () -> {
            if (holeCard != null) {
                flip(holeCard, dealerHand.cards().get(1));
            }
        });
        List<Card> drawn = round.playDealer();
        int delay = 22;
        int index = 2;
        setStatus("§fCrupier: §e?");
        later(16, () -> setStatus("§fCrupier: §e" + firstCards(dealerHand, 2)));
        for (Card card : drawn) {
            final int n = index++;
            final int shown = n + 1;
            later(delay, () -> {
                flyCard(dealerCards, dealerCardSpot(n), card, null, false);
                later(6, () -> setStatus("§fCrupier: §e" + firstCards(dealerHand, shown)));
            });
            delay += 16;
        }
        later(delay + 8, this::settleRound);
    }

    private void settleRound() {
        phase = Phase.SETTLING;
        BlackjackHand dealerHand = round.hand(BlackjackRound.DEALER);
        setStatus("§fCrupier: §e" + dealerHand.describe());
        Map<Integer, BlackjackRound.Settlement> results = round.settle();
        for (Map.Entry<Integer, BlackjackRound.Settlement> entry : results.entrySet()) {
            int seat = entry.getKey();
            UUID player = roundPlayers.get(seat);
            BlackjackRound.Settlement settlement = entry.getValue();
            context.escrow().settle(key(seat, player), settlement.payout());
            Player online = Bukkit.getPlayer(player);
            String line = switch (settlement.outcome()) {
                case BLACKJACK -> "§6§l¡BLACKJACK! §e+" + context.chips(settlement.payout() - settlement.stake());
                case WIN -> "§a§lGANASTE §a+" + context.chips(settlement.payout() - settlement.stake());
                case PUSH -> "§e§lEMPATE §7(se devuelve la apuesta)";
                case LOSE -> "§c§lPERDISTE §c-" + context.chips(settlement.stake());
            };
            setSeatLabel(seat, "§f" + (online == null ? "" : online.getName()) + "\n" + line);
            if (online != null) {
                online.sendMessage("§2[Blackjack] " + line);
                online.playSound(online.getLocation(), settlement.payout() > settlement.stake()
                    ? Sound.ENTITY_PLAYER_LEVELUP : settlement.payout() == 0
                    ? Sound.ENTITY_VILLAGER_NO : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1.0f);
            }
            if (settlement.payout() > settlement.stake()) {
                celebrate(seat, null);
            }
        }
        later(90, this::resetTable);
    }

    private void resetTable() {
        dealerCards.removeAll();
        holeCard = null;
        for (int seat = 0; seat < SEATS; seat++) {
            seatCards(seat).removeAll();
            showChips(seat, 0);
            Player player = occupant(seat);
            setSeatLabel(seat, player == null ? "" : "§f" + player.getName());
        }
        round = null;
        roundPlayers.clear();
        if (shoe.pastCutCard()) {
            shoe.shuffle();
            tableMessage("Se mezcla el sabot.");
        }
        phase = Phase.IDLE;
        setStatus("§7Sentate y apostá");
        for (int seat = 0; seat < SEATS; seat++) {
            refreshButtons(seat);
        }
    }

    private void bust(int seat) {
        context.sound(seatCardSpot(seat, 0), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.7f);
        message(seat, "§c§lTe pasaste. §7" + round.hand(seat).describe());
    }

    private void celebrate(int seat, String text) {
        var spot = seatCardSpot(seat, 1);
        spot.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spot.clone().add(0, 0.3, 0), 12, 0.3, 0.2, 0.3, 0);
        if (text != null) {
            message(seat, text);
        }
    }

    private void updateSeatLabel(int seat) {
        Player player = occupant(seat);
        setSeatLabel(seat, "§f" + (player == null ? "" : player.getName()) + "\n§e" + round.hand(seat).describe());
    }

    private static String firstCards(BlackjackHand hand, int count) {
        BlackjackHand partial = new BlackjackHand();
        hand.cards().stream().limit(count).forEach(partial::add);
        return partial.describe();
    }

    private dev.linqfy.bigCasares.modules.casino.world.EntityGroup seatCards(int seat) {
        return seats[seat].cards;
    }

    private int seatIndex(Player player) {
        Seat seat = seatOf(player.getUniqueId());
        return seat == null ? -1 : java.util.Arrays.asList(seats).indexOf(seat);
    }

    private String key(int seat, UUID player) {
        return "blackjack:" + installation.id() + ":" + seat + ":" + player;
    }
}
