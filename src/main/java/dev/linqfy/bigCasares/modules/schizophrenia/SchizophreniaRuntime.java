package dev.linqfy.bigCasares.modules.schizophrenia;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/** Owns every active episode, drives the hallucination clock and handles onset and cure. */
final class SchizophreniaRuntime {

    static final long TICK_PERIOD = 2L;

    private final Plugin plugin;
    private final SchizophreniaSettings settings;
    private final HallucinationPicker picker;
    private final HallucinationDirector director;
    private final CleaverFrenzy frenzy;
    private final RandomGenerator random = new SplittableRandom();
    private final LongSupplier clock;
    private final Map<UUID, Episode> episodes = new HashMap<>();
    private final NamespacedKey startedKey;
    private final NamespacedKey intensityKey;
    private final NamespacedKey immuneKey;
    private final NamespacedKey lastNaturalKey;
    private final Map<UUID, Integer> stress = new HashMap<>();
    private long lastServerOnset = Long.MIN_VALUE / 2;
    private long tick;
    private long nextNaturalCheck;

    private final VoidRealm voidRealm;

    SchizophreniaRuntime(Plugin plugin, SchizophreniaSettings settings, LongSupplier clock, VoidRealm voidRealm) {
        this.plugin = plugin;
        this.voidRealm = voidRealm;
        this.settings = settings;
        this.clock = clock;
        this.picker = new HallucinationPicker(settings.disabledHallucinations());
        this.director = new HallucinationDirector(plugin, settings, random, voidRealm);
        this.frenzy = new CleaverFrenzy(plugin, new CleaverItem(plugin), settings.cleaver(), random);
        this.startedKey = new NamespacedKey(plugin, "schizophrenia_started");
        this.intensityKey = new NamespacedKey(plugin, "schizophrenia_intensity");
        this.immuneKey = new NamespacedKey(plugin, "schizophrenia_immune_until");
        this.lastNaturalKey = new NamespacedKey(plugin, "schizophrenia_last_natural");
        this.nextNaturalCheck = settings.natural().checkIntervalSeconds() * 20L;
    }

    // ---------------------------------------------------------------- lifecycle

