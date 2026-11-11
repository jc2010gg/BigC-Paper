package dev.linqfy.bigCasares.modules.schizophrenia;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-player queue of delayed hallucination steps. Restore cues undo client-side lies (fake blocks,
 * borders, equipment) and always run, even when the episode is cut short.
 */
final class EpisodeTimeline {

    private record Cue(long dueTick, Runnable action, boolean restore) {
    }

    private final List<Cue> cues = new ArrayList<>();

    void at(long dueTick, Runnable action) {
        cues.add(new Cue(dueTick, action, false));
    }

    void restoreAt(long dueTick, Runnable restore) {
        cues.add(new Cue(dueTick, restore, true));
    }

    void runDue(long now, Consumer<RuntimeException> failures) {
        List<Cue> due = new ArrayList<>();
        cues.removeIf(cue -> {
            if (cue.dueTick() <= now) {
                due.add(cue);
                return true;
            }
            return false;
        });
        for (Cue cue : due) {
            run(cue, failures);
        }
    }

    /** Drops pending scares and immediately runs every pending restore. */
    void flush(Consumer<RuntimeException> failures) {
        List<Cue> restores = cues.stream().filter(Cue::restore).toList();
        cues.clear();
        for (Cue cue : restores) {
            run(cue, failures);
        }
    }

    int size() {
        return cues.size();
    }

    private static void run(Cue cue, Consumer<RuntimeException> failures) {
        try {
            cue.action().run();
        } catch (RuntimeException failure) {
            failures.accept(failure);
        }
    }
}
