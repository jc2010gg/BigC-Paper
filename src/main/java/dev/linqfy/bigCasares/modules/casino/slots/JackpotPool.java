package dev.linqfy.bigCasares.modules.casino.slots;

/** Progressive jackpot shared by every machine: a slice of each bet feeds it, a win resets it to the seed. */
public final class JackpotPool {

    private final long seed;
    private final double contribution;
    private double pool;

    public JackpotPool(long seed, double contribution, double current) {
        if (seed < 0 || contribution < 0 || contribution >= 1) {
            throw new IllegalArgumentException("Invalid jackpot settings");
        }
        this.seed = seed;
        this.contribution = contribution;
        this.pool = Math.max(seed, current);
    }

    public void feed(long bet) {
        pool += bet * contribution;
    }

    public long value() {
        return (long) Math.floor(pool);
    }

    /** Pays the whole pool and resets it. */
    public long claim() {
        long won = value();
        pool = seed;
        return won;
    }

    public double raw() {
        return pool;
    }
}
