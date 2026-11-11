package dev.linqfy.bigCasares.modules.casino.poker;

import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.CardSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

/**
 * One fixed-limit Texas Hold'em hand. Bets are a small bet (the big blind) before the turn and a big bet
 * after it, with a cap of four bets per street. Stacks that cannot cover a bet go all-in and are paid from
 * side pots. Seats are table indexes; the hand only knows chips and cards.
 */
public final class HoldemHand {

    public static final int RAISE_CAP = 4;

    public record Participant(int seat, long stack) {
    }

    public record Payout(int seat, long amount, HandValue value) {
    }

    private final CardSource source;
    private final List<Player> players = new ArrayList<>();
    private final List<Card> board = new ArrayList<>();
    private final long smallBlind;
    private final long bigBlind;
    private final int buttonIndex;
    private Street street = Street.PREFLOP;
    private long currentBet;
    private int raises;
    private int toAct = -1;
    private final List<Payout> payouts = new ArrayList<>();
    private boolean finished;

    /**
     * @param participants seats in clockwise order
     * @param buttonIndex index into {@code participants} of the dealer button
     */
    public HoldemHand(List<Participant> participants, int buttonIndex, long smallBlind, long bigBlind,
                      CardSource source) {
        if (participants.size() < 2) {
            throw new IllegalArgumentException("Hold'em needs at least two players");
        }
        if (smallBlind <= 0 || bigBlind < smallBlind) {
            throw new IllegalArgumentException("Invalid blinds");
        }
        this.source = Objects.requireNonNull(source, "source");
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.buttonIndex = Math.floorMod(buttonIndex, participants.size());
        for (Participant participant : participants) {
            if (participant.stack() <= 0) {
                throw new IllegalArgumentException("Seat " + participant.seat() + " has no chips");
            }
            players.add(new Player(participant.seat(), participant.stack()));
        }
    }

    /** Posts the blinds and deals two hole cards to everybody. */
    public void start() {
        if (!players.getFirst().hole.isEmpty()) {
            throw new IllegalStateException("Hand already started");
        }
        int count = players.size();
        int sbIndex = count == 2 ? buttonIndex : (buttonIndex + 1) % count;
        int bbIndex = (sbIndex + 1) % count;
        commit(players.get(sbIndex), Math.min(smallBlind, players.get(sbIndex).stack));
        commit(players.get(bbIndex), Math.min(bigBlind, players.get(bbIndex).stack));
        currentBet = bigBlind;
        raises = 1;
        for (int pass = 0; pass < 2; pass++) {
            for (int offset = 1; offset <= count; offset++) {
                players.get((buttonIndex + offset) % count).hole.add(source.draw());
            }
        }
        toAct = nextActor(bbIndex);
        if (toAct < 0) {
            finishStreet();
        }
    }

    public Street street() {
        return street;
    }

    public boolean finished() {
        return finished;
    }

    public List<Card> board() {
        return List.copyOf(board);
    }

    public List<Card> holeCards(int seat) {
        return List.copyOf(player(seat).hole);
    }

    public long stack(int seat) {
        return player(seat).stack;
    }

    public boolean folded(int seat) {
        return player(seat).folded;
    }

    public long pot() {
        return players.stream().mapToLong(player -> player.total).sum();
    }

    public long committedThisStreet(int seat) {
        return player(seat).street;
    }

    public OptionalInt actingSeat() {
        return finished || toAct < 0 ? OptionalInt.empty() : OptionalInt.of(players.get(toAct).seat);
    }

    public long toCall(int seat) {
        Player player = player(seat);
        return Math.min(player.stack, currentBet - player.street);
    }

    public long betSize() {
        return street == Street.TURN || street == Street.RIVER ? bigBlind * 2 : bigBlind;
    }

    public Set<PokerAction> legalActions(int seat) {
        if (actingSeat().orElse(Integer.MIN_VALUE) != seat) {
            return Set.of();
        }
        Player player = player(seat);
        Set<PokerAction> actions = EnumSet.of(PokerAction.FOLD);
        long owed = currentBet - player.street;
        actions.add(owed > 0 ? PokerAction.CALL : PokerAction.CHECK);
        if (raises < RAISE_CAP && player.stack > owed) {
            actions.add(PokerAction.RAISE);
        }
        return actions;
    }

    public void act(int seat, PokerAction action) {
        if (!legalActions(seat).contains(action)) {
            throw new IllegalStateException("Illegal action " + action + " for seat " + seat);
        }
        Player player = player(seat);
        switch (action) {
            case FOLD -> player.folded = true;
            case CHECK -> {
            }
            case CALL -> commit(player, Math.min(player.stack, currentBet - player.street));
            case RAISE -> {
                long target = currentBet + betSize();
                commit(player, Math.min(player.stack, target - player.street));
                if (player.street > currentBet) {
                    currentBet = player.street;
                    raises++;
                    players.forEach(other -> other.acted = false);
                }
            }
        }
        player.acted = true;

        List<Player> live = players.stream().filter(candidate -> !candidate.folded).toList();
        if (live.size() == 1) {
            awardUncontested(live.getFirst());
            return;
        }
        int next = nextActor(players.indexOf(player));
        if (next < 0) {
            finishStreet();
        } else {
            toAct = next;
        }
    }

