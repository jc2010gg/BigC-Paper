package dev.linqfy.bigCasares.modules.casino.loans;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanBookTest {

    private static final long HOUR = 3_600_000L;
    private final UUID debtor = UUID.randomUUID();
    private final LoanOffer offer = new LoanOffer("chico", 1000, 0.35, 120);

    @Test
    void interestIsFixedUpFrontAndOneLoanAtATime() {
        LoanBook book = new LoanBook(0.10, 3.0);
        book.take(debtor, offer, 0);
        assertEquals(1350, book.balance(debtor, 0));
        assertFalse(book.overdue(debtor, 2 * HOUR));
        assertThrows(IllegalStateException.class, () -> book.take(debtor, offer, 0));
    }

    @Test
    void lateFeesGrowEveryStartedHourUpToTheCap() {
        LoanBook book = new LoanBook(0.10, 3.0);
        book.take(debtor, offer, 0);
        long due = 2 * HOUR;
        assertTrue(book.overdue(debtor, due + 1));
        assertEquals(1350 + 135, book.balance(debtor, due + 1));
        assertEquals(1350 + 270, book.balance(debtor, due + HOUR + 1));
        assertEquals(1350 * 3, book.balance(debtor, due + 100 * HOUR), "capped at three times the contract");
    }

    @Test
    void paymentsClearTheLoanAndSurchargesAddUp() {
        LoanBook book = new LoanBook(0.10, 3.0);
        book.take(debtor, offer, 0);
        assertEquals(68, book.surcharge(debtor, 0.05));
        assertEquals(1418, book.balance(debtor, 0));
        assertEquals(418, book.pay(debtor, 418, 0));
        assertEquals(1000, book.pay(debtor, 5000, 0), "only what is owed is used");
        assertTrue(book.loan(debtor).isEmpty());
        assertFalse(book.overdue(debtor, 10 * HOUR));
    }
}
