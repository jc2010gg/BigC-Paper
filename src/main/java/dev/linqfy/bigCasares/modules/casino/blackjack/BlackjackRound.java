package dev.linqfy.bigCasares.modules.casino.blackjack;

import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.CardSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * One round of classic blackjack against the house: blackjack pays 3:2, the dealer stands on every 17,
 * doubling is allowed on the first two cards. Seats are plain indexes so the table decides who sits where.
 */
public final class BlackjackRound {

    public static final int DEALER = -1;

    public enum Phase {
        BETTING,
        PLAYERS,
        DEALER,
        SETTLED
    }

    /** One card leaving the shoe, in the order the dealer hands them out. */
    public record Deal(int seat, Card card, boolean faceDown) {
    }

    public record Settlement(BlackjackOutcome outcome, long stake, long payout) {
    }

    private final CardSource source;
    private final TreeMap<Integer, SeatState> seats = new TreeMap<>();
    private final BlackjackHand dealer = new BlackjackHand();
    private Phase phase = Phase.BETTING;
    private Integer active;

    public BlackjackRound(CardSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public Phase phase() {
        return phase;
    }

    public void bet(int seat, long amount) {
        requirePhase(Phase.BETTING);
        if (seat < 0) {
            throw new IllegalArgumentException("Invalid seat " + seat);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("A bet must be positive");
        }
        seats.computeIfAbsent(seat, ignored -> new SeatState()).stake += amount;
    }

    public boolean hasBets() {
        return !seats.isEmpty();
    }

    public long stake(int seat) {
        SeatState state = seats.get(seat);
        return state == null ? 0 : state.stake;
    }

    /** Deals two cards to every seat with a bet and to the dealer; the dealer's second card is face down. */
    public List<Deal> deal() {
        requirePhase(Phase.BETTING);
        if (seats.isEmpty()) {
            throw new IllegalStateException("Nobody placed a bet");
        }
        List<Deal> deals = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<Integer, SeatState> entry : seats.entrySet()) {
                Card card = source.draw();
                entry.getValue().hand.add(card);
                deals.add(new Deal(entry.getKey(), card, false));
            }
            Card card = source.draw();
            dealer.add(card);
            deals.add(new Deal(DEALER, card, pass == 1));
        }
        phase = Phase.PLAYERS;
        for (SeatState state : seats.values()) {
            if (state.hand.blackjack()) {
                state.done = true;
            }
        }
        if (dealer.blackjack()) {
            seats.values().forEach(state -> state.done = true);
        }
        advance();
        return deals;
    }

    public OptionalInt activeSeat() {
        return phase == Phase.PLAYERS && active != null ? OptionalInt.of(active) : OptionalInt.empty();
    }

    public boolean canDouble(int seat) {
        SeatState state = seats.get(seat);
        return isActive(seat) && state.hand.size() == 2 && !state.doubled;
    }

    public Card hit(int seat) {
        SeatState state = requireActive(seat);
        Card card = source.draw();
        state.hand.add(card);
        if (state.hand.total() >= 21) {
            state.done = true;
            advance();
        }
        return card;
    }

    public void stand(int seat) {
        requireActive(seat).done = true;
        advance();
    }

    /** Doubles the stake, draws exactly one card and ends the seat's turn. The caller collects the extra stake. */
    public Card doubleDown(int seat) {
        if (!canDouble(seat)) {
            throw new IllegalStateException("Seat " + seat + " cannot double");
        }
        SeatState state = seats.get(seat);
        state.stake *= 2;
        state.doubled = true;
        Card card = source.draw();
        state.hand.add(card);
        state.done = true;
        advance();
        return card;
    }

    /** Reveals the hole card and draws until 17 or more. Returns the cards drawn after the reveal. */
    public List<Card> playDealer() {
        requirePhase(Phase.DEALER);
        List<Card> drawn = new ArrayList<>();
        boolean anyLive = seats.values().stream().anyMatch(state -> !state.hand.bust() && !state.hand.blackjack());
        while (anyLive && dealer.total() < 17) {
            Card card = source.draw();
            dealer.add(card);
            drawn.add(card);
        }
        return drawn;
    }

    public Map<Integer, Settlement> settle() {
        requirePhase(Phase.DEALER);
        Map<Integer, Settlement> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, SeatState> entry : seats.entrySet()) {
            SeatState state = entry.getValue();
            BlackjackOutcome outcome = outcome(state.hand);
            long payout = switch (outcome) {
                case BLACKJACK -> state.stake + state.stake * 3 / 2;
                case WIN -> state.stake * 2;
                case PUSH -> state.stake;
                case LOSE -> 0;
            };
            result.put(entry.getKey(), new Settlement(outcome, state.stake, payout));
        }
        phase = Phase.SETTLED;
        return result;
    }

    public BlackjackHand hand(int seat) {
        if (seat == DEALER) {
            return dealer;
        }
        SeatState state = seats.get(seat);
        if (state == null) {
            throw new IllegalArgumentException("Seat " + seat + " has no hand");
        }
        return state.hand;
    }

    public List<Integer> seatsInPlay() {
        return List.copyOf(seats.keySet());
    }

    private BlackjackOutcome outcome(BlackjackHand hand) {
        if (hand.blackjack()) {
            return dealer.blackjack() ? BlackjackOutcome.PUSH : BlackjackOutcome.BLACKJACK;
        }
        if (hand.bust() || dealer.blackjack()) {
            return BlackjackOutcome.LOSE;
        }
        if (dealer.bust() || hand.total() > dealer.total()) {
            return BlackjackOutcome.WIN;
        }
        return hand.total() == dealer.total() ? BlackjackOutcome.PUSH : BlackjackOutcome.LOSE;
    }

    private void advance() {
        active = null;
        for (Map.Entry<Integer, SeatState> entry : seats.entrySet()) {
            if (!entry.getValue().done) {
                active = entry.getKey();
                return;
            }
        }
        phase = Phase.DEALER;
    }

    private boolean isActive(int seat) {
        return phase == Phase.PLAYERS && active != null && active == seat;
    }

    private SeatState requireActive(int seat) {
        if (!isActive(seat)) {
            throw new IllegalStateException("It is not seat " + seat + "'s turn");
        }
        return seats.get(seat);
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected phase " + expected + " but was " + phase);
        }
    }

    private static final class SeatState {
        private final BlackjackHand hand = new BlackjackHand();
        private long stake;
        private boolean doubled;
        private boolean done;
    }
}