    public List<Payout> payouts() {
        return List.copyOf(payouts);
    }

    /** Best hand of a seat with the current board, or {@code null} before five cards are known. */
    public HandValue value(int seat) {
        List<Card> cards = new ArrayList<>(player(seat).hole);
        cards.addAll(board);
        return cards.size() >= 5 ? HandEvaluator.best(cards) : null;
    }

    public List<Integer> seats() {
        return players.stream().map(player -> player.seat).toList();
    }

    private void finishStreet() {
        while (true) {
            for (Player player : players) {
                player.street = 0;
                player.acted = false;
            }
            currentBet = 0;
            raises = 0;
            switch (street) {
                case PREFLOP -> {
                    street = Street.FLOP;
                    board.add(source.draw());
                    board.add(source.draw());
                    board.add(source.draw());
                }
                case FLOP -> {
                    street = Street.TURN;
                    board.add(source.draw());
                }
                case TURN -> {
                    street = Street.RIVER;
                    board.add(source.draw());
                }
                default -> {
                    showdown();
                    return;
                }
            }
            long canAct = players.stream().filter(player -> !player.folded && player.stack > 0).count();
            if (canAct >= 2) {
                toAct = nextActor(buttonIndex);
                if (toAct >= 0) {
                    return;
                }
            }
        }
    }

    private void showdown() {
        street = Street.SHOWDOWN;
        toAct = -1;
        Map<Player, HandValue> values = new LinkedHashMap<>();
        for (Player player : players) {
            if (!player.folded) {
                List<Card> cards = new ArrayList<>(player.hole);
                cards.addAll(board);
                values.put(player, HandEvaluator.best(cards));
            }
        }
        Map<Player, Long> won = new LinkedHashMap<>();
        TreeSet<Long> levels = new TreeSet<>();
        players.forEach(player -> levels.add(player.total));
        long previous = 0;
        for (long level : levels) {
            if (level <= previous) {
                continue;
            }
            long slice = level - previous;
            long pot = 0;
            for (Player player : players) {
                pot += Math.max(0, Math.min(player.total, level) - previous);
            }
            final long floor = level;
            List<Player> eligible = values.keySet().stream().filter(player -> player.total >= floor).toList();
            previous = level;
            if (pot == 0 || slice == 0) {
                continue;
            }
            if (eligible.isEmpty()) {
                // Only folded money above every live stack: it belongs to the best live hand overall.
                eligible = List.copyOf(values.keySet());
            }
            HandValue best = eligible.stream().map(values::get).max(HandValue::compareTo).orElseThrow();
            List<Player> winners = eligible.stream().filter(player -> values.get(player).compareTo(best) == 0).toList();
            long share = pot / winners.size();
            long remainder = pot % winners.size();
            for (Player winner : winners) {
                long amount = share + (remainder-- > 0 ? 1 : 0);
                won.merge(winner, amount, Long::sum);
            }
        }
        for (Map.Entry<Player, Long> entry : won.entrySet()) {
            entry.getKey().stack += entry.getValue();
            payouts.add(new Payout(entry.getKey().seat, entry.getValue(), values.get(entry.getKey())));
        }
        finished = true;
    }

    private void awardUncontested(Player winner) {
        long pot = pot();
        winner.stack += pot;
        payouts.add(new Payout(winner.seat, pot, null));
        toAct = -1;
        finished = true;
    }

    /** The next player after {@code fromIndex} who still owes an action this street, or -1. */
    private int nextActor(int fromIndex) {
        int count = players.size();
        for (int offset = 1; offset <= count; offset++) {
            int index = (fromIndex + offset) % count;
            Player player = players.get(index);
            if (player.folded || player.stack == 0) {
                continue;
            }
            if (!player.acted || player.street < currentBet) {
                long othersAbleToAct = players.stream()
                    .filter(other -> other != player && !other.folded && other.stack > 0).count();
                if (othersAbleToAct == 0 && player.street >= currentBet) {
                    continue;
                }
                return index;
            }
        }
        return -1;
    }

    private void commit(Player player, long amount) {
        player.stack -= amount;
        player.street += amount;
        player.total += amount;
    }

    private Player player(int seat) {
        for (Player player : players) {
            if (player.seat == seat) {
                return player;
            }
        }
        throw new IllegalArgumentException("Seat " + seat + " is not in this hand");
    }

    private static final class Player {
        private final int seat;
        private final List<Card> hole = new ArrayList<>(2);
        private long stack;
        private long street;
        private long total;
        private boolean folded;
        private boolean acted;

        private Player(int seat, long stack) {
            this.seat = seat;
            this.stack = stack;
        }
    }
}
