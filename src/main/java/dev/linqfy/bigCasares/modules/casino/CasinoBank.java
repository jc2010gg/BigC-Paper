package dev.linqfy.bigCasares.modules.casino;

import java.util.UUID;

/** Chips are whole units of the server economy. */
public interface CasinoBank {

    long balance(UUID player);

    boolean withdraw(UUID player, long amount);

    boolean deposit(UUID player, long amount);

    String format(long amount);
}
