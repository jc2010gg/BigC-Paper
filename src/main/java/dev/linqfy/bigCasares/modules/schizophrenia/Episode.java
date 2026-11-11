package dev.linqfy.bigCasares.modules.schizophrenia;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** One player's ongoing breakdown. Lives only while the player is online; the start time persists. */
final class Episode {

    private final UUID playerId;
    private final long startedAtMillis;
    private final int startIntensity;
    private final EpisodeTimeline timeline = new EpisodeTimeline();
    private final List<PhantomClone> phantoms = new ArrayList<>();
    private final HorrorPacing pacing;
    private final BossBar sanity = BossBar.bossBar(Component.text("Cordura", NamedTextColor.DARK_PURPLE), 1f,
        BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
    private long nextHallucinationTick;
    private long nextHeartbeatTick;
    private boolean inVoid;
    private boolean natural;
    private boolean frenzied;
    private Hallucination previous;

    Episode(UUID playerId, long startedAtMillis, int startIntensity, long tick, RandomGenerator random) {
        this.playerId = playerId;
        this.startedAtMillis = startedAtMillis;
        this.startIntensity = EpisodeMath.clampIntensity(startIntensity);
        this.pacing = new HorrorPacing(tick, this.startIntensity, random);
        this.nextHallucinationTick = tick + 100L;
    }

    HorrorPacing pacing() {
        return pacing;
    }

    BossBar sanity() {
        return sanity;
    }

    UUID playerId() {
        return playerId;
    }

    long startedAtMillis() {
        return startedAtMillis;
    }

    int startIntensity() {
        return startIntensity;
    }

    EpisodeTimeline timeline() {
        return timeline;
    }

    List<PhantomClone> phantoms() {
        return phantoms;
    }

    long nextHallucinationTick() {
        return nextHallucinationTick;
    }

    void nextHallucinationTick(long tick) {
        this.nextHallucinationTick = tick;
    }

    boolean natural() {
        return natural;
    }

    void natural(boolean natural) {
        this.natural = natural;
    }

    boolean frenzied() {
        return frenzied;
    }

    void frenzied(boolean frenzied) {
        this.frenzied = frenzied;
    }

    boolean inVoid() {
        return inVoid;
    }

    void inVoid(boolean inVoid) {
        this.inVoid = inVoid;
    }

    long nextHeartbeatTick() {
        return nextHeartbeatTick;
    }

    void nextHeartbeatTick(long tick) {
        this.nextHeartbeatTick = tick;
    }

    Hallucination previous() {
        return previous;
    }

    void previous(Hallucination hallucination) {
        this.previous = hallucination;
    }

    int intensity(long nowMillis, double minutesPerIntensity) {
        return EpisodeMath.intensity(startIntensity, nowMillis - startedAtMillis, minutesPerIntensity);
    }
}
