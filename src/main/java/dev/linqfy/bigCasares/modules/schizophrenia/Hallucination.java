package dev.linqfy.bigCasares.modules.schizophrenia;

import java.util.Locale;
import java.util.Optional;

/**
 * Everything the brain can invent, grouped by the pacing beat it belongs to. {@code minIntensity}
 * gates the nastier ones behind a longer untreated episode; {@code weight} is the relative chance.
 */
public enum Hallucination {
    // Ambient: background unease while things are "calm".
    DISTANT_ROAR(Beat.AMBIENT, 1, 6),
    WHISPERS(Beat.AMBIENT, 1, 8),
    SILHOUETTE(Beat.AMBIENT, 2, 6),
    MOB_SOUNDS(Beat.AMBIENT, 1, 5),
    FAKE_CHAT(Beat.AMBIENT, 2, 5),
    MUSIC_BOX(Beat.AMBIENT, 2, 4),
    WALL_WRITING(Beat.AMBIENT, 3, 5),
    YOUR_NAME(Beat.AMBIENT, 1, 5),
    CHAT_FLOOD(Beat.AMBIENT, 4, 2),
    INTRUDER_ALERT(Beat.AMBIENT, 3, 3),
    // Dread: something is coming.
    FOOTSTEPS(Beat.DREAD, 1, 10),
    PHANTOM_CLONE(Beat.DREAD, 1, 10),
    MINING_BEHIND(Beat.DREAD, 1, 5),
    DOOR_KNOCKING(Beat.DREAD, 1, 5),
    HEARTBEAT(Beat.DREAD, 2, 4),
    DOPPELGANGER(Beat.DREAD, 3, 4),
    LIGHTS_OUT(Beat.DREAD, 2, 5),
    STALKER(Beat.DREAD, 2, 6),
    BLOCK_ROT(Beat.DREAD, 3, 5),
    MASKED_PLAYERS(Beat.DREAD, 3, 5),
    RED_VIGNETTE(Beat.DREAD, 4, 4),
    ETERNAL_NIGHT(Beat.DREAD, 3, 3),
    SCREEN_SHAKE(Beat.DREAD, 2, 5),
    BLOOD_RAIN(Beat.DREAD, 4, 3),
    CROWD(Beat.DREAD, 3, 4),
    ALTERNATE(Beat.DREAD, 3, 6),
    // Scare: the payoff.
    CREEPER_HISS(Beat.SCARE, 1, 8),
    RIGHT_BEHIND_YOU(Beat.SCARE, 1, 10),
    FAKE_HURT(Beat.SCARE, 2, 5),
    THE_VISITOR(Beat.SCARE, 2, 6),
    GLITCH_TITLE(Beat.SCARE, 2, 5),
    CHASE(Beat.SCARE, 3, 7),
    FAKE_EXPLOSION(Beat.SCARE, 3, 4),
    CAMERA_SNAP(Beat.SCARE, 3, 4),
    FAKE_THUNDER(Beat.SCARE, 3, 3),
    ELDER_GUARDIAN(Beat.SCARE, 3, 2),
    HUNTED(Beat.SCARE, 3, 5),
    SWARM(Beat.SCARE, 4, 4),
    CAMERA_SPIN(Beat.SCARE, 4, 4),
    FLESH_ROOM(Beat.SCARE, 4, 4),
    FAKE_DEATH(Beat.SCARE, 4, 3),
    BREAKDOWN(Beat.SCARE, 5, 5),
    FACE_FLASH(Beat.SCARE, 3, 4),
    VOID_VISIT(Beat.SCARE, 3, 6);

    /** Where a hallucination sits in the calm, dread, scare loop. */
    public enum Beat { AMBIENT, DREAD, SCARE }

    private final Beat beat;
    private final int minIntensity;
    private final int weight;

    Hallucination(Beat beat, int minIntensity, int weight) {
        this.beat = beat;
        this.minIntensity = minIntensity;
        this.weight = weight;
    }

    public Beat beat() {
        return beat;
    }

    public int minIntensity() {
        return minIntensity;
    }

    public int weight() {
        return weight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<Hallucination> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Hallucination value : values()) {
            if (value.name().equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
