package dev.linqfy.bigCasares.modules.casino.pawn;

/** What the appraiser looks at, extracted from an item so the maths stays independent of Bukkit. */
public record AppraisalInput(String material, int amount, int maxDurability, int damage, int enchantmentLevels) {
}
