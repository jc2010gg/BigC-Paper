package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.bar.DrinkType;
import dev.linqfy.bigCasares.modules.casino.loans.LoanOffer;
import dev.linqfy.bigCasares.modules.casino.pawn.Appraiser;
import dev.linqfy.bigCasares.modules.casino.slots.SlotOutcome;
import dev.linqfy.bigCasares.modules.casino.slots.SlotPaytable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Settings of the casino, read from the {@code casino} section of config.yml. Missing keys use the defaults. */
public record CasinoSettings(
    List<Long> chips,
    double ambientRadius,
    Blackjack blackjack,
    Poker poker,
    Roulette roulette,
    Slots slots,
    Pit pit,
    Loans loans,
    Pawn pawn,
    Map<DrinkType, Long> drinkPrices
) {

    public record Blackjack(long minBet, long maxBet, int decks, int bettingSeconds, int turnSeconds) {
    }

    public record Poker(long buyIn, long smallBlind, long bigBlind, int turnSeconds) {
    }

    public record Roulette(int cycleSeconds, int spinSeconds, long maxBet) {
    }

    public record Slots(List<Long> bets, double nearMissChance, long jackpotSeed, double jackpotContribution,
                        SlotPaytable paytable) {
    }

    public record Pit(long entryFee, double houseCut, int bettingSeconds, int fightSeconds) {
    }

    public record Loans(List<LoanOffer> offers, double lateFeePerHour, double maxMultiplier, String collectorSkin,
                        boolean seizeChests, int chestRadius, double punchSurcharge, int seizureCooldownSeconds) {
    }

    public record Pawn(double loanRatio, double redeemRatio, double saleRatio, long holdHours,
                       Map<String, Long> prices) {
    }

    public static CasinoSettings defaults() {
        return load(new MemoryConfiguration());
    }

    public static CasinoSettings load(ConfigurationSection root) {
        ConfigurationSection casino = section(root, "casino");
        List<Long> chips = longs(casino, "chips", List.of(10L, 50L, 100L, 500L, 1000L, 5000L));

        ConfigurationSection bj = section(casino, "blackjack");
        Blackjack blackjack = new Blackjack(
            positive(bj.getLong("min-bet", 10), "blackjack.min-bet"),
            positive(bj.getLong("max-bet", 5000), "blackjack.max-bet"),
            Math.max(1, bj.getInt("decks", 6)),
            Math.max(5, bj.getInt("betting-seconds", 15)),
            Math.max(5, bj.getInt("turn-seconds", 30)));

        ConfigurationSection pk = section(casino, "poker");
        Poker poker = new Poker(
            positive(pk.getLong("buy-in", 1000), "poker.buy-in"),
            positive(pk.getLong("small-blind", 10), "poker.small-blind"),
            positive(pk.getLong("big-blind", 20), "poker.big-blind"),
            Math.max(5, pk.getInt("turn-seconds", 35)));
        if (poker.bigBlind() < poker.smallBlind()) {
            throw new IllegalArgumentException("casino.poker.big-blind must be at least the small blind");
        }

        ConfigurationSection rl = section(casino, "roulette");
        int cycle = Math.max(30, rl.getInt("cycle-seconds", 60));
        Roulette roulette = new Roulette(cycle, Math.max(6, Math.min(cycle - 15, rl.getInt("spin-seconds", 11))),
            positive(rl.getLong("max-bet", 10000), "roulette.max-bet"));

        ConfigurationSection sl = section(casino, "slots");
        ConfigurationSection chances = section(sl, "chances");
        ConfigurationSection multipliers = section(sl, "multipliers");
        SlotPaytable defaults = SlotPaytable.defaults();
        Map<SlotOutcome, Double> chanceMap = new EnumMap<>(SlotOutcome.class);
        Map<SlotOutcome, Integer> multiplierMap = new EnumMap<>(SlotOutcome.class);
        for (SlotOutcome outcome : SlotOutcome.values()) {
            if (outcome == SlotOutcome.LOSE) {
                continue;
            }
            String key = outcome.name().toLowerCase(Locale.ROOT);
            chanceMap.put(outcome, chances.getDouble(key, defaults.chance(outcome)));
            multiplierMap.put(outcome, multipliers.getInt(key, defaults.multiplier(outcome)));
        }
        Slots slots = new Slots(
            longs(sl, "bets", List.of(10L, 50L, 100L, 500L)),
            clamp(sl.getDouble("near-miss-chance", 0.35), 0, 1),
            Math.max(0, sl.getLong("jackpot-seed", 5000)),
            clamp(sl.getDouble("jackpot-contribution", 0.03), 0, 0.5),
            new SlotPaytable(chanceMap, multiplierMap));

        ConfigurationSection pt = section(casino, "fight-pit");
        Pit pit = new Pit(
            positive(pt.getLong("entry-fee", 250), "fight-pit.entry-fee"),
            clamp(pt.getDouble("house-cut", 0.10), 0, 0.9),
            Math.max(10, pt.getInt("betting-seconds", 30)),
            Math.max(20, pt.getInt("fight-seconds", 120)));

        ConfigurationSection ln = section(casino, "loans");
        List<LoanOffer> offers = new ArrayList<>();
        ConfigurationSection offerSection = ln.getConfigurationSection("offers");
        if (offerSection == null) {
            offers.add(new LoanOffer("chico", 1000, 0.35, 120));
            offers.add(new LoanOffer("mediano", 5000, 0.50, 240));
            offers.add(new LoanOffer("grande", 20000, 0.80, 360));
        } else {
            for (String id : offerSection.getKeys(false)) {
                ConfigurationSection offer = section(offerSection, id);
                offers.add(new LoanOffer(id, offer.getLong("amount"), offer.getDouble("interest"),
                    offer.getLong("term-minutes")));
            }
        }
        if (offers.isEmpty()) {
            throw new IllegalArgumentException("casino.loans.offers needs at least one offer");
        }
        Loans loans = new Loans(List.copyOf(offers),
            Math.max(0, ln.getDouble("late-fee-per-hour", 0.10)),
            Math.max(1, ln.getDouble("max-multiplier", 3.0)),
            ln.getString("collector-skin", ""),
            ln.getBoolean("seize-chests", true),
            Math.max(0, ln.getInt("chest-radius", 32)),
            Math.max(0, ln.getDouble("punch-surcharge", 0.05)),
            Math.max(10, ln.getInt("seizure-cooldown-seconds", 60)));

        ConfigurationSection pw = section(casino, "pawn-shop");
        Map<String, Long> prices = new LinkedHashMap<>(Appraiser.defaultPrices());
        ConfigurationSection priceSection = pw.getConfigurationSection("prices");
        if (priceSection != null) {
            for (String material : priceSection.getKeys(false)) {
                prices.put(material.toUpperCase(Locale.ROOT), priceSection.getLong(material));
            }
        }
        Pawn pawn = new Pawn(
            clamp(pw.getDouble("loan-ratio", 0.6), 0.05, 0.95),
            Math.max(1, pw.getDouble("redeem-ratio", 1.25)),
            clamp(pw.getDouble("sale-ratio", 0.85), 0.05, 10),
            Math.max(1, pw.getLong("hold-hours", 48)),
            Map.copyOf(prices));

        ConfigurationSection br = section(casino, "bar");
        Map<DrinkType, Long> drinkPrices = new EnumMap<>(DrinkType.class);
        drinkPrices.put(DrinkType.WHISKY_NETHERRACK, br.getLong("whisky_netherrack", 150));
        drinkPrices.put(DrinkType.CERVEZA_CALIENTE, br.getLong("cerveza_caliente", 60));
        drinkPrices.put(DrinkType.COCTEL_RUINA, br.getLong("coctel_ruina", 300));
        drinkPrices.put(DrinkType.VASO_POLVO_BLANCO, br.getLong("vaso_polvo_blanco", 800));

        return new CasinoSettings(chips, Math.max(16, casino.getDouble("ambient-radius", 48)), blackjack, poker,
            roulette, slots, pit, loans, pawn, Map.copyOf(drinkPrices));
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        return section == null ? new MemoryConfiguration() : section;
    }

    private static List<Long> longs(ConfigurationSection section, String path, List<Long> fallback) {
        if (!section.isList(path)) {
            return fallback;
        }
        List<Long> values = section.getLongList(path).stream().filter(value -> value > 0).sorted().distinct().toList();
        return values.isEmpty() ? fallback : values;
    }

    private static long positive(long value, String key) {
        if (value <= 0) {
            throw new IllegalArgumentException("casino." + key + " must be positive");
        }
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
