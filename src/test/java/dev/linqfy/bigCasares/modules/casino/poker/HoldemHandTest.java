package dev.linqfy.bigCasares.modules.casino.poker;

import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.CardSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldemHandTest {

    private static CardSource stacked(String... codes) {
        Deque<Card> cards = new ArrayDeque<>(Arrays.stream(codes).map(Card::parse).toList());
        return cards::removeFirst;
    }

    @Test
    void headsUpButtonPostsSmallBlindAndActsFirst() {
        HoldemHand hand = new HoldemHand(List.of(new HoldemHand.Participant(0, 1000),
            new HoldemHand.Participant(1, 1000)), 0, 10, 20, stacked(
            "as", "kd", "ah", "kc", "2c", "7d", "9h", "3s", "4s"));
        hand.start();
        assertEquals(990, hand.stack(0));
        assertEquals(980, hand.stack(1));
        assertEquals(0, hand.actingSeat().getAsInt());
        assertEquals(10, hand.toCall(0));
        hand.act(0, PokerAction.FOLD);
        assertTrue(hand.finished());
        assertEquals(1010, hand.stack(1), "the big blind collects both blinds");
        assertEquals(1, hand.payouts().size());
    }

    @Test
    void checkedDownHandGoesToShowdownAndPaysTheBestHand() {
        // Hole cards go one at a time from the left of the button: seat1, seat0, seat1, seat0.
        HoldemHand hand = new HoldemHand(List.of(new HoldemHand.Participant(0, 500),
            new HoldemHand.Participant(1, 500)), 0, 10, 20, stacked(
            "kd", "as", "kc", "ah", "2c", "7d", "9h", "3s", "4s"));
        hand.start();
        hand.act(0, PokerAction.CALL);
        hand.act(1, PokerAction.CHECK);
        assertEquals(Street.FLOP, hand.street());
        for (int street = 0; street < 3; street++) {
            hand.act(1, PokerAction.CHECK);
            hand.act(0, PokerAction.CHECK);
        }
        assertTrue(hand.finished());
        assertEquals(Street.SHOWDOWN, hand.street());
        assertEquals(520, hand.stack(0), "pocket aces beat pocket kings");
        assertEquals(480, hand.stack(1));
    }

    @Test
    void raisesAreFixedAndCappedAtFourBets() {
        HoldemHand hand = new HoldemHand(List.of(new HoldemHand.Participant(0, 1000),
            new HoldemHand.Participant(1, 1000)), 0, 10, 20, stacked(
            "kd", "as", "kc", "ah", "2c", "7d", "9h", "3s", "4s"));
        hand.start();
        hand.act(0, PokerAction.RAISE);
        assertEquals(40, hand.committedThisStreet(0));
        hand.act(1, PokerAction.RAISE);
        assertEquals(60, hand.committedThisStreet(1));
        hand.act(0, PokerAction.RAISE);
        assertEquals(80, hand.committedThisStreet(0));
        assertTrue(!hand.legalActions(1).contains(PokerAction.RAISE), "the fourth bet caps the street");
        hand.act(1, PokerAction.CALL);
        assertEquals(Street.FLOP, hand.street());
        assertEquals(160, hand.pot());
    }

    @Test
    void shortAllInOnlyWinsTheMainPot() {
        // seat0 is short with 50 and holds aces; seats 1 and 2 keep betting into a side pot.
        HoldemHand hand = new HoldemHand(List.of(
            new HoldemHand.Participant(0, 50),
            new HoldemHand.Participant(1, 1000),
            new HoldemHand.Participant(2, 1000)), 2, 10, 20, stacked(
            // left of button (seat0), seat1, seat2, then again
            "as", "kd", "qd", "ah", "kc", "qc",
            "2c", "7d", "9h", "3s", "4s"));
        hand.start();
        // seat0 SB, seat1 BB, seat2 acts first.
        assertEquals(2, hand.actingSeat().getAsInt());
        hand.act(2, PokerAction.RAISE);
        hand.act(0, PokerAction.RAISE);
        hand.act(1, PokerAction.RAISE);
        hand.act(2, PokerAction.CALL);
        // seat0 is all-in at 50 by now or calls the rest with what it has.
        while (!hand.finished()) {
            int seat = hand.actingSeat().getAsInt();
            hand.act(seat, hand.legalActions(seat).contains(PokerAction.CHECK) ? PokerAction.CHECK : PokerAction.CALL);
        }
        long total = hand.stack(0) + hand.stack(1) + hand.stack(2);
        assertEquals(2050, total, "chips are conserved");
        assertEquals(150, hand.stack(0), "aces win three times their 50");
        assertTrue(hand.stack(1) > hand.stack(2), "kings take the side pot from queens");
    }
}
