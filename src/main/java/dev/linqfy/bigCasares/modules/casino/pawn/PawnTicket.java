package dev.linqfy.bigCasares.modules.casino.pawn;

import java.util.UUID;

/**
 * A pawned (or seized) item. While {@link State#PAWNED} its owner can buy it back for {@code redeemPrice};
 * once it expires it goes to the shop window for anybody at {@code salePrice}.
 */
public record PawnTicket(String id, UUID owner, String itemData, String itemName, long loan, long redeemPrice,
                         long salePrice, long createdAtMillis, long expiresAtMillis, State state) {

    public enum State {
        PAWNED,
        SHOWCASE
    }

    public PawnTicket withState(State newState) {
        return new PawnTicket(id, owner, itemData, itemName, loan, redeemPrice, salePrice, createdAtMillis,
            expiresAtMillis, newState);
    }
}
