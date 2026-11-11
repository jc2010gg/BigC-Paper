package dev.linqfy.bigCasares.modules.casino.loans;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Usurious loans. Every started hour past the due date adds {@code lateFeePerHour} of the contract amount,
 * up to {@code maxMultiplier} times that amount. One loan per player.
 */
public final class LoanBook {

    private static final long HOUR = 3_600_000L;

    private final double lateFeePerHour;
    private final double maxMultiplier;
    private final Map<UUID, Loan> loans = new LinkedHashMap<>();

    public LoanBook(double lateFeePerHour, double maxMultiplier) {
        if (lateFeePerHour < 0 || maxMultiplier < 1) {
            throw new IllegalArgumentException("Invalid late fee settings");
        }
        this.lateFeePerHour = lateFeePerHour;
        this.maxMultiplier = maxMultiplier;
    }

    public Optional<Loan> loan(UUID debtor) {
        return Optional.ofNullable(loans.get(debtor));
    }

    public Collection<Loan> loans() {
        return List.copyOf(loans.values());
    }

    public Loan take(UUID debtor, LoanOffer offer, long now) {
        if (loans.containsKey(debtor)) {
            throw new IllegalStateException("Ya tenés un préstamo activo");
        }
        Loan loan = new Loan(debtor, offer.principal(), offer.repayment(), 0, 0, now,
            now + offer.termMinutes() * 60_000L);
        loans.put(debtor, loan);
        return loan;
    }

    public void restore(Loan loan) {
        loans.put(loan.debtor(), loan);
    }

    public long lateFees(Loan loan, long now) {
        if (now <= loan.dueAtMillis()) {
            return 0;
        }
        long hours = (now - loan.dueAtMillis() + HOUR - 1) / HOUR;
        long fees = Math.round(loan.owed() * lateFeePerHour * hours);
        long cap = Math.round(loan.owed() * (maxMultiplier - 1.0));
        return Math.min(fees, cap);
    }

    /** What is still owed now, fees included. */
    public long balance(UUID debtor, long now) {
        Loan loan = loans.get(debtor);
        if (loan == null) {
            return 0;
        }
        return Math.max(0, loan.owed() + loan.surcharges() + lateFees(loan, now) - loan.paid());
    }

    public boolean overdue(UUID debtor, long now) {
        Loan loan = loans.get(debtor);
        return loan != null && now > loan.dueAtMillis() && balance(debtor, now) > 0;
    }

    /** Applies a payment and returns the part of it that was actually used. Clears the loan when paid off. */
    public long pay(UUID debtor, long amount, long now) {
        Loan loan = loans.get(debtor);
        if (loan == null || amount <= 0) {
            return 0;
        }
        long used = Math.min(amount, balance(debtor, now));
        Loan updated = loan.withPayment(used);
        loans.put(debtor, updated);
        if (balance(debtor, now) == 0) {
            loans.remove(debtor);
        }
        return used;
    }

    /** Extra charge, for example for punching the collector. */
    public long surcharge(UUID debtor, double fraction) {
        Loan loan = loans.get(debtor);
        if (loan == null) {
            return 0;
        }
        long amount = Math.max(1, Math.round(loan.owed() * fraction));
        loans.put(debtor, loan.withSurcharge(amount));
        return amount;
    }
}
