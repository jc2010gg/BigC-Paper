package dev.linqfy.bigCasares.modules.casino.pawn;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * Values items from a base price list. Worn gear loses up to 70% of its value, every enchantment level
 * adds 15%. Items that are not on the list are not accepted.
 */
public final class Appraiser {

    private final Map<String, Long> basePrices = new TreeMap<>();

    public Appraiser(Map<String, Long> basePrices) {
        basePrices.forEach((material, price) -> {
            if (price > 0) {
                this.basePrices.put(material.toUpperCase(Locale.ROOT), price);
            }
        });
    }

    public static Map<String, Long> defaultPrices() {
        Map<String, Long> prices = new TreeMap<>();
        prices.put("ELYTRA", 3000L);
        prices.put("NETHER_STAR", 4000L);
        prices.put("WITHER_SKELETON_SKULL", 1200L);
        prices.put("DRAGON_HEAD", 2500L);
        prices.put("TOTEM_OF_UNDYING", 900L);
        prices.put("HEART_OF_THE_SEA", 700L);
        prices.put("ENCHANTED_GOLDEN_APPLE", 1500L);
        prices.put("NETHERITE_INGOT", 1600L);
        prices.put("NETHERITE_SCRAP", 350L);
        prices.put("ANCIENT_DEBRIS", 350L);
        prices.put("NETHERITE_BLOCK", 14400L);
        prices.put("DIAMOND", 120L);
        prices.put("DIAMOND_BLOCK", 1080L);
        prices.put("EMERALD", 40L);
        prices.put("EMERALD_BLOCK", 360L);
        prices.put("GOLD_INGOT", 30L);
        prices.put("GOLD_BLOCK", 270L);
        prices.put("IRON_INGOT", 10L);
        prices.put("IRON_BLOCK", 90L);
        prices.put("TRIDENT", 1400L);
        prices.put("MACE", 2200L);
        prices.put("BEACON", 4500L);
        prices.put("SHULKER_SHELL", 250L);
        for (String piece : new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "SWORD", "PICKAXE", "AXE",
            "SHOVEL", "HOE"}) {
            prices.put("DIAMOND_" + piece, 450L);
            prices.put("NETHERITE_" + piece, 2200L);
            prices.put("IRON_" + piece, 60L);
            prices.put("GOLDEN_" + piece, 80L);
        }
        prices.put("BOW", 40L);
        prices.put("CROSSBOW", 60L);
        prices.put("SHIELD", 30L);
        return prices;
    }

    public Map<String, Long> prices() {
        return Map.copyOf(basePrices);
    }

    public OptionalLong appraise(AppraisalInput input) {
        Long base = basePrices.get(input.material().toUpperCase(Locale.ROOT));
        if (base == null || input.amount() <= 0) {
            return OptionalLong.empty();
        }
        double condition = 1.0;
        if (input.maxDurability() > 0) {
            double remaining = Math.max(0.0, 1.0 - input.damage() / (double) input.maxDurability());
            condition = 0.3 + 0.7 * remaining;
        }
        double enchantments = 1.0 + 0.15 * Math.max(0, input.enchantmentLevels());
        return OptionalLong.of(Math.max(1, Math.round(base * input.amount() * condition * enchantments)));
    }
}
