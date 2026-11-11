package dev.linqfy.bigCasares.modules.casino.pawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pawn tickets and the liquidation window. Item data is opaque (serialized by the shop). */
public final class PawnLedger {

    private final double loanRatio;
    private final double redeemRatio;
    private final double saleRatio;
    private final long holdMillis;
    private final Map<String, PawnTicket> tickets = new LinkedHashMap<>();
    private long sequence;

    public PawnLedger(double loanRatio, double redeemRatio, double saleRatio, long holdMillis) {
        if (loanRatio <= 0 || loanRatio >= 1 || redeemRatio < 1 || saleRatio <= 0 || holdMillis <= 0) {
            throw new IllegalArgumentException("Invalid pawn settings");
        }
        this.loanRatio = loanRatio;
        this.redeemRatio = redeemRatio;
        this.saleRatio = saleRatio;
        this.holdMillis = holdMillis;
    }

    public long offerFor(long appraisal) {
        return Math.max(1, Math.round(appraisal * loanRatio));
    }

    public PawnTicket pawn(UUID owner, String itemData, String itemName, long appraisal, long now) {
        long loan = offerFor(appraisal);
        PawnTicket ticket = new PawnTicket(nextId(now), owner, itemData, itemName, loan,
            Math.round(loan * redeemRatio), salePrice(appraisal), now, now + holdMillis, PawnTicket.State.PAWNED);
        tickets.put(ticket.id(), ticket);
        return ticket;
    }

    /** Puts a seized item straight into the window. */
    public PawnTicket consign(String itemData, String itemName, long appraisal, long now) {
        PawnTicket ticket = new PawnTicket(nextId(now), null, itemData, itemName, 0, 0, salePrice(appraisal),
            now, now, PawnTicket.State.SHOWCASE);
        tickets.put(ticket.id(), ticket);
        return ticket;
    }

    /** Moves expired tickets to the window and returns them. */
    public List<PawnTicket> expire(long now) {
        List<PawnTicket> expired = new ArrayList<>();
        for (PawnTicket ticket : List.copyOf(tickets.values())) {
            if (ticket.state() == PawnTicket.State.PAWNED && now >= ticket.expiresAtMillis()) {
                PawnTicket moved = ticket.withState(PawnTicket.State.SHOWCASE);
                tickets.put(ticket.id(), moved);
                expired.add(moved);
            }
        }
        return expired;
    }

    public List<PawnTicket> pawnedBy(UUID owner) {
        return tickets.values().stream()
            .filter(ticket -> ticket.state() == PawnTicket.State.PAWNED && owner.equals(ticket.owner()))
            .toList();
    }

    public List<PawnTicket> showcase() {
        return tickets.values().stream().filter(ticket -> ticket.state() == PawnTicket.State.SHOWCASE).toList();
    }

    public Optional<PawnTicket> find(String id) {
        return Optional.ofNullable(tickets.get(id));
    }

    /** Removes a ticket the owner is redeeming. Fails once it reached the window. */
    public PawnTicket redeem(String id, UUID owner, long now) {
        PawnTicket ticket = tickets.get(id);
        if (ticket == null || ticket.state() != PawnTicket.State.PAWNED || !owner.equals(ticket.owner())) {
            throw new IllegalStateException("Esa boleta ya no está disponible");
        }
        if (now >= ticket.expiresAtMillis()) {
            throw new IllegalStateException("La boleta venció: el ítem pasó a la vitrina");
        }
        tickets.remove(id);
        return ticket;
    }

    public PawnTicket buy(String id) {
        PawnTicket ticket = tickets.get(id);
        if (ticket == null || ticket.state() != PawnTicket.State.SHOWCASE) {
            throw new IllegalStateException("Alguien ya se lo llevó");
        }
        tickets.remove(id);
        return ticket;
    }

    /** Puts a ticket back, for example when paying for it failed. */
    public void restore(PawnTicket ticket) {
        tickets.put(ticket.id(), ticket);
    }

    public Collection<PawnTicket> all() {
        return List.copyOf(tickets.values());
    }

    private long salePrice(long appraisal) {
        return Math.max(1, Math.round(appraisal * saleRatio));
    }

    private String nextId(long now) {
        sequence++;
        return Long.toString(now, 36) + "-" + Long.toString(sequence, 36);
    }
}
