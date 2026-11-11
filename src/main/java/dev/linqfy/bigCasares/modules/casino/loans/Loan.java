package dev.linqfy.bigCasares.modules.casino.loans;

import java.util.UUID;

/** Outstanding debt. {@code owed} is the contract amount; surcharges and late fees are added on top. */
public record Loan(UUID debtor, long principal, long owed, long paid, long surcharges,
                   long takenAtMillis, long dueAtMillis) {

    public Loan withPayment(long amount) {
        return new Loan(debtor, principal, owed, paid + amount, surcharges, takenAtMillis, dueAtMillis);
    }

    public Loan withSurcharge(long amount) {
        return new Loan(debtor, principal, owed, paid, surcharges + amount, takenAtMillis, dueAtMillis);
    }
}
