package dev.linqfy.bigCasares.modules.casino;

import org.bukkit.inventory.ItemStack;

/** Hooks between attractions: the debt collector sells what it seizes through the pawn shop window. */
public interface CasinoServices {

    /** Appraised value of an item, or 0 when the pawn shop would not take it. */
    long appraise(ItemStack item);

    /** Puts a seized item in the liquidation window. */
    void consign(ItemStack item, long appraisal);
}
