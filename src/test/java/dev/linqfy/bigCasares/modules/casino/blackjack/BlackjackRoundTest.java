package dev.linqfy.bigCasares.modules.casino.blackjack;

import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.CardSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackRoundTest {

    /** Cards are dealt seat by seat, then the dealer, twice. */
    private static CardSource stacked(String... codes) {
        Deque<Card> cards = new ArrayDeque<>(Arrays.stream(codes).map(Card::parse).toList());
        return cards::removeFirst;
    }

    @Test
    void handTotalsCountAcesSoftOrHard() {
        BlackjackHand hand = new BlackjackHand();
        hand.add(Card.parse("as"));
        hand.add(Card.parse("6h"));
        assertEquals(17, hand.total());
        assertTrue(hand.soft());
        assertEquals("7/17", hand.describe());
        hand.add(Card.parse("9c"));
        assertEquals(16, hand.total());
        assertFalse(hand.soft());
        hand.add(Card.parse("kd"));
        assertTrue(hand.bust());
    }

    @Test
    void naturalBlackjackPaysThreeToTwo() {
        BlackjackRound round = new BlackjackRound(stacked("as", "9h", "kd", "7c"));
        round.bet(0, 100);
        round.deal();
        assertEquals(BlackjackRound.Phase.DEALER, round.phase(), "a natural skips the player's turn");
        round.playDealer();
        BlackjackRound.Settlement settlement = round.settle().get(0);
        assertEquals(BlackjackOutcome.BLACKJACK, settlement.outcome());
        assertEquals(250, settlement.payout());
    }

    @Test
    void dealerStandsOnSeventeenAndHigherHandWins() {
        // seat 0: 10 + 8 = 18; dealer: 10 + 6, draws an ace for a hard 17 and stops.
        BlackjackRound round = new BlackjackRound(stacked("10h", "10s", "8h", "6c", "as", "5d"));
        round.bet(0, 50);
        round.deal();
        assertEquals(0, round.activeSeat().getAsInt());
        round.stand(0);
        List<Card> drawn = round.playDealer();
        assertEquals(List.of(Card.parse("as")), drawn, "16 + ace = 17 and the dealer stops");
        Map<Integer, BlackjackRound.Settlement> result = round.settle();
        assertEquals(BlackjackOutcome.WIN, result.get(0).outcome());
        assertEquals(100, result.get(0).payout());
    }

    @Test
    void bustingLosesEvenIfTheDealerBustsToo() {
        BlackjackRound round = new BlackjackRound(stacked("10h", "10s", "6h", "6c", "9d", "10c"));
        round.bet(0, 40);
        round.deal();
        round.hit(0);
        assertEquals(BlackjackRound.Phase.DEALER, round.phase(), "25 ends the turn");
        round.playDealer();
        assertEquals(BlackjackOutcome.LOSE, round.settle().get(0).outcome());
    }

    @Test
    void doublingDrawsOneCardAndDoublesTheStake() {
        BlackjackRound round = new BlackjackRound(stacked("5h", "10s", "6h", "7c", "10d"));
        round.bet(0, 30);
        round.deal();
        assertTrue(round.canDouble(0));
        Card card = round.doubleDown(0);
        assertEquals(Card.parse("10d"), card);
        assertEquals(60, round.stake(0));
        round.playDealer();
        BlackjackRound.Settlement settlement = round.settle().get(0);
        assertEquals(BlackjackOutcome.WIN, settlement.outcome());
        assertEquals(120, settlement.payout());
    }

    @Test
    void tiesPushAndDealerBlackjackBeatsTwentyOne() {
        BlackjackRound push = new BlackjackRound(stacked("10h", "10s", "8h", "8c"));
        push.bet(0, 10);
        push.deal();
        push.stand(0);
        push.playDealer();
        assertEquals(BlackjackOutcome.PUSH, push.settle().get(0).outcome());

        BlackjackRound dealerNatural = new BlackjackRound(stacked("10h", "as", "kh", "kc"));
        dealerNatural.bet(0, 10);
        dealerNatural.deal();
        assertEquals(BlackjackRound.Phase.DEALER, dealerNatural.phase());
        dealerNatural.playDealer();
        assertEquals(0, dealerNatural.settle().get(0).payout());
    }

    @Test
    void turnsGoSeatBySeatAndOnlyTheActiveSeatMayAct() {
        BlackjackRound round = new BlackjackRound(stacked("10h", "9h", "10s", "8h", "7h", "7c", "10d"));
        round.bet(0, 10);
        round.bet(2, 10);
        round.deal();
        assertEquals(0, round.activeSeat().getAsInt());
        assertThrows(IllegalStateException.class, () -> round.hit(2));
        round.stand(0);
        assertEquals(2, round.activeSeat().getAsInt());
        round.stand(2);
        assertEquals(BlackjackRound.Phase.DEALER, round.phase());
    }
}
