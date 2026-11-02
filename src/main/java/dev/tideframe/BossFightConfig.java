package dev.tideframe;

import dev.tideframe.core.demo.BossFightSettings;
import dev.tideframe.core.demo.KeyChallenge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads config.yml → boss-fight into {@link BossFightSettings}; also adds sections new in this version to old configs. */
public final class BossFightConfig {
    private BossFightConfig() { }

    public static BossFightSettings load(Path config) {
        var root = YamlConfiguration.loadConfiguration(config.toFile());
        var section = root.getConfigurationSection("boss-fight");
        var d = BossFightSettings.defaults();
        if (section == null) return d;
        var keys = new ArrayList<KeyChallenge.Key>();
        if (section.isList("keys")) for (Object key : section.getList("keys")) {
            try { keys.add(KeyChallenge.Key.valueOf(String.valueOf(key).trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException unknown) { throw new IllegalArgumentException("boss-fight.keys: unknown key " + key + " (use W, A, S, D, SPACE, CLICK)"); }
        } else keys.addAll(d.keys());
        return new BossFightSettings(keys, section.getInt("lives", d.lives()),
                ticks(section, "time-start", d.startTicks()), ticks(section, "time-min", d.minTicks()),
                section.getInt("enrage-at", d.enragePercent()), section.getInt("hit-damage", d.hitDamage()), section.getDouble("miss-damage", d.missDamage()),
                strings(section.getConfigurationSection("colors")), numbers(section.getConfigurationSection("layout")),
                booleans(section.getConfigurationSection("show")), strings(section.getConfigurationSection("texts")),
                strings(section.getConfigurationSection("sounds")));
    }
    private static int ticks(ConfigurationSection section, String key, int fallback) {
        return section.isSet(key) ? (int) Math.round(section.getDouble(key) * 20) : fallback;
    }
    private static Map<String, String> strings(ConfigurationSection section) {
        var out = new LinkedHashMap<String, String>();
        if (section != null) for (String key : section.getKeys(false)) out.put(key, section.isString(key) ? section.getString(key) : String.valueOf(section.get(key)));
        return out;
    }
    private static Map<String, Double> numbers(ConfigurationSection section) {
        var out = new LinkedHashMap<String, Double>();
        if (section != null) for (String key : section.getKeys(false)) {
            if (!section.isDouble(key) && !section.isInt(key)) throw new IllegalArgumentException("boss-fight.layout." + key + " must be a number");
            out.put(key, section.getDouble(key));
        }
        return out;
    }
    private static Map<String, Boolean> booleans(ConfigurationSection section) {
        var out = new LinkedHashMap<String, Boolean>();
        if (section != null) for (String key : section.getKeys(false)) {
            if (!section.isBoolean(key)) throw new IllegalArgumentException("boss-fight.show." + key + " must be true or false");
            out.put(key, section.getBoolean(key));
        }
        return out;
    }

    /**
     * Appends every top-level section of the bundled config.yml that the server's config.yml lacks (with its comments),
     * so owners upgrading from an older version see the new options. Existing values are never touched.
     */
    /**
     * Configs that still carry the untouched pre-0.19 display defaults move to the full-screen sizes.
     * A value the owner changed is never rewritten.
     */
    static boolean upgradeDisplayDefaults(Path config) throws IOException {
        if (!Files.exists(config)) return false;
        String text = Files.readString(config, StandardCharsets.UTF_8), updated = text;
        updated = updated.replace("  sizes: [1, 1.5, 1.75, 2.25, 3.5]\n", "  sizes: [1, 1.75, 2, 2.75]\n");
        if (!updated.equals(text)) updated = updated.replace("  default-size: 1.75\n", "  default-size: 2\n");
        // New in 0.19: the cursor size option, right under default-size.
        if (!updated.contains("cursor-size:")) {
            var matcher = java.util.regex.Pattern.compile("(?m)^  default-size: .*\\n").matcher(updated);
            if (matcher.find()) updated = updated.substring(0, matcher.end())
                    + "  # Mouse cursor size inside menus (0.25 to 1.5 of the original design).\n  cursor-size: 0.4\n" + updated.substring(matcher.end());
        }
        if (updated.equals(text)) return false;
        Files.writeString(config, updated, StandardCharsets.UTF_8);
        return true;
    }

    static boolean addMissingSections(Path config, String bundled) throws IOException {
        if (!Files.exists(config)) return false;
        var existing = YamlConfiguration.loadConfiguration(config.toFile());
        var blocks = new LinkedHashMap<String, String>();
        var comments = new StringBuilder();
        String current = null; var block = new StringBuilder();
        for (String line : bundled.split("\n", -1)) {
            boolean topLevel = !line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#") && line.contains(":");
            if (topLevel) {
                if (current != null) blocks.put(current, block.toString());
                current = line.substring(0, line.indexOf(':')).trim();
                block = new StringBuilder(comments).append(line).append('\n'); comments.setLength(0);
            } else if (line.startsWith("#")) comments.append(line).append('\n');
            else if (current != null) { block.append(comments).append(line).append('\n'); comments.setLength(0); }
        }
        if (current != null) blocks.put(current, block.toString());
        var missing = new StringBuilder();
        for (var entry : blocks.entrySet()) if (!existing.contains(entry.getKey())) missing.append('\n').append(entry.getValue().stripTrailing()).append('\n');
        if (missing.isEmpty()) return false;
        String text = Files.readString(config, StandardCharsets.UTF_8);
        Files.writeString(config, text + (text.endsWith("\n") ? "" : "\n") + missing, StandardCharsets.UTF_8);
        return true;
    }
}
