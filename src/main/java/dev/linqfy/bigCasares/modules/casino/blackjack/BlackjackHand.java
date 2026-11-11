package dev.linqfy.bigCasares.modules.casino.blackjack;

import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.cards.Rank;

import java.util.ArrayList;
import java.util.List;

public final class BlackjackHand {

    private final List<Card> cards = new ArrayList<>();

    public void add(Card card) {
        cards.add(card);
    }

    public List<Card> cards() {
        return List.copyOf(cards);
    }

    public int size() {
        return cards.size();
    }

    public int total() {
        int total = 0;
        boolean ace = false;
        for (Card card : cards) {
            total += card.rank().blackjackValue();
            ace |= card.rank() == Rank.ACE;
        }
        return ace && total + 10 <= 21 ? total + 10 : total;
    }

    /** True while an ace is being counted as 11. */
    public boolean soft() {
        int hard = 0;
        boolean ace = false;
        for (Card card : cards) {
            hard += card.rank().blackjackValue();
            ace |= card.rank() == Rank.ACE;
        }
        return ace && hard + 10 <= 21;
    }

    public boolean bust() {
        return total() > 21;
    }

    public boolean blackjack() {
        return cards.size() == 2 && total() == 21;
    }

    /** Text shown above the cards, for example {@code 17}, {@code 7/17} or {@code BLACKJACK}. */
    public String describe() {
        if (blackjack()) {
            return "BLACKJACK";
        }
        if (bust()) {
            return "SE PASÓ (" + total() + ")";
        }
        int total = total();
        return soft() && total != 21 ? (total - 10) + "/" + total : Integer.toString(total);
    }
}
