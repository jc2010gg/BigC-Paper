package dev.linqfy.bigCasares.modules.casino.loans;

/** A loan tier offered by the loan shark. {@code interest} is a fraction: 0.5 means +50%. */
public record LoanOffer(String id, long principal, double interest, long termMinutes) {

    public LoanOffer {
        if (principal <= 0 || interest < 0 || termMinutes <= 0) {
            throw new IllegalArgumentException("Invalid loan offer " + id);
        }
    }

    public long repayment() {
        return Math.round(principal * (1.0 + interest));
    }
}