    void tick() {
        tick += TICK_PERIOD;
        long now = clock.getAsLong();
        for (Episode episode : new ArrayList<>(episodes.values())) {
            Player player = plugin.getServer().getPlayer(episode.playerId());
            if (player == null || !player.isOnline()) {
                suspend(episode.playerId());
                continue;
            }
            try {
                tickEpisode(player, episode, now);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "[Esquizofrenia] Falló una alucinación de " + player.getName(), failure);
            }
        }
        if (settings.natural().enabled() && tick >= nextNaturalCheck) {
            nextNaturalCheck = tick + settings.natural().checkIntervalSeconds() * 20L;
            naturalOnset(now);
        }
    }

    private void tickEpisode(Player player, Episode episode, long now) {
        if (EpisodeMath.expired(now - episode.startedAtMillis(), settings.maxEpisodeMinutes())) {
            end(player, false);
            player.sendMessage(Component.text("Las voces se cansaron... por ahora.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            return;
        }
        episode.timeline().runDue(tick, this::logCueFailure);
        director.updatePhantoms(player, episode, tick);
        if (episode.inVoid()) {
            // Nothing else exists in the black place.
            director.guardVoid(player, episode);
            return;
        }
        if (player.isDead()) {
            return;
        }
        int intensity = episode.intensity(now, settings.minutesPerIntensity());
        if (frenzy.enabled() && intensity >= EpisodeMath.MAX_INTENSITY) {
            // The last stage is no longer about seeing things. It is about what you do to them.
            frenzy.update(player, episode, tick);
            director.atmosphere(player, episode, tick, intensity);
            return;
        }
        HorrorPacing pacing = episode.pacing();
        if (pacing.advance(tick, intensity, random)) {
            director.onPhase(player, episode, tick, pacing.phase());
            if (pacing.phase() == HorrorPacing.Phase.SCARE) {
                scare(player, episode, intensity);
            }
        }
        director.atmosphere(player, episode, tick, intensity);
        if (tick < episode.nextHallucinationTick()) {
            return;
        }
        double tension = pacing.tension(tick);
        int stacked = EpisodeMath.stackedBeats(tension, intensity);
        for (int i = 0; i < stacked; i++) {
            Hallucination.Beat beat = switch (pacing.phase()) {
                case CALM -> intensity >= 4 && random.nextDouble() < 0.25 ? Hallucination.Beat.DREAD : Hallucination.Beat.AMBIENT;
                case DREAD -> random.nextDouble() < 0.6 ? Hallucination.Beat.DREAD : Hallucination.Beat.AMBIENT;
                case SCARE -> null;
                case AFTERMATH -> intensity >= 5 ? Hallucination.Beat.AMBIENT : null;
            };
            if (beat == null) {
                break;
            }
            long at = tick + i * 15L;
            picker.pick(intensity, beat, episode.previous(), random).ifPresent(hallucination -> {
                episode.timeline().at(at, () -> {
                    if (!director.play(hallucination, player, episode, at, intensity)) {
                        director.play(Hallucination.WHISPERS, player, episode, at, intensity);
                    }
                });
                episode.previous(hallucination);
            });
        }
        double seconds = EpisodeMath.beatSeconds(pacing.tension(tick),
            settings.slowestIntervalSeconds(), settings.fastestIntervalSeconds(), random.nextDouble());
        episode.nextHallucinationTick(tick + Math.round(seconds * 20.0));
    }

    /**
     * The payoff of a dread phase: one set piece, falling back to the simplest scare if it has no
     * room.
     */
    private void scare(Player player, Episode episode, int intensity) {
        int combo = EpisodeMath.scaresInCombo(intensity);
        for (int i = 0; i < combo; i++) {
            Hallucination chosen = picker.pick(intensity, Hallucination.Beat.SCARE, episode.previous(), random)
                .orElse(Hallucination.CREEPER_HISS);
            episode.previous(chosen);
            long at = tick + i * 50L;
            episode.timeline().at(at, () -> {
                if (!director.play(chosen, player, episode, at, intensity)
                    && !director.play(Hallucination.RIGHT_BEHIND_YOU, player, episode, at, intensity)) {
                    director.play(Hallucination.CREEPER_HISS, player, episode, at, intensity);
                }
            });
        }
    }

    void shutdown() {
        for (UUID playerId : new ArrayList<>(episodes.keySet())) {
            suspend(playerId);
        }
        removeStrayPhantoms();
    }

    /** Leftover mannequins can only exist after a crash mid-episode; they are not persistent, but be sure. */
    void removeStrayPhantoms() {
        plugin.getServer().getWorlds().forEach(world -> world.getEntities().stream()
            .filter(entity -> entity.getScoreboardTags().contains(HallucinationDirector.PHANTOM_TAG))
            .forEach(Entity::remove));
    }

    // ---------------------------------------------------------------- episodes

    boolean start(Player player, int intensity) {
        if (episodes.containsKey(player.getUniqueId())) {
            return false;
        }
        long now = clock.getAsLong();
        int clamped = EpisodeMath.clampIntensity(intensity);
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(startedKey, PersistentDataType.LONG, now);
        data.set(intensityKey, PersistentDataType.INTEGER, clamped);
        data.remove(immuneKey);
        Episode episode = new Episode(player.getUniqueId(), now, clamped, tick, random);
        episodes.put(player.getUniqueId(), episode);
        director.showSanity(player, episode);
        return true;
    }

    /** Re-attaches a persisted episode after a relog: logging out is not a cure. */
    void resume(Player player) {
        // A cleaver is never kept across a logout or a crash; the frenzy hands a new one back.
        frenzy.item().takeAway(player);
        if (voidRealm != null && voidRealm.isAway(player)) {
            voidRealm.bringBack(player);
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        Long started = data.get(startedKey, PersistentDataType.LONG);
        if (started == null || episodes.containsKey(player.getUniqueId())) {
            return;
        }
        if (EpisodeMath.expired(clock.getAsLong() - started, settings.maxEpisodeMinutes())) {
            data.remove(startedKey);
            data.remove(intensityKey);
            return;
        }
        int intensity = data.getOrDefault(intensityKey, PersistentDataType.INTEGER, 1);
        Episode episode = new Episode(player.getUniqueId(), started, intensity, tick, random);
        episodes.put(player.getUniqueId(), episode);
        Long lastNatural = data.get(lastNaturalKey, PersistentDataType.LONG);
        episode.natural(lastNatural != null && lastNatural >= started);
        director.showSanity(player, episode);
    }

    void suspend(UUID playerId) {
        Episode episode = episodes.remove(playerId);
        if (episode != null) {
            episode.timeline().flush(this::logCueFailure);
            director.removeAllPhantoms(episode);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                frenzy.stop(player, episode);
                director.clearAtmosphere(player, episode);
            }
        }
    }

    void end(Player player, boolean grantImmunity) {
        suspend(player.getUniqueId());
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(startedKey);
        data.remove(intensityKey);
        if (grantImmunity && settings.immunityMinutesAfterCure() > 0) {
            data.set(immuneKey, PersistentDataType.LONG, clock.getAsLong() + settings.immunityMinutesAfterCure() * 60_000L);
        }
        player.resetPlayerTime();
        player.resetPlayerWeather();
        player.setWorldBorder(null);
        player.sendHealthUpdate();
    }

    boolean isAffected(Player player) {
        return episodes.containsKey(player.getUniqueId());
    }

    boolean isFrenzied(Player player) {
        Episode episode = episodes.get(player.getUniqueId());
        return episode != null && episode.frenzied();
    }

    CleaverItem cleaver() {
        return frenzy.item();
    }

    Optional<Integer> intensity(Player player) {
        Episode episode = episodes.get(player.getUniqueId());
        return episode == null ? Optional.empty()
            : Optional.of(episode.intensity(clock.getAsLong(), settings.minutesPerIntensity()));
    }

    boolean trigger(Player player, Hallucination hallucination) {
        Episode episode = episodes.get(player.getUniqueId());
        if (episode == null) {
            return false;
        }
        return director.play(hallucination, player, episode, tick, EpisodeMath.MAX_INTENSITY);
    }

    /** Skips the quiet part: dread starts building now. */
    void provoke(Player player) {
        Episode episode = episodes.get(player.getUniqueId());
        if (episode != null) {
            int intensity = episode.intensity(clock.getAsLong(), settings.minutesPerIntensity());
            episode.pacing().forceDread(tick, intensity, random);
            director.onPhase(player, episode, tick, HorrorPacing.Phase.DREAD);
        }
    }

    boolean ownsPhantom(Entity entity) {
        return entity.getScoreboardTags().contains(HallucinationDirector.PHANTOM_TAG);
    }

    /** The victim swung at a ghost: it was never there. */
    void dispelPhantom(Player player, Entity entity) {
        Episode episode = episodes.get(player.getUniqueId());
        if (episode == null) {
            entity.remove();
            return;
        }
        for (PhantomClone clone : new ArrayList<>(episode.phantoms())) {
            if (clone.entity().equals(entity)) {
                player.playSound(entity.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1f, 0.5f);
                director.vanish(player, clone, true);
                episode.phantoms().remove(clone);
                return;
            }
        }
    }

    List<String> statusLines() {
        long now = clock.getAsLong();
        List<String> lines = new ArrayList<>();
        lines.add("§5Esquizofrenia §7- episodios activos: §f" + episodes.size());
        for (Episode episode : episodes.values()) {
            Player player = plugin.getServer().getPlayer(episode.playerId());
            String name = player == null ? episode.playerId().toString() : player.getName();
            long minutes = (now - episode.startedAtMillis()) / 60_000L;
            lines.add("§7- §f" + name + " §7intensidad §d" + episode.intensity(now, settings.minutesPerIntensity())
                + "§7, hace §f" + minutes + " min§7, fase §f" + (episode.frenzied() ? "cuchilla"
                : episode.pacing().phase().name().toLowerCase(java.util.Locale.ROOT))
                + "§7, fantasmas §f" + episode.phantoms().size());
        }
        if (settings.natural().enabled() && !stress.isEmpty()) {
            StringBuilder line = new StringBuilder("§7Estrés acumulado (umbral " + settings.natural().stressThreshold() + "):");
            stress.forEach((id, level) -> {
                Player player = plugin.getServer().getPlayer(id);
                line.append(" §f").append(player == null ? id.toString().substring(0, 8) : player.getName())
                    .append("§7=").append(level);
            });
            lines.add(line.toString());
        }
        return lines;
    }

    // ---------------------------------------------------------------- Lobotomizador

    /**
     * Anyone speared by another player is lobotomized: the voices stop if there were any, the
     * patient is left stunned and cannot relapse for a while.
     */
    void lobotomize(Player surgeon, Player patient, LobotomizerItem item) {
        boolean wasSick = isAffected(patient);
        end(patient, true);
        patient.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        patient.playSound(patient.getLocation(), Sound.ITEM_SPEAR_HIT, SoundCategory.PLAYERS, 1f, 0.6f);
        // Tinnitus after the blow.
        patient.playSound(patient.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1f, 2f);
        int stun = settings.lobotomizer().stunSeconds() * 20;
        if (stun > 0) {
            patient.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stun, 1, false, false, true));
            patient.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, stun, 0, false, false, true));
            patient.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, Math.min(stun, 140), 0, false, false, true));
        }
        patient.playSound(patient.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1f, 0.5f);
        patient.playSound(patient.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        surgeon.playSound(surgeon.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.7f, 0.5f);
        patient.showTitle(Title.title(
            Component.text("*bonk*", NamedTextColor.GRAY, TextDecoration.ITALIC),
            Component.text(wasSick ? "las voces se callaron" : "...", NamedTextColor.DARK_GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(1000))));
        patient.sendMessage(Component.text(surgeon.getName() + " te hizo una lobotomía. "
            + (wasSick ? "Silencio, al fin." : "No sentís... nada."), NamedTextColor.GRAY));
        surgeon.sendMessage(Component.text("Le hiciste una lobotomía a " + patient.getName() + ". "
            + (wasSick ? "Las voces se fueron." : "No tenía voces, pero ahora tampoco tiene ideas."),
            wasSick ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        int cost = settings.lobotomizer().durabilityCost();
        if (cost > 0 && item.isLobotomizer(surgeon.getInventory().getItemInMainHand())) {
            surgeon.damageItemStack(EquipmentSlot.HAND, cost);
        }
    }

    // ---------------------------------------------------------------- natural onset

    private void naturalOnset(long now) {
        SchizophreniaSettings.Natural natural = settings.natural();
        int activeNatural = (int) episodes.values().stream().filter(Episode::natural).count();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (isAffected(player) || immune(player, now) || !natural.allowsWorld(player.getWorld().getName())
                || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)) {
                stress.remove(id);
                continue;
            }
            long sinceRest = player.getStatistic(Statistic.TIME_SINCE_REST);
            long time = player.getWorld().getTime();
            OnsetPolicy.Conditions conditions = new OnsetPolicy.Conditions(
                alone(player, natural.aloneRadius()),
                player.getEyeLocation().getBlock().getLightLevel(),
                player.getY(),
                player.getWorld().getEnvironment() == World.Environment.NORMAL && time >= 13_000 && time <= 23_000,
                sinceRest,
                player.getHealth(),
                player.isSleeping());
            int level = OnsetPolicy.applyStress(stress.getOrDefault(id, 0),
                OnsetPolicy.stressDelta(conditions, natural), natural);
            if (level <= 0) {
                stress.remove(id);
            } else {
                stress.put(id, level);
            }
            Long lastOnset = player.getPersistentDataContainer().get(lastNaturalKey, PersistentDataType.LONG);
            if (OnsetPolicy.mayStart(level, sinceRest, now, lastOnset, lastServerOnset, activeNatural, natural)
                && random.nextDouble() < natural.chance() && start(player, 1)) {
                episodes.get(id).natural(true);
                player.getPersistentDataContainer().set(lastNaturalKey, PersistentDataType.LONG, now);
                lastServerOnset = now;
                stress.remove(id);
                activeNatural++;
            }
        }
    }

    int stress(Player player) {
        return stress.getOrDefault(player.getUniqueId(), 0);
    }

    private boolean immune(Player player, long now) {
        Long until = player.getPersistentDataContainer().get(immuneKey, PersistentDataType.LONG);
        return until != null && until > now;
    }

    private static boolean alone(Player player, int radius) {
        double squared = (double) radius * radius;
        for (Player other : player.getWorld().getPlayers()) {
            if (other != player && other.getGameMode() != GameMode.SPECTATOR
                && other.getLocation().distanceSquared(player.getLocation()) <= squared) {
                return false;
            }
        }
        return true;
    }

    private void logCueFailure(RuntimeException failure) {
        plugin.getLogger().log(Level.FINE, "[Esquizofrenia] cue failed", failure);
    }
}
