package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.entity.Mannequin;

/**
 * A fake player only its victim can see. Behaviours:
 * STARE stands still and vanishes when looked at; STALKER creeps closer whenever it is not
 * watched, freezes under your gaze and jumpscares on arrival; MIRROR wears your own face and
 * stares back until you blink; SNAP is pinned for a forced camera turn; BEHIND keeps standing
 * right at your back and screams when you turn around; CHASER runs you down whether you look or
 * not; WATCHER stands far away all episode long and relocates when stared at; CROWD creeps in
 * slowly while unwatched and never vanishes under your gaze; ALTERNATE wears a friend's face,
 * walks right up to you insisting it is them, and then stops being them.
 */
final class PhantomClone {

    enum Behaviour { STARE, STALKER, MIRROR, SNAP, BEHIND, CHASER, WATCHER, CROWD, ALTERNATE }

    private final Mannequin entity;
    private final Behaviour behaviour;
    private final long expiresAtTick;
    private int watchedFor;

    PhantomClone(Mannequin entity, Behaviour behaviour, long expiresAtTick) {
        this.entity = entity;
        this.behaviour = behaviour;
        this.expiresAtTick = expiresAtTick;
    }

    Mannequin entity() {
        return entity;
    }

    Behaviour behaviour() {
        return behaviour;
    }

    boolean expired(long tick) {
        return tick >= expiresAtTick;
    }

    int watchedFor() {
        return watchedFor;
    }

    void watchedFor(int ticks) {
        this.watchedFor = ticks;
    }

    void remove() {
        if (entity.isValid()) {
            entity.remove();
        }
    }
}
